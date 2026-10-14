package com.applymate;

import com.applymate.ai.LlmClient;
import com.applymate.ai.Schemas.EmailDraft;
import com.applymate.util.Csv;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ToolsTest extends IntegrationTest {

    @Test
    void csvParserHandlesQuotesNewlinesAndBom() {
        List<List<String>> rows = Csv.parse("﻿a,b,c\r\n\"x, y\",\"he said \"\"hi\"\"\",\"line1\nline2\"\n");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1)).containsExactly("x, y", "he said \"hi\"", "line1\nline2");
    }

    @Test
    void csvExportNeutralisesFormulas() {
        assertThat(Csv.field("=HYPERLINK(\"http://evil\")")).startsWith("\"'=HYPERLINK");
        assertThat(Csv.field("+1 555")).isEqualTo("'+1 555");
        assertThat(Csv.field("plain")).isEqualTo("plain");
    }

    @Test
    void importReportsBadRowsByLineAndRoundTripsThroughExport() throws Exception {
        String token = newUser();
        String csv = """
                Company,Job Title,Status,Date Applied,Link,Notes
                Acme,Backend Developer,Interviewing,2026-09-01,https://acme.example/jobs/1,"Spoke to Priya, went well"
                Globex,Data Analyst,Applied - no response,"Sep 3, 2026",,
                ,Missing Company,Applied,2026-09-04,,
                Initech,QA,Rejected after interview,03/04/2026,,
                Hooli,Dev,Applied,2026-09-05,javascript:alert(1),
                =EVIL(),Formula Co,Offer,2026-09-06,,
                """;
        JsonNode result = read(mvc.perform(multipart("/applications/import")
                        .file(new MockMultipartFile("file", "tracker.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
        assertThat(result.get("imported").asInt()).isEqualTo(3);
        assertThat(result.get("skipped").findValuesAsText("line")).containsExactly("4", "5", "6");
        assertThat(result.get("skipped").get(1).get("reason").asText()).contains("2026-10-14"); // ambiguous date refused

        JsonNode apps = read(call(get("/applications"), token));
        assertThat(apps.findValuesAsText("status")).containsExactlyInAnyOrder("INTERVIEWING", "APPLIED", "OFFER");

        String export = call(get("/applications/export.csv"), token)
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(export).contains("\"Spoke to Priya, went well\"");
        assertThat(export).contains("'=EVIL()"); // a formula never survives into a spreadsheet
    }

    @Test
    void calendarFeedIsPrivateRevocableAndValidIcs() throws Exception {
        String token = newUser();
        send(post("/applications"), token, Map.of("companyName", "Acme, Inc; Ltd", "jobTitle", "Developer",
                "nextAction", "Technical interview with the platform team, which runs long enough to need folding",
                "nextActionDate", LocalDate.now().plusDays(2).toString()));

        String url = read(call(post("/profile/calendar-feed"), token)).get("url").asText();
        String path = url.substring(url.indexOf("/calendar/feed/"));
        String ics = mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("BEGIN:VEVENT").contains("Acme\\, Inc\\; Ltd"); // RFC 5545 text escaping
        for (String line : ics.split("\r\n")) {
            assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
        call(get("/profile"), token).andExpect(jsonPath("$.calendarFeedEnabled").value(true));

        call(delete("/profile/calendar-feed"), token).andExpect(status().isNoContent());
        mvc.perform(get(path)).andExpect(status().isNotFound());
        mvc.perform(get("/calendar/feed/" + "ab".repeat(24) + ".ics")).andExpect(status().isNotFound());
    }

    @Test
    void weeklyProgressCountsAStreakWithoutPenalisingTheCurrentWeek() throws Exception {
        String token = newUser();
        send(patch("/profile"), token, Map.of("weeklyGoal", 2)).andExpect(status().isOk());
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int week = 1; week <= 2; week++) {
            for (int i = 0; i < 2; i++) {
                send(post("/applications"), token, Map.of("companyName", "C" + week + i, "jobTitle", "Dev",
                        "dateApplied", monday.minusWeeks(week).plusDays(i).toString()));
            }
        }
        send(post("/applications"), token, Map.of("companyName", "Now", "jobTitle", "Dev", "dateApplied", monday.toString()));

        call(get("/applications/progress"), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.goal").value(2))
                .andExpect(jsonPath("$.thisWeek").value(1))
                .andExpect(jsonPath("$.streakWeeks").value(2)) // this week isn't over: the streak stands
                .andExpect(jsonPath("$.recentWeeks.length()").value(8));
    }

    @Test
    void emailDraftUsesTheApplicationAndSendsNothing() throws Exception {
        String token = newUser();
        long id = read(send(post("/applications"), token, Map.of("companyName", "Acme", "jobTitle", "Backend Developer"))).get("id").asLong();
        when(llm.generate(eq("email-draft"), anyString(), anyString(), eq(EmailDraft.class)))
                .thenReturn(new LlmClient.Result<>(new EmailDraft("Following up on Backend Developer", "Hi there, ..."), "m"));

        send(post("/applications/" + id + "/email-draft"), token, Map.of("purpose", "FOLLOW_UP", "extra", "I can start in May"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Following up on Backend Developer"));

        var prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(llm).generate(eq("email-draft"), anyString(), prompt.capture(), eq(EmailDraft.class));
        assertThat(prompt.getValue()).contains("Company: Acme").contains("I can start in May");
        verify(mailTransport, never()).send(any(), any(), any());

        String stranger = newUser();
        send(post("/applications/" + id + "/email-draft"), stranger, Map.of("purpose", "FOLLOW_UP")).andExpect(status().isNotFound());
    }
}
