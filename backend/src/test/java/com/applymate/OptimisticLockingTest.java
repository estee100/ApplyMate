package com.applymate;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OptimisticLockingTest extends IntegrationTest {

    private JsonNode createApp(String token) throws Exception {
        return read(send(post("/applications"), token, Map.of("companyName", "Acme", "jobTitle", "Dev"))
                .andExpect(status().isCreated()));
    }

    @Test
    void aStaleVersionIsAConflictNotASilentOverwrite() throws Exception {
        String token = newUser();
        JsonNode app = createApp(token);
        long id = app.get("id").asLong();
        long v0 = app.get("version").asLong();

        // Tab A saves first.
        JsonNode afterA = read(send(patch("/applications/" + id), token, Map.of("notes", "from tab A", "version", v0))
                .andExpect(status().isOk()));
        assertThat(afterA.get("version").asLong()).isEqualTo(v0 + 1);

        // Tab B still holds v0: its save is refused and tab A's notes survive.
        send(patch("/applications/" + id), token, Map.of("notes", "from tab B", "version", v0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("another tab")));
        call(get("/applications/" + id), token).andExpect(jsonPath("$.notes").value("from tab A"));
    }

    @Test
    void concurrentEditsFromTheSameVersionHaveExactlyOneWinner() throws Exception {
        String token = newUser();
        JsonNode app = createApp(token);
        long id = app.get("id").asLong();
        long v0 = app.get("version").asLong();

        int writers = 5;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < writers; i++) {
            String notes = "writer " + i;
            results.add(pool.submit(() -> {
                start.await();
                return send(patch("/applications/" + id), token, Map.of("notes", notes, "version", v0))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) statuses.add(f.get(30, TimeUnit.SECONDS));
        pool.shutdown();

        // Whether a loser is caught by the version check or by Hibernate's version
        // predicate at flush, it is a 409; nobody's write is silently lost.
        assertThat(statuses).containsOnly(200, 409);
        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
        call(get("/applications/" + id), token).andExpect(jsonPath("$.version").value(v0 + 1));
    }

    @Test
    void documentsAndTheMasterResumeAreVersionedToo() throws Exception {
        String token = newUser();
        when(llm.generate(org.mockito.ArgumentMatchers.eq("skill-extraction"), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(com.applymate.ai.Schemas.SkillExtraction.class)))
                .thenThrow(com.applymate.ai.AiException.disabled());
        mvc.perform(multipart("/resumes").file(new org.springframework.mock.web.MockMultipartFile("file", "cv.txt", "text/plain",
                ResumeAndAiTest.RESUME.getBytes(java.nio.charset.StandardCharsets.UTF_8))).header("Authorization", "Bearer " + token));

        long masterVersion = read(call(get("/resumes/active/master"), token)).get("version").asLong();
        var master = Map.of("fullName", "Jordan", "sections", List.of(), "skills", List.of());
        send(put("/resumes/active/master?version=" + masterVersion), token, master).andExpect(status().isOk());
        send(put("/resumes/active/master?version=" + masterVersion), token, master).andExpect(status().isConflict());

        long job = read(send(post("/jobs/paste-text"), token, Map.of("title", "Dev", "company", "Acme",
                "description", "We need a Java developer for REST APIs and PostgreSQL work. " + java.util.UUID.randomUUID())))
                .get("job").get("id").asLong();
        JsonNode doc = read(call(post("/jobs/" + job + "/documents/from-master"), token));
        long docVersion = doc.get("version").asLong();
        var edit = Map.of("content", Map.of("fullName", "Jordan L."), "version", docVersion);
        send(put("/documents/" + doc.get("id").asLong()), token, edit).andExpect(status().isOk());
        send(put("/documents/" + doc.get("id").asLong()), token, edit).andExpect(status().isConflict());
    }

    private static <T> org.mockito.stubbing.OngoingStubbing<T> when(T call) {
        return org.mockito.Mockito.when(call);
    }
}
