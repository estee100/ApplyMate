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
    void missingOrBadTokenIs401NotForbidden() throws Exception {
        mvc.perform(get("/applications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/applications").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }
}
