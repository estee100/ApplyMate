package com.applymate;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApplicationTrackingTest extends IntegrationTest {

    private JsonNode createManual(String token, Map<String, Object> body) throws Exception {
        return read(send(post("/applications"), token, body).andExpect(status().isCreated()));
    }

    @Test
    void manualApplicationNeedsCompanyAndPosition() throws Exception {
        String token = newUser();
        send(post("/applications"), token, Map.of("companyName", "Acme"))
                .andExpect(status().isBadRequest());

        JsonNode app = createManual(token, Map.of("companyName", "Acme", "jobTitle", "Backend Developer"));
        assertThat(app.get("status").asText()).isEqualTo("APPLIED");
        assertThat(app.get("manual").asBoolean()).isTrue();
        assertThat(app.get("dateApplied").asText()).isEqualTo(LocalDate.now().toString());
    }

    @Test
    void javascriptUrlsAreRejected() throws Exception {
        String token = newUser();
        send(post("/applications"), token,
                Map.of("companyName", "Acme", "jobTitle", "Dev", "jobPostingUrl", "javascript:alert(1)"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusChangeWritesEventWithNote() throws Exception {
        String token = newUser();
        long id = createManual(token, Map.of("companyName", "Acme", "jobTitle", "Dev")).get("id").asLong();

        send(patch("/applications/" + id), token, Map.of("status", "INTERVIEWING", "note", "Invited by email"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERVIEWING"));

        JsonNode events = read(call(get("/applications/" + id + "/events"), token).andExpect(status().isOk()));
        assertThat(events).hasSize(2);
        assertThat(events.get(0).get("fromStatus").isNull()).isTrue();
        assertThat(events.get(1).get("fromStatus").asText()).isEqualTo("APPLIED");
        assertThat(events.get(1).get("toStatus").asText()).isEqualTo("INTERVIEWING");
        assertThat(events.get(1).get("note").asText()).isEqualTo("Invited by email");
    }

    @Test
    void noteWithoutStatusChangeIs422() throws Exception {
        String token = newUser();
        long id = createManual(token, Map.of("companyName", "Acme", "jobTitle", "Dev")).get("id").asLong();
        send(patch("/applications/" + id), token, Map.of("note", "Just a note"))
                .andExpect(status().isUnprocessableEntity());
        // Same status is not a change either.
        send(patch("/applications/" + id), token, Map.of("status", "APPLIED", "note", "Still applied"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void otherUsersApplicationsAre404() throws Exception {
        String owner = newUser();
        String stranger = newUser();
        long id = createManual(owner, Map.of("companyName", "Acme", "jobTitle", "Dev")).get("id").asLong();

        call(get("/applications/" + id), stranger).andExpect(status().isNotFound());
        send(patch("/applications/" + id), stranger, Map.of("status", "OFFER")).andExpect(status().isNotFound());
        call(delete("/applications/" + id), stranger).andExpect(status().isNotFound());
        call(get("/applications/" + id + "/communications"), stranger).andExpect(status().isNotFound());

        long commId = read(send(post("/applications/" + id + "/communications"), owner,
                Map.of("kind", "EMAIL", "direction", "RECEIVED", "subject", "Next steps")))
                .get("id").asLong();
        call(delete("/communications/" + commId), stranger).andExpect(status().isNotFound());
        call(delete("/communications/" + commId), owner).andExpect(status().isNoContent());
    }

    @Test
    void overdueNextStepNeedsFollowUpButClosedStagesNever() throws Exception {
        String token = newUser();
        String yesterday = LocalDate.now().minusDays(1).toString();
        long open = createManual(token, Map.of("companyName", "A", "jobTitle", "Dev",
                "nextAction", "Chase recruiter", "nextActionDate", yesterday)).get("id").asLong();
        long closed = createManual(token, Map.of("companyName", "B", "jobTitle", "Dev",
                "status", "REJECTED", "nextActionDate", yesterday)).get("id").asLong();

        call(get("/applications/" + open), token).andExpect(jsonPath("$.needsFollowUp").value(true));
        call(get("/applications/" + closed), token).andExpect(jsonPath("$.needsFollowUp").value(false));

        // Clearing the next step removes the flag (the application is brand new).
        send(patch("/applications/" + open), token, Map.of("clearNextAction", true))
                .andExpect(jsonPath("$.needsFollowUp").value(false))
                .andExpect(jsonPath("$.nextActionDate").doesNotExist());

        call(get("/applications/stats"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.active").value(1))
                .andExpect(jsonPath("$.byStatus.REJECTED").value(1));
    }

    @Test
    void analyticsCountsStagesReachedNotJustCurrentStatus() throws Exception {
        String token = newUser();
        long id = createManual(token, Map.of("companyName", "A", "jobTitle", "Dev")).get("id").asLong();
        createManual(token, Map.of("companyName", "B", "jobTitle", "Dev"));
        send(patch("/applications/" + id), token, Map.of("status", "INTERVIEWING"));
        send(patch("/applications/" + id), token, Map.of("status", "REJECTED"));

        call(get("/analytics/summary"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications").value(2))
                .andExpect(jsonPath("$.responseRatePct").value(50))
                .andExpect(jsonPath("$.interviewRatePct").value(50))
                .andExpect(jsonPath("$.offerRatePct").value(0))
                .andExpect(jsonPath("$.applicationsByMonth.length()").value(12));
    }
}
