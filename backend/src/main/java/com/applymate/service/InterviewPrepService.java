package com.applymate.service;

import com.applymate.ai.AiService;
import com.applymate.ai.Schemas.InterviewPlan;
import com.applymate.ai.Schemas.PrepFocus;
import com.applymate.ai.Schemas.PrepQuestion;
import com.applymate.dto.DocumentDtos.InterviewPrepResponse;
import com.applymate.error.ApiException;
import com.applymate.model.ApplicationStatus;
import com.applymate.model.InterviewPrep;
import com.applymate.model.Job;
import com.applymate.model.JobApplication;
import com.applymate.model.Resume;
import com.applymate.repository.InterviewPrepRepository;
import com.applymate.repository.JobMatchRepository;
import com.applymate.resume.ResumeTailoring;
import com.applymate.util.Text;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * An AI plan for one interview. Written once and saved: opening the page reads the
 * saved plan for free, and only an explicit "write it again" spends another request.
 */
@Service
public class InterviewPrepService {

    private final InterviewPrepRepository preps;
    private final JobMatchRepository matches;
    private final JobApplicationService applications;
    private final ResumeService resumes;
    private final AiService ai;
    private final CurrentUser currentUser;
    private final ObjectMapper mapper;
    private final Clock clock;

    public InterviewPrepService(InterviewPrepRepository preps, JobMatchRepository matches,
                                JobApplicationService applications, ResumeService resumes, AiService ai,
                                CurrentUser currentUser, ObjectMapper mapper, Clock clock) {
        this.preps = preps;
        this.matches = matches;
        this.applications = applications;
        this.resumes = resumes;
        this.ai = ai;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.clock = clock;
    }

    public InterviewPrepResponse get(Long applicationId) {
        applications.owned(applicationId);
        return preps.findByApplicationId(applicationId).map(InterviewPrepService::toResponse).orElse(null);
    }

    public InterviewPrepResponse generate(Long applicationId, boolean refresh) {
        Long userId = currentUser.id();
        JobApplication app = applications.owned(applicationId);
        if (app.getStatus() != ApplicationStatus.INTERVIEWING) {
            throw ApiException.unprocessable("Interview prep is available once an application reaches the interview stage.");
        }
        Job job = app.getJob();
        if (job == null || !JobService.analyzable(job)) {
            // Refused before any model call: hand-logged jobs have no advert to work from.
            throw ApiException.unprocessable("Interview prep needs the job advert. Paste the job into ApplyMate and track it from there.");
        }
        var existing = preps.findByApplicationId(applicationId);
        if (existing.isPresent() && !refresh) return toResponse(existing.get());

        Resume resume = resumes.requireActive(userId);
        var master = resumes.masterOf(resume);
        String evidence = master != null ? ResumeTailoring.indexedText(master) : resume.getText();
        String matchSummary = matches.findByUserIdAndJobIdAndResumeId(userId, job.getId(), resume.getId())
                .map(m -> "Strengths: " + String.join("; ", m.getRequirementsMet())
                        + "\nGaps: " + String.join("; ", m.getRequirementsMissing()))
                .orElse("");

        var result = ai.interviewPlan(userId, evidence, job.getTitle(), job.getCompany(), job.getDescription(), matchSummary);
        InterviewPrep prep = preps.findByApplicationId(applicationId).orElseGet(InterviewPrep::new);
        prep.setApplicationId(applicationId);
        prep.setContent(mapper.valueToTree(normalize(result.value())));
        prep.setModelUsed(result.model());
        prep.setGeneratedAt(Instant.now(clock));
        return toResponse(preps.save(prep));
    }

    /** Clipped rather than rejected: the request is already spent. */
    static InterviewPlan normalize(InterviewPlan plan) {
        List<PrepFocus> focus = safe(plan.focusAreas()).stream().limit(8)
                .filter(f -> f != null && !Text.isBlank(f.topic()))
                .map(f -> new PrepFocus(Text.clipOrEmpty(f.topic(), 200), Text.clipOrEmpty(f.why(), 1000),
                        Text.cleanList(f.actions(), 8, 500)))
                .toList();
        List<PrepQuestion> questions = safe(plan.likelyQuestions()).stream().limit(12)
                .filter(q -> q != null && !Text.isBlank(q.question()))
                .map(q -> new PrepQuestion(Text.clipOrEmpty(q.question(), 500), Text.clipOrEmpty(q.howToAnswer(), 1500)))
                .toList();
        return new InterviewPlan(Text.clipOrEmpty(plan.summary(), 1000), focus, questions,
                Text.cleanList(plan.questionsToAsk(), 10, 300), Text.cleanList(plan.watchOuts(), 10, 500));
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static InterviewPrepResponse toResponse(InterviewPrep p) {
        return new InterviewPrepResponse(p.getContent(), p.getModelUsed(), p.getGeneratedAt());
    }
}
