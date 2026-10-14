package com.applymate;

import com.applymate.ai.LlmClient;
import com.applymate.jobs.JobSources;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full Spring context against a real Postgres (Testcontainers), so Flyway
 * migrations and JSONB columns are exercised exactly as in production.
 * One container is shared by every test class through the cached context.
 */
@SpringBootTest(properties = {
        "app.ai.api-key=test-key",
        "app.ai.daily-limit-per-user=5",
        "app.ai.per-minute-limit-per-user=100",
        // Every test registers from the same MockMvc address.
        "app.auth.register-per-hour-per-ip=100000",
        "app.auth.login-per-15-minutes-per-ip=100000"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    /** The model is always mocked: tests never call the network or spend quota. */
    @MockitoBean
    protected LlmClient llm;

    /** Job sources are mocked too; their parsing is unit-tested in JobParsingTest. */
    @MockitoBean
    protected JobSources jobSources;

    /** Registers a fresh user and returns a bearer token for them. */
    protected String newUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String body = mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    protected ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    protected ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mvc.perform(request
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    protected JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
