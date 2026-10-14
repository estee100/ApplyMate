package com.applymate;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthTest extends IntegrationTest {

    private String body(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void registerThenLoginIsCaseInsensitive() throws Exception {
        String email = "Case-" + UUID.randomUUID() + "@Example.com";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(email, "password123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(email.toUpperCase(), "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void duplicateEmailInDifferentCaseIsConflictNotServerError() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(email, "password123")))
                .andExpect(status().isCreated());
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(email.toUpperCase(), "password123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void wrongPasswordIs401WithMessage() throws Exception {
        String email = "wp-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(email, "password123")));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body(email, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Incorrect email or password."));
    }

    @Test
    void everyResponseCarriesARequestIdAndUnsafeIdsAreReplaced() throws Exception {
        mvc.perform(get("/health").header("X-Request-Id", "client-trace-1234"))
                .andExpect(header().string("X-Request-Id", "client-trace-1234"));
        // A header that could forge log lines is not echoed.
        String replaced = mvc.perform(get("/health").header("X-Request-Id", "evil value <script> with spaces"))
                .andReturn().getResponse().getHeader("X-Request-Id");
        org.assertj.core.api.Assertions.assertThat(replaced).matches("[a-f0-9]{16}");
    }

    @Test
    void openApiDocumentDescribesTheApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("ApplyMate API"))
                .andExpect(jsonPath("$.paths['/jobs/feed']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"));
    }

    @Test
    void missingOrBadTokenIs401NotForbidden() throws Exception {
        mvc.perform(get("/applications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/applications").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }
}
