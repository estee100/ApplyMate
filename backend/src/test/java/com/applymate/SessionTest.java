package com.applymate;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SessionTest extends IntegrationTest {

    private JsonNode register() throws Exception {
        String email = "session-" + UUID.randomUUID() + "@example.com";
        return read(mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}")).andExpect(status().isCreated()));
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", token))));
    }

    @Test
    void refreshRotatesTheToken() throws Exception {
        JsonNode first = register();
        assertThat(first.get("expiresIn").asLong()).isEqualTo(900);
        JsonNode second = read(refresh(first.get("refreshToken").asText()).andExpect(status().isOk()));
        assertThat(second.get("refreshToken").asText()).isNotEqualTo(first.get("refreshToken").asText());
        call(get("/profile"), second.get("token").asText()).andExpect(status().isOk());
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeFamily() throws Exception {
        JsonNode first = register();
        String stolen = first.get("refreshToken").asText();
        String legit = read(refresh(stolen)).get("refreshToken").asText();

        clock.advance(Duration.ofMinutes(1)); // well past the multi-tab grace window
        refresh(stolen).andExpect(status().isUnauthorized());
        // The legitimate holder's newer token died with the family.
        refresh(legit).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentTabsWithinTheGraceWindowDoNotRevokeTheFamily() throws Exception {
        JsonNode first = register();
        String shared = first.get("refreshToken").asText();
        String winner = read(refresh(shared)).get("refreshToken").asText();
        refresh(shared).andExpect(status().isUnauthorized()); // the losing tab
        refresh(winner).andExpect(status().isOk());           // the session survives
    }

    @Test
    void expiredRefreshTokensAreRejected() throws Exception {
        String token = register().get("refreshToken").asText();
        clock.advance(Duration.ofDays(31));
        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void accessTokensExpireAfterFifteenMinutes() throws Exception {
        String access = register().get("token").asText();
        call(get("/profile"), access).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(16));
        call(get("/profile"), access).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutAndPasswordChangeEndSessions() throws Exception {
        JsonNode a = register();
        mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", a.get("refreshToken").asText()))))
                .andExpect(status().isNoContent());
        refresh(a.get("refreshToken").asText()).andExpect(status().isUnauthorized());

        JsonNode b = register();
        JsonNode changed = read(send(post("/auth/change-password"), b.get("token").asText(),
                Map.of("currentPassword", "password123", "newPassword", "brand-new-pass")).andExpect(status().isOk()));
        refresh(b.get("refreshToken").asText()).andExpect(status().isUnauthorized());
        refresh(changed.get("refreshToken").asText()).andExpect(status().isOk());
    }
}
