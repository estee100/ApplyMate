package com.applymate.controller;

import com.applymate.ai.Schemas.EmailDraft;
import com.applymate.error.ApiException;
import com.applymate.service.CurrentUser;
import com.applymate.service.RateLimiter;
import com.applymate.service.ToolsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

@RestController
public class ToolsController {

    private final ToolsService tools;
    private final RateLimiter limiter;
    private final CurrentUser currentUser;

    public ToolsController(ToolsService tools, RateLimiter limiter, CurrentUser currentUser) {
        this.tools = tools;
        this.limiter = limiter;
        this.currentUser = currentUser;
    }

    public record EmailDraftRequest(@NotNull ToolsService.EmailPurpose purpose, @Size(max = 300) String extra) {}

    @GetMapping("/applications/progress")
    public ToolsService.Progress progress() {
        return tools.progress();
    }

    @GetMapping(value = "/applications/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> export() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"applymate-applications-" + LocalDate.now() + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(tools.exportCsv().getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping(value = "/applications/import", consumes = "multipart/form-data")
    public ToolsService.ImportResult importCsv(@RequestParam("file") MultipartFile file) {
        if (file.getSize() > 2 * 1024 * 1024) throw ApiException.badRequest("Import files must be 2 MB or smaller.");
        try {
            return tools.importCsv(new String(file.getBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw ApiException.badRequest("The upload could not be read.");
        }
    }

    @PostMapping("/applications/{id}/email-draft")
    public EmailDraft emailDraft(@PathVariable Long id, @Valid @RequestBody EmailDraftRequest request) {
        return tools.emailDraft(id, request.purpose(), request.extra());
    }

    @PostMapping("/profile/calendar-feed")
    public Map<String, String> rotateCalendarFeed() {
        return Map.of("url", tools.rotateCalendarToken());
    }

    @DeleteMapping("/profile/calendar-feed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeCalendarFeed() {
        tools.revokeCalendarToken();
    }

    /** Public: calendar apps poll this URL directly, so the secret in it is the credential. */
    @GetMapping(value = "/calendar/feed/{token}.ics", produces = "text/calendar")
    public ResponseEntity<String> calendarFeed(@PathVariable String token) {
        if (!token.matches("[a-f0-9]{48}")) throw ApiException.notFound("Calendar");
        limiter.check("ics:" + token, 60, Duration.ofHours(1), "Too many requests for this calendar.");
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "calendar", StandardCharsets.UTF_8))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=900")
                .body(tools.calendarFeed(token));
    }
}
