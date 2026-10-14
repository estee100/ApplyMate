package com.applymate;

import com.applymate.ai.LlmClient;
import com.applymate.ai.Schemas.*;
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

class DocumentsTest extends IntegrationTest {

    private static final Map<String, Object> MASTER = Map.of(
            "fullName", "Jordan Lee", "headline", "", "contactLine", "jordan@example.com", "summary", "",
            "sections", List.of(Map.of("heading", "Experience", "bullets", List.of(),
                    "entries", List.of(Map.of("title", "Developer Intern", "subtitle", "Acme", "right", "2025",
                            "subtitleRight", "Edmonton", "meta", "", "bullets", List.of("Built APIs", "Wrote tests"))))),
            "skills", List.of("Languages: Java, SQL"));

    private String userWithResume(boolean withMaster) throws Exception {
        String token = newUser();
        when(llm.generate(eq("skill-extraction"), anyString(), anyString(), eq(SkillExtraction.class)))
                .thenReturn(new LlmClient.Result<>(new SkillExtraction(List.of("Java"), List.of("Developer"),
                        List.of(), List.of(), "junior", 1, "x"), "m"));
        mvc.perform(multipart("/resumes").file(new MockMultipartFile("file", "cv.txt", "text/plain",
                ResumeAndAiTest.RESUME.getBytes(StandardCharsets.UTF_8))).header("Authorization", "Bearer " + token));
        if (withMaster) send(put("/resumes/active/master"), token, MASTER).andExpect(status().isOk());
        return token;
    }

    private long paste(String token) throws Exception {
        return read(send(post("/jobs/paste-text"), token, Map.of("title", "Backend Developer", "company", "Acme",
                "description", "We need a Java developer for REST APIs and PostgreSQL. " + UUID.randomUUID())))
                .get("job").get("id").asLong();
    }

    @Test
    void fromMasterCopiesExactlyWithoutAi() throws Exception {
        String token = userWithResume(false);
        long job = paste(token);
        call(post("/jobs/" + job + "/documents/from-master"), token).andExpect(status().isUnprocessableEntity());

        send(put("/resumes/active/master"), token, MASTER);
        JsonNode doc = read(call(post("/jobs/" + job + "/documents/from-master"), token).andExpect(status().isCreated()));
        assertThat(doc.get("modelUsed").asText()).isEqualTo("master-copy");
        assertThat(doc.get("content").get("sections").get(0).get("entries").get(0).get("subtitle").asText()).isEqualTo("Acme");
        verify(llm, never()).generate(eq("resume-tailoring"), anyString(), anyString(), any());
    }

    @Test
    void tailoredResumeAppliesEditsToMasterAndEditsCanBeReset() throws Exception {
        String token = userWithResume(true);
        long job = paste(token);
        when(llm.generate(eq("resume-tailoring"), anyString(), anyString(), eq(MasterTailoring.class)))
                .thenReturn(new LlmClient.Result<>(new MasterTailoring("", "", List.of(new SectionTailoring(0,
                        List.of(new EntryTailoring(0, List.of("Designed REST APIs in Java"))), List.of())),
                        List.of("Languages: SQL, Java")), "m"));

        JsonNode doc = read(send(post("/jobs/" + job + "/documents/resume"), token, Map.of("instructions", "Focus on APIs"))
                .andExpect(status().isCreated()));
        long id = doc.get("id").asLong();
        JsonNode entry = doc.get("content").get("sections").get(0).get("entries").get(0);
        assertThat(entry.get("bullets").toString()).isEqualTo("[\"Designed REST APIs in Java\"]");
        assertThat(entry.get("subtitle").asText()).isEqualTo("Acme");

        // The master was sent indexed, and the user's steering reached the prompt.
        var prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(llm).generate(eq("resume-tailoring"), anyString(), prompt.capture(), eq(MasterTailoring.class));
        assertThat(prompt.getValue()).contains("[entry 0] Developer Intern").contains("The candidate asks you to: Focus on APIs");

        var edited = json.createObjectNode().put("fullName", "Jordan L.");
        send(put("/documents/" + id), token, Map.of("content", edited))
                .andExpect(jsonPath("$.edited").value(true))
                .andExpect(jsonPath("$.content.fullName").value("Jordan L."))
                .andExpect(jsonPath("$.original.fullName").value("Jordan Lee"));
        call(post("/documents/" + id + "/reset"), token)
                .andExpect(jsonPath("$.edited").value(false))
                .andExpect(jsonPath("$.content.fullName").value("Jordan Lee"));

        String stranger = newUser();
        call(get("/documents/" + id), stranger).andExpect(status().isNotFound());
    }

    @Test
    void keywordCoverageAndTemplateFollowTheEditedDocument() throws Exception {
        String token = userWithResume(true);
        long job = read(send(post("/jobs/paste-text"), token, Map.of("title", "Backend Developer", "company", "Acme",
                "description", "Build APIs with Java, Kafka and Kubernetes on AWS. " + UUID.randomUUID()))).get("job").get("id").asLong();
        JsonNode doc = read(call(post("/jobs/" + job + "/documents/from-master"), token));
        long id = doc.get("id").asLong();
        assertThat(doc.get("template").asText()).isEqualTo("CLASSIC");

        JsonNode before = read(call(get("/documents/" + id + "/keywords"), token).andExpect(status().isOk()));
        assertThat(before.get("found").toString()).contains("Java");
        assertThat(before.get("missing").toString()).contains("Kafka");

        var edited = json.readTree(doc.get("content").toString());
        ((com.fasterxml.jackson.databind.node.ArrayNode) edited.get("skills")).add("Streaming: Kafka");
        send(put("/documents/" + id), token, Map.of("content", edited)).andExpect(status().isOk());
        JsonNode after = read(call(get("/documents/" + id + "/keywords"), token));
        assertThat(after.get("found").toString()).contains("Kafka");
        assertThat(after.get("coveragePct").asInt()).isGreaterThan(before.get("coveragePct").asInt());

        send(put("/documents/" + id + "/template"), token, Map.of("template", "MODERN"))
                .andExpect(jsonPath("$.template").value("MODERN"));
    }

    @Test
    void coverLetterIsNormalised() throws Exception {
        String token = userWithResume(false);
        long job = paste(token);
        when(llm.generate(eq("cover-letter"), anyString(), anyString(), eq(CoverLetter.class)))
                .thenReturn(new LlmClient.Result<>(new CoverLetter("Dear Hiring Team,", List.of("One.", " ", "Two."), "Sincerely,"), "m"));
        call(post("/jobs/" + job + "/documents/cover-letter"), token).andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("COVER_LETTER"))
                .andExpect(jsonPath("$.content.paragraphs.length()").value(2));
    }

    @Test
    void interviewPrepOnlyAtInterviewStageAndOnlyOnceUnlessRefreshed() throws Exception {
        String token = userWithResume(true);
        long job = paste(token);
        long app = read(send(post("/applications"), token, Map.of("jobId", job))).get("id").asLong();
        call(post("/applications/" + app + "/interview-prep"), token).andExpect(status().isUnprocessableEntity());

        send(patch("/applications/" + app), token, Map.of("status", "INTERVIEWING"));
        when(llm.generate(eq("interview-prep"), anyString(), anyString(), eq(InterviewPlan.class)))
                .thenReturn(new LlmClient.Result<>(new InterviewPlan("Focus on APIs.",
                        List.of(new PrepFocus("REST design", "Core of the role", List.of("Review status codes"))),
                        List.of(new PrepQuestion("Tell me about an API you built", "Use the Acme project")),
                        List.of("What does on-call look like?"), List.of("No Kubernetes")), "m"));

        call(post("/applications/" + app + "/interview-prep"), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.focusAreas[0].topic").value("REST design"));
        call(post("/applications/" + app + "/interview-prep"), token).andExpect(status().isOk());
        verify(llm, times(1)).generate(eq("interview-prep"), anyString(), anyString(), eq(InterviewPlan.class));
        call(get("/applications/" + app + "/interview-prep"), token).andExpect(jsonPath("$.modelUsed").value("m"));

        // Hand-logged applications are refused before any model call.
        long manual = read(send(post("/applications"), token, Map.of("companyName", "B", "jobTitle", "Dev",
                "status", "INTERVIEWING"))).get("id").asLong();
        call(post("/applications/" + manual + "/interview-prep"), token).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void historyCombinesApplicationsAndDocumentsAndCalendarShowsNextSteps() throws Exception {
        String token = userWithResume(true);
        long job = paste(token);
        call(post("/jobs/" + job + "/documents/from-master"), token);
        send(post("/applications"), token, Map.of("jobId", job, "nextAction", "Follow up",
                "nextActionDate", LocalDate.now().plusDays(3).toString()));
        send(post("/applications"), token, Map.of("companyName", "Elsewhere", "jobTitle", "Analyst"));

        JsonNode history = read(call(get("/history"), token).andExpect(status().isOk()));
        assertThat(history).hasSize(2);
        JsonNode tracked = history.get(0).get("jobId").isNull() ? history.get(1) : history.get(0);
        assertThat(tracked.get("documents")).hasSize(1);
        assertThat(tracked.get("application").get("status").asText()).isEqualTo("APPLIED");

        call(get("/calendar").param("from", LocalDate.now().minusDays(1).toString())
                .param("to", LocalDate.now().plusDays(7).toString()), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'NEXT_ACTION')].title").value(org.hamcrest.Matchers.hasItem(
                        "Follow up: Backend Developer at Acme")));
        call(get("/calendar").param("from", "2020-01-01").param("to", "2026-01-01"), token).andExpect(status().isBadRequest());
    }
}
