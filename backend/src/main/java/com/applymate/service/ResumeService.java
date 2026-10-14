package com.applymate.service;

import com.applymate.ai.AiService;
import com.applymate.ai.Schemas.ResumeContent;
import com.applymate.ai.Schemas.SkillExtraction;
import com.applymate.dto.ProfileDtos.*;
import com.applymate.error.ApiException;
import com.applymate.error.Versions;
import com.applymate.model.Resume;
import com.applymate.model.SkillProfile;
import com.applymate.repository.ResumeRepository;
import com.applymate.repository.SkillProfileRepository;
import com.applymate.resume.ResumeNormalizer;
import com.applymate.resume.ResumeTextExtractor;
import com.applymate.util.Text;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Resume upload, the AI skill profile and the master resume.
 *
 * <p>Model calls are made outside any transaction: one can take a minute, and holding
 * a pooled connection that long starves every other request.
 */
@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    private final ResumeRepository resumes;
    private final SkillProfileRepository profiles;
    private final ResumeTextExtractor extractor;
    private final AiService ai;
    private final CurrentUser currentUser;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ResumeService(ResumeRepository resumes, SkillProfileRepository profiles, ResumeTextExtractor extractor,
                         AiService ai, CurrentUser currentUser, ObjectMapper mapper, TransactionTemplate tx, Clock clock) {
        this.resumes = resumes;
        this.profiles = profiles;
        this.extractor = extractor;
        this.ai = ai;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.tx = tx;
        this.clock = clock;
    }

    public Optional<Resume> active(Long userId) {
        return resumes.findFirstByUserIdAndActiveTrueOrderByUploadedAtDesc(userId);
    }

    public Resume requireActive(Long userId) {
        return active(userId).orElseThrow(() -> ApiException.unprocessable("Upload your resume on the Profile page first."));
    }

    public Optional<SkillProfile> activeProfile(Long userId) {
        return active(userId).flatMap(r -> profiles.findByResumeId(r.getId()));
    }

    public UploadResponse upload(String fileName, byte[] bytes) {
        Long userId = currentUser.id();
        ResumeTextExtractor.Extracted extracted = extractor.extract(bytes);

        Resume saved = tx.execute(status -> {
            Resume previous = active(userId).orElse(null);
            resumes.deactivateAll(userId);
            Resume resume = new Resume();
            resume.setUserId(userId);
            resume.setFileName(Text.clipOrEmpty(fileName == null ? "resume" : fileName, 255));
            resume.setContentType(extracted.contentType());
            resume.setText(extracted.text());
            // The master resume is the user's own work; a new CV must not discard it.
            if (previous != null) resume.setMasterContent(previous.getMasterContent());
            return resumes.save(resume);
        });

        String warning = null;
        SkillProfile profile = null;
        try {
            profile = extractProfile(userId, saved);
        } catch (ApiException e) {
            // The upload itself succeeded; say why the profile is missing instead of failing it.
            log.info("Skill extraction skipped for resume {}: {}", saved.getId(), e.getMessage());
            warning = "Your resume was saved, but the skill profile could not be generated: " + e.getMessage()
                    + " You can retry from your profile.";
        }
        return new UploadResponse(toResponse(saved), profile == null ? null : toResponse(profile), warning);
    }

    public SkillProfileResponse refreshProfile() {
        Long userId = currentUser.id();
        return toResponse(extractProfile(userId, requireActive(userId)));
    }

    private SkillProfile extractProfile(Long userId, Resume resume) {
        var result = ai.extractSkills(userId, resume.getText());
        SkillExtraction x = result.value();
        return tx.execute(status -> {
            SkillProfile p = profiles.findByResumeId(resume.getId()).orElseGet(SkillProfile::new);
            p.setResumeId(resume.getId());
            p.setSkills(Text.cleanList(x.skills(), 60, 80));
            p.setJobTitles(Text.cleanList(x.jobTitles(), 10, 120));
            p.setDomains(Text.cleanList(x.domains(), 20, 120));
            p.setLocations(Text.cleanList(x.locations(), 10, 120));
            p.setSeniority(Text.clip(x.seniority(), 60));
            p.setYearsExperience(years(x.yearsExperience()));
            p.setSummary(Text.clip(x.summary(), 2000));
            p.setModelUsed(result.model());
            p.setGeneratedAt(Instant.now(clock));
            return profiles.save(p);
        });
    }

    public SkillProfileResponse updateProfile(SkillProfileUpdate update) {
        Long userId = currentUser.id();
        Resume resume = requireActive(userId);
        return tx.execute(status -> {
            SkillProfile p = profiles.findByResumeId(resume.getId()).orElseGet(() -> {
                SkillProfile created = new SkillProfile();
                created.setResumeId(resume.getId());
                created.setModelUsed("manual");
                return created;
            });
            if (update.skills() != null) p.setSkills(Text.cleanList(update.skills(), 60, 80));
            if (update.jobTitles() != null) p.setJobTitles(Text.cleanList(update.jobTitles(), 10, 120));
            if (update.domains() != null) p.setDomains(Text.cleanList(update.domains(), 20, 120));
            if (update.locations() != null) p.setLocations(Text.cleanList(update.locations(), 10, 120));
            if (update.seniority() != null) p.setSeniority(Text.clip(update.seniority(), 60));
            if (update.yearsExperience() != null) p.setYearsExperience(years(update.yearsExperience().doubleValue()));
            if (update.summary() != null) p.setSummary(Text.clip(update.summary(), 2000));
            return toResponse(profiles.save(p));
        });
    }

    public List<ResumeResponse> list() {
        return resumes.findByUserIdOrderByUploadedAtDesc(currentUser.id()).stream().map(this::toResponse).toList();
    }

    // --- Master resume -------------------------------------------------------

    public MasterResumeResponse master() {
        Resume resume = active(currentUser.id()).orElse(null);
        ResumeContent content = masterOf(resume);
        return new MasterResumeResponse(content == null ? ResumeNormalizer.empty() : content, content != null,
                resume == null ? 0 : resume.getVersion());
    }

    public MasterResumeResponse saveMaster(ResumeContent content, Long version) {
        Long userId = currentUser.id();
        ResumeContent normalized = ResumeNormalizer.normalize(content);
        long newVersion = tx.execute(status -> {
            Resume resume = requireActive(userId);
            Versions.check(version, resume.getVersion());
            resume.setMasterContent(mapper.valueToTree(normalized));
            return resumes.saveAndFlush(resume).getVersion();
        });
        return new MasterResumeResponse(normalized, true, newVersion);
    }

    /** Reads the CV into the master layout for the user to check. Saves nothing. */
    public MasterResumeResponse fillMasterFromCv() {
        Long userId = currentUser.id();
        Resume resume = requireActive(userId);
        ResumeContent filled = ResumeNormalizer.normalize(ai.structureResume(userId, resume.getText()).value());
        return new MasterResumeResponse(filled, false, resume.getVersion());
    }

    public ResumeContent masterOf(Resume resume) {
        if (resume == null || resume.getMasterContent() == null || resume.getMasterContent().isNull()) return null;
        try {
            return ResumeNormalizer.normalize(mapper.treeToValue(resume.getMasterContent(), ResumeContent.class));
        } catch (Exception e) {
            log.warn("Unreadable master resume on resume {}", resume.getId());
            return null;
        }
    }

    // --- Mapping -------------------------------------------------------------

    private static BigDecimal years(double value) {
        if (Double.isNaN(value) || value < 0) return null;
        return BigDecimal.valueOf(Math.min(value, 99.9)).setScale(1, RoundingMode.HALF_UP);
    }

    public ResumeResponse toResponse(Resume r) {
        return new ResumeResponse(r.getId(), r.getFileName(), r.getContentType(), r.getText().length(), r.isActive(),
                r.getMasterContent() != null && !r.getMasterContent().isNull(), r.getUploadedAt());
    }

    public SkillProfileResponse toResponse(SkillProfile p) {
        return new SkillProfileResponse(p.getSkills(), p.getJobTitles(), p.getDomains(), p.getLocations(),
                p.getSeniority(), p.getYearsExperience(), p.getSummary(), p.getModelUsed(), p.getGeneratedAt());
    }
}
