package com.applymate.controller;

import com.applymate.dto.DocumentDtos.*;
import com.applymate.error.ApiException;
import com.applymate.service.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
public class DocumentController {

    private final DocumentService documents;
    private final InterviewPrepService interviewPrep;
    private final HistoryService history;
    private final RecommendationService recommendations;

    public DocumentController(DocumentService documents, InterviewPrepService interviewPrep, HistoryService history,
                              RecommendationService recommendations) {
        this.documents = documents;
        this.interviewPrep = interviewPrep;
        this.history = history;
        this.recommendations = recommendations;
    }

    @PostMapping("/jobs/{jobId}/documents/from-master")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse fromMaster(@PathVariable Long jobId) {
        return documents.fromMaster(jobId);
    }

    @PostMapping("/jobs/{jobId}/documents/resume")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse resume(@PathVariable Long jobId, @Valid @RequestBody(required = false) GenerateRequest request) {
        return documents.tailoredResume(jobId, request == null ? null : request.instructions());
    }

    @PostMapping("/jobs/{jobId}/documents/cover-letter")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse coverLetter(@PathVariable Long jobId, @Valid @RequestBody(required = false) GenerateRequest request) {
        return documents.coverLetter(jobId, request == null ? null : request.instructions());
    }

    @GetMapping("/documents/{id}")
    public DocumentResponse get(@PathVariable Long id) {
        return documents.get(id);
    }

    @PutMapping("/documents/{id}")
    public DocumentResponse save(@PathVariable Long id, @Valid @RequestBody SaveDocumentRequest request) {
        return documents.saveEdit(id, request.content(), request.version());
    }

    @PostMapping("/documents/{id}/reset")
    public DocumentResponse reset(@PathVariable Long id) {
        return documents.reset(id);
    }

    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        documents.delete(id);
    }

    @GetMapping("/applications/{id}/interview-prep")
    public InterviewPrepResponse getPrep(@PathVariable Long id) {
        return interviewPrep.get(id);
    }

    @PostMapping("/applications/{id}/interview-prep")
    public InterviewPrepResponse generatePrep(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean refresh) {
        return interviewPrep.generate(id, refresh);
    }

    @GetMapping("/history")
    public List<HistoryEntry> history() {
        return history.history();
    }

    @GetMapping("/calendar")
    public List<CalendarEvent> calendar(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 400) {
            throw ApiException.badRequest("Choose a date range of at most 400 days.");
        }
        return history.calendar(from, to);
    }

    @GetMapping("/jobs/recommended")
    public RecommendationService.Recommendations recommended() {
        return recommendations.recommend();
    }
}
