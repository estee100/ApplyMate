package com.applymate.controller;

import com.applymate.dto.ApplicationDtos.*;
import com.applymate.model.ApplicationStatus;
import com.applymate.service.JobApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class JobApplicationController {

    private final JobApplicationService service;

    public JobApplicationController(JobApplicationService service) {
        this.service = service;
    }

    @GetMapping("/applications")
    public List<ApplicationResponse> list(@RequestParam(required = false) ApplicationStatus status) {
        return service.list(status);
    }

    @GetMapping("/applications/stats")
    public StatsResponse stats() {
        return service.stats();
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse create(@Valid @RequestBody CreateApplicationRequest request) {
        return service.create(request);
    }

    @GetMapping("/applications/{id}")
    public ApplicationResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PatchMapping("/applications/{id}")
    public ApplicationResponse update(@PathVariable Long id, @Valid @RequestBody UpdateApplicationRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/applications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/applications/{id}/events")
    public List<EventResponse> events(@PathVariable Long id) {
        return service.events(id);
    }

    @GetMapping("/applications/{id}/communications")
    public List<CommunicationResponse> communications(@PathVariable Long id) {
        return service.communications(id);
    }

    @PostMapping("/applications/{id}/communications")
    @ResponseStatus(HttpStatus.CREATED)
    public CommunicationResponse addCommunication(@PathVariable Long id, @Valid @RequestBody CommunicationRequest request) {
        return service.addCommunication(id, request);
    }

    @PatchMapping("/communications/{id}")
    public CommunicationResponse updateCommunication(@PathVariable Long id, @Valid @RequestBody CommunicationRequest request) {
        return service.updateCommunication(id, request);
    }

    @DeleteMapping("/communications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCommunication(@PathVariable Long id) {
        service.deleteCommunication(id);
    }
}
