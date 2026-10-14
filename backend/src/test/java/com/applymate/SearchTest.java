package com.applymate;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SearchTest extends IntegrationTest {

    @Test
    void prefixSearchFindsApplicationsAndRanksTitleMatchesFirst() throws Exception {
        String token = newUser();
        send(post("/applications"), token, Map.of("companyName", "Globex", "jobTitle", "Data Analyst",
                "notes", "Mentioned they use Kubernetes heavily"));
        send(post("/applications"), token, Map.of("companyName", "Initech", "jobTitle", "Kubernetes Platform Engineer"));

        JsonNode results = read(call(get("/search").param("q", "kube"), token).andExpect(status().isOk()));
        JsonNode apps = results.get("applications");
        assertThat(apps).hasSize(2);
        // Title (weight A) outranks notes (weight D).
        assertThat(apps.get(0).get("companyName").asText()).isEqualTo("Initech");
        // The notes match comes back with a highlighted fragment.
        JsonNode snippet = apps.get(1).get("snippet");
        int[] range = json.convertValue(snippet.get("highlights").get(0), int[].class);
        assertThat(snippet.get("text").asText().substring(range[0], range[1])).isEqualTo("Kubernetes");
    }

    @Test
    void searchIsScopedToTheUsersOwnData() throws Exception {
        String owner = newUser();
        String stranger = newUser();
        String unique = "zyx" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        send(post("/jobs/paste-text"), owner, Map.of("title", "Engineer " + unique, "company", "Secretco",
                "description", "A private advert the owner pasted, mentioning Java and Spring Boot at length."));
        send(post("/applications"), owner, Map.of("companyName", unique, "jobTitle", "Dev"));

        call(get("/search").param("q", unique), owner)
                .andExpect(jsonPath("$.jobs.length()").value(1))
                .andExpect(jsonPath("$.applications.length()").value(1));
        call(get("/search").param("q", unique), stranger)
                .andExpect(jsonPath("$.jobs.length()").value(0))
                .andExpect(jsonPath("$.applications.length()").value(0));
    }

    @Test
    void tsquerySyntaxIsNeutralised() throws Exception {
        String token = newUser();
        // Raw to_tsquery would throw on these; they are reduced to plain words or nothing.
        for (String q : new String[]{"a & | !", "((", "java:* | !spring", "':*", "   "}) {
            call(get("/search").param("q", q), token).andExpect(status().isOk());
        }
    }
}
