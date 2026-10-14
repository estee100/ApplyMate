package com.applymate;

import com.applymate.ai.LlmClient;
import com.applymate.ai.Schemas.MatchAnalysis;
import com.applymate.ai.Schemas.SkillExtraction;
import com.applymate.jobs.JobSources;
import com.applymate.jobs.NormalizedJob;
import com.applymate.model.JobSource;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class JobsTest extends IntegrationTest {

    private Map<String, Object> pasted(String title) {
        return Map.of("title", title, "company", "Acme", "location", "Edmonton",
                "description", "We need a developer who knows Java, Spring Boot and PostgreSQL. " + UUID.randomUUID());
    }

    private long paste(String token, String title) throws Exception {
        return read(send(post("/jobs/paste-text"), token, pasted(title)).andExpect(status().isCreated()))
                .get("job").get("id").asLong();
    }

    private void uploadResume(String token) throws Exception {
        when(llm.generate(eq("skill-extraction"), anyString(), anyString(), eq(SkillExtraction.class)))
                .thenReturn(new LlmClient.Result<>(new SkillExtraction(List.of("Java"), List.of("Developer"),
                        List.of(), List.of(), "junior", 1, "x"), "m"));
        mvc.perform(multipart("/resumes").file(new MockMultipartFile("file", "cv.txt", "text/plain",
                        ResumeAndAiTest.RESUME.getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + token)).andExpect(status().isCreated());
    }

    @Test
    void pastedJobIsPrivateToItsOwner() throws Exception {
        String owner = newUser();
        String stranger = newUser();
        long id = paste(owner, "Backend Developer");

        call(get("/jobs/" + id), owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.analyzable").value(true))
                .andExpect(jsonPath("$.job.source").value("PASTED"));
        call(get("/jobs/" + id), stranger).andExpect(status().isNotFound());
        call(put("/jobs/" + id + "/saved"), stranger).andExpect(status().isNotFound());
        call(get("/jobs/mine"), owner).andExpect(jsonPath("$[0].id").value(id));
    }

    @Test
    void pastingTheSameTextTwiceReusesTheJob() throws Exception {
        String token = newUser();
        var body = pasted("Same Job");
        long first = read(send(post("/jobs/paste-text"), token, body)).get("job").get("id").asLong();
        long second = read(send(post("/jobs/paste-text"), token, body)).get("job").get("id").asLong();
        assertThat(second).isEqualTo(first);
        call(get("/jobs/mine"), token).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void saveAndUnsaveAreIdempotent() throws Exception {
        String token = newUser();
        long id = paste(token, "Saved Job");
        call(put("/jobs/" + id + "/saved"), token).andExpect(status().isNoContent());
        call(put("/jobs/" + id + "/saved"), token).andExpect(status().isNoContent());
        call(get("/jobs/saved"), token).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].saved").value(true));
        call(delete("/jobs/" + id + "/saved"), token).andExpect(status().isNoContent());
        call(get("/jobs/saved"), token).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void matchNeedsAResumeThenIsCachedAndClamped() throws Exception {
        String token = newUser();
        long id = paste(token, "Matched Job");
        send(post("/jobs/" + id + "/match"), token, Map.of()).andExpect(status().isUnprocessableEntity());

        uploadResume(token);
        // A poisoned advert might get the model to say 250; the server clamps it.
        when(llm.generate(eq("match-analysis"), anyString(), anyString(), eq(MatchAnalysis.class)))
                .thenReturn(new LlmClient.Result<>(new MatchAnalysis(250, List.of("Java", "Java"), List.of("Kubernetes"), "Good fit."), "m"));

        call(post("/jobs/" + id + "/match"), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.matchPercentage").value(100))
                .andExpect(jsonPath("$.requirementsMet.length()").value(1));
        call(post("/jobs/" + id + "/match"), token).andExpect(status().isOk());
        verify(llm, times(1)).generate(eq("match-analysis"), anyString(), anyString(), eq(MatchAnalysis.class));

        // The card shows the cached figures without another call.
        call(get("/jobs/mine"), token)
                .andExpect(jsonPath("$[0].match.percentage").value(100))
                .andExpect(jsonPath("$[0].match.missingCount").value(1));

        // The advert went to the model fenced as untrusted data, never in the system prompt.
        var system = org.mockito.ArgumentCaptor.forClass(String.class);
        var prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(llm).generate(eq("match-analysis"), system.capture(), prompt.capture(), eq(MatchAnalysis.class));
        assertThat(system.getValue()).doesNotContain("Spring Boot and PostgreSQL");
        assertThat(prompt.getValue()).contains("<<<UNTRUSTED_JOB_POSTING>>>\nWe need a developer");
    }

    @Test
    void trackingAJobCopiesItsDetailsAndShowsOnTheCard() throws Exception {
        String token = newUser();
        long id = paste(token, "Tracked Job");
        JsonNode app = read(send(post("/applications"), token, Map.of("jobId", id)).andExpect(status().isCreated()));
        assertThat(app.get("jobTitle").asText()).isEqualTo("Tracked Job");
        assertThat(app.get("manual").asBoolean()).isFalse();
        send(post("/applications"), token, Map.of("jobId", id)).andExpect(status().isConflict());
        call(get("/jobs/mine"), token).andExpect(jsonPath("$[0].applicationStatus").value("APPLIED"));
    }

    @Test
    void feedMergesSourcesAndServesRepeatsFromCache() throws Exception {
        String token = newUser();
        String q = "rust-" + UUID.randomUUID();
        var adzunaJob = new NormalizedJob(JobSource.ADZUNA, "az-" + q, "Rust Developer", "Acme", null, "Edmonton",
                null, "https://adzuna.ca/1", "Adzuna", null, LocalDate.now(), "Rust");
        var himalayasJob = new NormalizedJob(JobSource.HIMALAYAS, "hm-" + q, "Remote Rust Engineer", "Beta", null,
                "Remote", null, "https://himalayas.app/1", "Himalayas", null, LocalDate.now().minusDays(1), "Rust");
        when(jobSources.adzunaEnabled()).thenReturn(true);
        when(jobSources.searchAdzuna(argThat(query -> q.equals(query.q())))).thenReturn(List.of(adzunaJob));
        when(jobSources.searchHimalayas(argThat(query -> q.equals(query.q())))).thenReturn(List.of(himalayasJob));

        call(get("/jobs/feed").param("q", q.toUpperCase()), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs.length()").value(2))
                .andExpect(jsonPath("$.jobs[0].title").value("Rust Developer"))
                .andExpect(jsonPath("$.sources.length()").value(2));
        call(get("/jobs/feed").param("q", " " + q + " "), token).andExpect(jsonPath("$.jobs.length()").value(2));
        verify(jobSources, times(1)).searchHimalayas(argThat(query -> q.equals(query.q())));
    }

    @Test
    void feedFallsBackToStoredJobsWhenEverySourceFails() throws Exception {
        String token = newUser();
        when(jobSources.adzunaEnabled()).thenReturn(false);
        when(jobSources.searchHimalayas(any())).thenThrow(new JobSources.SourceException("Himalayas could not be reached"));
        call(get("/jobs/feed").param("q", "nothing-" + UUID.randomUUID()), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.notice").value(org.hamcrest.Matchers.containsString("unavailable")));
    }
}
