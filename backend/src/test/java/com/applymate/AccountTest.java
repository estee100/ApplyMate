package com.applymate;

import com.applymate.model.PasswordResetToken;
import com.applymate.repository.PasswordResetTokenRepository;
import com.applymate.repository.UserRepository;
import com.applymate.util.Text;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountTest extends IntegrationTest {

    @Autowired
    UserRepository users;

    @Autowired
    PasswordResetTokenRepository tokens;

    private String register(String email) throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void forgotPasswordAnswersTheSameForUnknownEmails() throws Exception {
        String known = "known-" + UUID.randomUUID() + "@example.com";
        register(known);
        String a = mvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + known + "\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String b = mvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody-" + UUID.randomUUID() + "@example.com\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertThat(a).isEqualTo(b);
    }

    @Test
    void resetChangesPasswordRevokesSessionsAndIsSingleUse() throws Exception {
        String email = "reset-" + UUID.randomUUID() + "@example.com";
        String oldToken = register(email);
        Long userId = users.findByEmail(email).orElseThrow().getId();
        tokens.save(new PasswordResetToken(userId, Text.sha256("known-token"), Instant.now().plusSeconds(600)));

        send(post("/auth/reset-password"), oldToken, Map.of("token", "known-token", "password", "brand-new-pass"))
                .andExpect(status().isOk());

        call(get("/profile"), oldToken).andExpect(status().isUnauthorized());
        login(email, "password123").andExpect(status().isUnauthorized());
        login(email, "brand-new-pass").andExpect(status().isOk());
        send(post("/auth/reset-password"), oldToken, Map.of("token", "known-token", "password", "another-pass"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void expiredResetLinksAreRefused() throws Exception {
        String email = "expired-" + UUID.randomUUID() + "@example.com";
        register(email);
        Long userId = users.findByEmail(email).orElseThrow().getId();
        tokens.save(new PasswordResetToken(userId, Text.sha256("old-token"), Instant.now().minusSeconds(1)));
        mvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"old-token\",\"password\":\"brand-new-pass\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePasswordReturnsAFreshTokenAndRevokesTheOldOne() throws Exception {
        String email = "change-" + UUID.randomUUID() + "@example.com";
        String oldToken = register(email);
        send(post("/auth/change-password"), oldToken, Map.of("currentPassword", "wrong-one", "newPassword", "brand-new-pass"))
                .andExpect(status().isBadRequest());
        String fresh = read(send(post("/auth/change-password"), oldToken,
                Map.of("currentPassword", "password123", "newPassword", "brand-new-pass"))
                .andExpect(status().isOk())).get("token").asText();
        call(get("/profile"), oldToken).andExpect(status().isUnauthorized());
        call(get("/profile"), fresh).andExpect(status().isOk());
    }

    @Test
    void loginIsRateLimitedPerEmail() throws Exception {
        String email = "brute-" + UUID.randomUUID() + "@example.com";
        register(email);
        for (int i = 0; i < 10; i++) login(email, "wrong-password").andExpect(status().isUnauthorized());
        login(email, "password123").andExpect(status().isTooManyRequests());
    }

    @Test
    void deletingTheAccountNeedsThePasswordAndRemovesEverything() throws Exception {
        String email = "delete-" + UUID.randomUUID() + "@example.com";
        String token = register(email);
        send(post("/applications"), token, Map.of("companyName", "Acme", "jobTitle", "Dev"));
        send(delete("/profile"), token, Map.of("password", "nope")).andExpect(status().isBadRequest());
        send(delete("/profile"), token, Map.of("password", "password123")).andExpect(status().isNoContent());
        assertThat(users.findByEmail(email)).isEmpty();
        call(get("/applications"), token).andExpect(status().isUnauthorized());
    }
}
