package com.applymate.controller;

import com.applymate.dto.JobApplicationRequest;
import com.applymate.dto.JobApplicationResponse;
import com.applymate.model.ApplicationStatus;
import com.applymate.service.JobApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/applications")
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;

    public JobApplicationController(JobApplicationService jobApplicationService) {
        this.jobApplicationService = jobApplicationService;
    }

    @GetMapping
    public ResponseEntity<List<JobApplicationResponse>> list(
            @RequestParam(required = false) ApplicationStatus status
    ) {
        return ResponseEntity.ok(jobApplicationService.findAllForCurrentUser(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobApplicationResponse> get(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(jobApplicationService.findById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<JobApplicationResponse> create(@Valid @RequestBody JobApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobApplicationService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<JobApplicationResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody JobApplicationRequest request
    ) {
        try {
            return ResponseEntity.ok(jobApplicationService.update(id, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            jobApplicationService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
