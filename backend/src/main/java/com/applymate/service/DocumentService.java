package com.applymate.service;

import com.applymate.ai.AiService;
import com.applymate.ai.Schemas.CoverLetter;
import com.applymate.ai.Schemas.ResumeContent;
import com.applymate.dto.DocumentDtos.*;
import com.applymate.error.ApiException;
import com.applymate.error.Versions;
import com.applymate.model.*;
import com.applymate.repository.GeneratedDocumentRepository;
import com.applymate.repository.JobMatchRepository;
import com.applymate.resume.KeywordCoverage;
import com.applymate.resume.ResumeNormalizer;
import com.applymate.resume.ResumeTailoring;
import com.applymate.util.Text;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Resumes and cover letters for one job. Generated documents are drafts the user
 * reviews and edits before export: no AI output ever leaves the app on its own.
 * That review step is the backstop against a poisoned advert steering a document.
 */
@Service
public class DocumentService {

    private final GeneratedDocumentRepository documents;
    private final JobMatchRepository matches;
    private final JobService jobs;
    private final ResumeService resumes;
    private final AiService ai;
    private final CurrentUser currentUser;
    private final ObjectMapper mapper;
    private final KeywordCoverage keywordCoverage;

    public DocumentService(GeneratedDocumentRepository documents, JobMatchRepository matches, JobService jobs,
                           ResumeService resumes, AiService ai, CurrentUser currentUser, ObjectMapper mapper,
                           KeywordCoverage keywordCoverage) {
        this.keywordCoverage = keywordCoverage;
        this.documents = documents;
        this.matches = matches;
        this.jobs = jobs;
        this.resumes = resumes;
        this.ai = ai;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    /** An exact copy of the master resume. No AI, no quota, works for any job. */
    public DocumentResponse fromMaster(Long jobId) {
        Long userId = currentUser.id();
        Job job = jobs.visible(jobId, userId);
        Resume resume = resumes.requireActive(userId);
        ResumeContent master = resumes.masterOf(resume);
        if (master == null) throw ApiException.unprocessable("Save your master resume on the Profile page first.");
        return save(userId, job, resume, DocumentKind.RESUME, mapper.valueToTree(master), ResumeTailoring.MASTER_COPY);
    }

    /**
     * A resume tailored with AI. With a saved master, the model returns edits applied to
     * a copy of it, so facts cannot change; without one, it is written from the CV text.
     */
    public DocumentResponse tailoredResume(Long jobId, String instructions) {
        Long userId = currentUser.id();
        Job job = requireAnalyzable(jobId, userId);
        Resume resume = resumes.requireActive(userId);
        ResumeContent master = resumes.masterOf(resume);
        String matchSummary = matchSummary(userId, jobId, resume.getId());

        ResumeContent content;
        String model;
        if (master != null && !master.sections().isEmpty()) {
            var result = ai.tailorMaster(userId, ResumeTailoring.indexedText(master), job.getTitle(), job.getCompany(),
                    job.getDescription(), matchSummary, instructions);
            content = ResumeTailoring.apply(master, result.value());
            model = result.model();
        } else {
            var result = ai.generateResume(userId, resume.getText(), job.getTitle(), job.getCompany(),
                    job.getDescription(), matchSummary, instructions);
            content = ResumeNormalizer.normalize(result.value());
            model = result.model();
        }
        return save(userId, job, resume, DocumentKind.RESUME, mapper.valueToTree(content), model);
    }

    public DocumentResponse coverLetter(Long jobId, String instructions) {
        Long userId = currentUser.id();
        Job job = requireAnalyzable(jobId, userId);
        Resume resume = resumes.requireActive(userId);
        ResumeContent master = resumes.masterOf(resume);
        // The master is the user's checked version of their CV, so prefer it as evidence.
        String evidence = master != null ? ResumeTailoring.indexedText(master) : resume.getText();
        var result = ai.coverLetter(userId, evidence, job.getTitle(), job.getCompany(), job.getDescription(),
                matchSummary(userId, jobId, resume.getId()), instructions);
        return save(userId, job, resume, DocumentKind.COVER_LETTER, mapper.valueToTree(normalize(result.value())), result.model());
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(Long id) {
        return toResponse(owned(id));
    }

    @Transactional
    public DocumentResponse saveEdit(Long id, JsonNode content, Long version) {
        GeneratedDocument doc = owned(id);
        Versions.check(version, doc.getVersion());
        doc.setEditedContent(validated(doc.getKind(), content));
        return toResponse(documents.saveAndFlush(doc));
    }

    @Transactional
    public DocumentResponse setTemplate(Long id, ResumeTemplate template, Long version) {
        GeneratedDocument doc = owned(id);
        Versions.check(version, doc.getVersion());
        doc.setTemplate(template);
        return toResponse(documents.saveAndFlush(doc));
    }

    /**
     * Which of the advert's keywords the document (as currently edited) contains.
     * Deterministic and free, so it can update as the user types.
     */
    @Transactional(readOnly = true)
    public KeywordCoverage.Coverage keywords(Long id) {
        GeneratedDocument doc = owned(id);
        Job job = doc.getJob();
        List<String> hints = doc.getResumeId() == null ? List.of()
                : matches.findByUserIdAndJobIdAndResumeId(doc.getUserId(), job.getId(), doc.getResumeId())
                        .map(m -> m.getRequirementsMet().size() + m.getRequirementsMissing().size() > 0
                                ? java.util.stream.Stream.concat(m.getRequirementsMet().stream(), m.getRequirementsMissing().stream()).toList()
                                : List.<String>of())
                        .orElse(List.of());
        return keywordCoverage.check(job.getDescription(), hints, flatten(doc.currentContent()));
    }

    /** Every string in a document, joined: what an ATS would read from it. */
    static String flatten(JsonNode node) {
        StringBuilder sb = new StringBuilder();
        collect(node, sb);
        return sb.toString();
    }

    private static void collect(JsonNode node, StringBuilder sb) {
        if (node == null) return;
        if (node.isTextual()) sb.append(node.asText()).append('\n');
        else node.forEach(child -> collect(child, sb));
    }

    @Transactional
    public DocumentResponse reset(Long id) {
        GeneratedDocument doc = owned(id);
        doc.setEditedContent(null);
        return toResponse(documents.saveAndFlush(doc));
    }

    @Transactional
    public void delete(Long id) {
        documents.delete(owned(id));
    }

    // --- Helpers -------------------------------------------------------------

    private Job requireAnalyzable(Long jobId, Long userId) {
        Job job = jobs.visible(jobId, userId);
        if (!JobService.analyzable(job)) {
            throw ApiException.unprocessable("This job has no description to work from. Start from your master resume instead.");
        }
        return job;
    }

    private String matchSummary(Long userId, Long jobId, Long resumeId) {
        return matches.findByUserIdAndJobIdAndResumeId(userId, jobId, resumeId)
                .map(m -> "Strengths: " + String.join("; ", m.getRequirementsMet())
                        + "\nGaps: " + String.join("; ", m.getRequirementsMissing()))
                .orElse("");
    }

    // Called from methods that made a model call, so no surrounding transaction; the
    // repository save is its own short one.
    private DocumentResponse save(Long userId, Job job, Resume resume, DocumentKind kind, JsonNode content, String model) {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setUserId(userId);
        doc.setJob(job);
        doc.setResumeId(resume.getId());
        doc.setKind(kind);
        doc.setContent(content);
        doc.setModelUsed(model);
        return toResponse(documents.save(doc));
    }

    /** User edits are held to the same shape and limits as generated content. */
    private JsonNode validated(DocumentKind kind, JsonNode content) {
        try {
            return switch (kind) {
                case RESUME -> mapper.valueToTree(ResumeNormalizer.normalize(mapper.treeToValue(content, ResumeContent.class)));
                case COVER_LETTER -> mapper.valueToTree(normalize(mapper.treeToValue(content, CoverLetter.class)));
            };
        } catch (Exception e) {
            throw ApiException.badRequest("That document is not in the expected format.");
        }
    }

    static CoverLetter normalize(CoverLetter letter) {
        if (letter == null) return new CoverLetter("", List.of(), "");
        List<String> paragraphs = new ArrayList<>();
        for (String p : letter.paragraphs() == null ? List.<String>of() : letter.paragraphs()) {
            String clipped = Text.clip(p, 3000);
            if (clipped != null && paragraphs.size() < 8) paragraphs.add(clipped);
        }
        return new CoverLetter(Text.clipOrEmpty(letter.greeting(), 200), paragraphs, Text.clipOrEmpty(letter.closing(), 200));
    }

    private GeneratedDocument owned(Long id) {
        return documents.findForUser(id, currentUser.id()).orElseThrow(() -> ApiException.notFound("Document"));
    }

    private DocumentResponse toResponse(GeneratedDocument d) {
        Job job = d.getJob();
        return new DocumentResponse(d.getId(), d.getKind(), job.getId(), job.getTitle(), job.getCompany(),
                d.currentContent(), d.getContent(), d.getEditedContent() != null, d.getModelUsed(),
                currentUser.get().getName(), d.getCreatedAt(), d.getUpdatedAt(), d.getVersion(), d.getTemplate());
    }
}
