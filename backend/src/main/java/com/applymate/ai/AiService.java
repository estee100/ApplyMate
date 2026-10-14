package com.applymate.ai;

import com.applymate.ai.Schemas.*;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

import static com.applymate.ai.Prompts.*;

/**
 * Every AI feature in the app, each one a single structured call. Callers persist
 * the results: recomputation is only ever user-initiated, because the free tier
 * allows a handful of requests per minute.
 */
@Service
public class AiService {

    private final LlmClient llm;
    private final AiQuota quota;

    public AiService(LlmClient llm, AiQuota quota) {
        this.llm = llm;
        this.quota = quota;
    }

    public LlmClient.Result<SkillExtraction> extractSkills(Long userId, String resumeText) {
        return metered(userId, () -> llm.generate("skill-extraction", SKILLS_SYSTEM,
                "Here is the resume:\n\n" + clipResume(resumeText), SkillExtraction.class));
    }

    public LlmClient.Result<MatchAnalysis> analyzeMatch(Long userId, String resumeText, String title, String company,
                                                        String description) {
        String prompt = "CANDIDATE RESUME:\n\n" + clipResume(resumeText) + "\n\n"
                + "Role: " + title + "\nCompany: " + company + "\n\n"
                + untrusted(description, MAX_JOB_CHARS) + "\n\nAssess the candidate's fit for this role.";
        var result = metered(userId, () -> llm.generate("match-analysis", MATCH_SYSTEM, prompt, MatchAnalysis.class));
        MatchAnalysis m = result.value();
        // Clamp regardless of what came back: a poisoned advert must not push the score out of range.
        int clamped = Math.max(0, Math.min(100, m.matchPercentage()));
        return new LlmClient.Result<>(new MatchAnalysis(clamped, m.requirementsMet(), m.requirementsMissing(), m.rationale()),
                result.model());
    }

    public LlmClient.Result<ResumeContent> structureResume(Long userId, String resumeText) {
        return metered(userId, () -> llm.generate("resume-structuring", STRUCTURE_SYSTEM,
                "Here is the resume:\n\n" + clipResume(resumeText), ResumeContent.class));
    }

    public LlmClient.Result<MasterTailoring> tailorMaster(Long userId, String indexedMaster, String title, String company,
                                                          String description, String matchSummary, String instructions) {
        String prompt = "CANDIDATE MASTER RESUME:\n\n" + clipResume(indexedMaster) + "\n\n"
                + jobBlock(title, company, description, matchSummary, instructions)
                + "Tailor the master resume to this job.";
        return metered(userId, () -> llm.generate("resume-tailoring", TAILOR_SYSTEM, prompt, MasterTailoring.class));
    }

    public LlmClient.Result<ResumeContent> generateResume(Long userId, String resumeText, String title, String company,
                                                          String description, String matchSummary, String instructions) {
        String prompt = "CANDIDATE RESUME:\n\n" + clipResume(resumeText) + "\n\n"
                + jobBlock(title, company, description, matchSummary, instructions) + "Produce a tailored resume.";
        return metered(userId, () -> llm.generate("resume-generation", RESUME_SYSTEM, prompt, ResumeContent.class));
    }

    public LlmClient.Result<CoverLetter> coverLetter(Long userId, String resumeText, String title, String company,
                                                     String description, String matchSummary, String instructions) {
        String prompt = "CANDIDATE RESUME:\n\n" + clipResume(resumeText) + "\n\n"
                + jobBlock(title, company, description, matchSummary, instructions) + "Write the cover letter.";
        return metered(userId, () -> llm.generate("cover-letter", COVER_LETTER_SYSTEM, prompt, CoverLetter.class));
    }

    public LlmClient.Result<InterviewPlan> interviewPlan(Long userId, String resumeText, String title, String company,
                                                         String description, String matchSummary) {
        String prompt = "CANDIDATE RESUME:\n\n" + clipResume(resumeText) + "\n\n"
                + jobBlock(title, company, description, matchSummary, null) + "Prepare this candidate for the interview.";
        return metered(userId, () -> llm.generate("interview-prep", INTERVIEW_SYSTEM, prompt, InterviewPlan.class));
    }

    public LlmClient.Result<EmailDraft> emailDraft(Long userId, String purpose, String facts, String advert) {
        String prompt = "PURPOSE: " + purpose + "\n\nFACTS ABOUT THE APPLICATION:\n" + facts + "\n\n"
                + (advert == null || advert.isBlank() ? "" : untrusted(advert, 6000) + "\n\n")
                + "Draft the email.";
        return metered(userId, () -> llm.generate("email-draft", EMAIL_SYSTEM, prompt, EmailDraft.class));
    }

    public LlmClient.Result<JobExtraction> extractJob(Long userId, String pageText, String url) {
        String prompt = "This page was fetched from " + url + "\n\n" + untrusted(pageText, MAX_PAGE_CHARS)
                + "\n\nExtract the job details.";
        return metered(userId, () -> llm.generate("job-extraction", EXTRACT_JOB_SYSTEM, prompt, JobExtraction.class));
    }

    /** Charge the user's quota, call, and refund if the call failed. */
    private <T> LlmClient.Result<T> metered(Long userId, Supplier<LlmClient.Result<T>> call) {
        // Checked first so a disabled feature never touches the user's quota.
        if (!quota.aiEnabled()) throw AiException.disabled();
        quota.acquire(userId);
        try {
            LlmClient.Result<T> result = call.get();
            if (result == null || result.value() == null) throw AiException.unreadable();
            return result;
        } catch (RuntimeException e) {
            quota.refund(userId);
            throw e;
        }
    }
}
