package com.applymate.service;

import com.applymate.dto.JobApplicationRequest;
import com.applymate.dto.JobApplicationResponse;
import com.applymate.model.ApplicationStatus;
import com.applymate.model.JobApplication;
import com.applymate.model.User;
import com.applymate.repository.JobApplicationRepository;
import com.applymate.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;
    private final UserRepository userRepository;

    public JobApplicationService(
            JobApplicationRepository jobApplicationRepository,
            UserRepository userRepository
    ) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.userRepository = userRepository;
    }

    public List<JobApplicationResponse> findAllForCurrentUser(ApplicationStatus statusFilter) {
        Long userId = currentUserId();
        List<JobApplication> list = statusFilter == null
                ? jobApplicationRepository.findByUserIdOrderByDateAppliedDesc(userId)
                : jobApplicationRepository.findByUserIdAndStatusOrderByDateAppliedDesc(userId, statusFilter);
        return list.stream().map(this::toResponse).toList();
    }

    public JobApplicationResponse findById(Long id) {
        Long userId = currentUserId();
        JobApplication app = jobApplicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        return toResponse(app);
    }

    @Transactional
    public JobApplicationResponse create(JobApplicationRequest request) {
        User user = userRepository.findById(currentUserId())
                .orElseThrow(() -> new IllegalStateException("User not found"));
        JobApplication app = new JobApplication();
        app.setUser(user);
        applyRequest(app, request);
        jobApplicationRepository.save(app);
        return toResponse(app);
    }

    @Transactional
    public JobApplicationResponse update(Long id, JobApplicationRequest request) {
        Long userId = currentUserId();
        JobApplication app = jobApplicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        applyRequest(app, request);
        jobApplicationRepository.save(app);
        return toResponse(app);
    }

    @Transactional
    public void delete(Long id) {
        Long userId = currentUserId();
        JobApplication app = jobApplicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        jobApplicationRepository.delete(app);
    }

    private void applyRequest(JobApplication app, JobApplicationRequest request) {
        app.setJobTitle(request.jobTitle());
        app.setCompanyName(request.companyName());
        app.setLocation(request.location());
        app.setJobPostingUrl(request.jobPostingUrl());
        app.setStatus(request.status());
        app.setDateApplied(request.dateApplied());
        app.setNotes(request.notes());
    }

    private JobApplicationResponse toResponse(JobApplication app) {
        return new JobApplicationResponse(
                app.getId(),
                app.getJobTitle(),
                app.getCompanyName(),
                app.getLocation(),
                app.getJobPostingUrl(),
                app.getStatus(),
                app.getDateApplied(),
                app.getNotes()
        );
    }

    private Long currentUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }
}
