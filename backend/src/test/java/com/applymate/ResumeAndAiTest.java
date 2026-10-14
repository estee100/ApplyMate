package com.applymate;

import com.applymate.ai.AiException;
import com.applymate.ai.LlmClient;
import com.applymate.ai.Schemas.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ResumeAndAiTest extends IntegrationTest {

    static final String RESUME = """
            Jordan Lee
            jordan@example.com | github.com/jlee
            Education: University of Alberta, BSc Computer Science, 2021-2026
            Experience: Software Developer Intern, Acme Corp, Edmonton, May-Aug 2025
            - Built REST APIs in Java and Spring Boot serving 10k requests a day
            - Wrote PostgreSQL migrations and reduced query time by 40 percent
            Skills: Java, Python, SQL, React, Docker
            """;

    private MockMultipartFile file(String text) {
        return new MockMultipartFile("file", "resume.txt", "text/plain", text.getBytes(StandardCharsets.UTF_8));
    }

    private void mockSkills() {
        when(llm.generate(eq("skill-extraction"), anyString(), anyString(), eq(SkillExtraction.class)))
                .thenReturn(new LlmClient.Result<>(new SkillExtraction(
                        List.of("Java", "Python", "java", " SQL "), List.of("Software Developer"), List.of(),
                        List.of("Edmonton"), "junior", 0.5, "A student developer."), "test-model"));
    }

    @Test
    void uploadExtractsTextAndBuildsSkillProfile() throws Exception {
        String token = newUser();
        mockSkills();
        JsonNode body = read(mvc.perform(multipart("/resumes").file(file(RESUME)).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()));

        assertThat(body.get("resume").get("active").asBoolean()).isTrue();
        // Duplicates (case-insensitive) and whitespace are cleaned from model output.
        assertThat(body.get("skillProfile").get("skills").toString()).isEqualTo("[\"Java\",\"Python\",\"SQL\"]");
        assertThat(body.get("warning").isNull()).isTrue();

        call(get("/profile"), token)
                .andExpect(jsonPath("$.skillProfile.jobTitles[0]").value("Software Developer"))
                .andExpect(jsonPath("$.ai.usedToday").value(1));
    }

    @Test
    void uploadStillSucceedsWhenAiIsRateLimited() throws Exception {
        String token = newUser();
        when(llm.generate(eq("skill-extraction"), anyString(), anyString(), eq(SkillExtraction.class)))
                .thenThrow(AiException.rateLimited("Wait a minute and try again."));

        JsonNode body = read(mvc.perform(multipart("/resumes").file(file(RESUME)).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()));
        assertThat(body.get("skillProfile").isNull()).isTrue();
        assertThat(body.get("warning").asText()).contains("Wait a minute");
        // A failed call is refunded.
        call(get("/profile"), token).andExpect(jsonPath("$.ai.usedToday").value(0));
    }

    @Test
    void tooLittleTextIsRejected() throws Exception {
        String token = newUser();
        mvc.perform(multipart("/resumes").file(file("Just a name")).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void binaryDisguisedAsTextIsRejected() throws Exception {
        String token = newUser();
        byte[] junk = new byte[400];
        for (int i = 0; i < junk.length; i++) junk[i] = (byte) (i % 7 == 0 ? 0xC3 : 0x01);
        mvc.perform(multipart("/resumes").file(new MockMultipartFile("file", "cv.txt", "text/plain", junk))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dailyQuotaIsEnforcedPerUser() throws Exception {
        String token = newUser();
        mockSkills();
        mvc.perform(multipart("/resumes").file(file(RESUME)).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
        // Daily limit is 5 in tests; the upload used one.
        for (int i = 0; i < 4; i++) {
            call(post("/resumes/active/skill-profile/refresh"), token).andExpect(status().isOk());
        }
        call(post("/resumes/active/skill-profile/refresh"), token)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("today")));

        // Another user is unaffected.
        String other = newUser();
        mvc.perform(multipart("/resumes").file(file(RESUME)).header("Authorization", "Bearer " + other))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warning").doesNotExist());
    }

    @Test
    void masterResumeIsNormalisedAndSurvivesANewUpload() throws Exception {
        String token = newUser();
        mockSkills();
        mvc.perform(multipart("/resumes").file(file(RESUME)).header("Authorization", "Bearer " + token));

        var master = Map.of(
                "fullName", "  Jordan Lee  ",
                "headline", "",
                "contactLine", "jordan@example.com",
                "summary", "",
                "sections", List.of(Map.of("heading", "Experience", "bullets", List.of(),
                        "entries", List.of(Map.of("title", "Developer Intern", "subtitle", "Acme",
                                "right", "2025", "bullets", List.of("Built APIs", "  "))))),
                "skills", List.of("Languages: Java, SQL"));
        send(put("/resumes/active/master"), token, master)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.fullName").value("Jordan Lee"))
                .andExpect(jsonPath("$.content.sections[0].entries[0].bullets.length()").value(1))
                .andExpect(jsonPath("$.content.sections[0].entries[0].meta").value(""));

        mvc.perform(multipart("/resumes").file(file(RESUME + "\nNew line")).header("Authorization", "Bearer " + token));
        call(get("/resumes/active/master"), token)
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.content.fullName").value("Jordan Lee"));
        call(get("/resumes"), token).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void masterNeedsAResume() throws Exception {
        String token = newUser();
        call(get("/resumes/active/master"), token).andExpect(jsonPath("$.saved").value(false));
        send(put("/resumes/active/master"), token, Map.of("fullName", "X"))
                .andExpect(status().isUnprocessableEntity());
    }
}
