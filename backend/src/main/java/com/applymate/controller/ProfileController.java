package com.applymate.controller;

import com.applymate.ai.Schemas.ResumeContent;
import com.applymate.dto.ProfileDtos.*;
import com.applymate.error.ApiException;
import com.applymate.service.ProfileService;
import com.applymate.service.ResumeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
public class ProfileController {

    private final ProfileService profileService;
    private final ResumeService resumeService;

    public ProfileController(ProfileService profileService, ResumeService resumeService) {
        this.profileService = profileService;
        this.resumeService = resumeService;
    }

    @GetMapping("/profile")
    public ProfileResponse profile() {
        return profileService.get();
    }

    @PatchMapping("/profile")
    public ProfileResponse updateProfile(@Valid @RequestBody ProfileUpdate update) {
        return profileService.update(update);
    }

    @GetMapping("/resumes")
    public List<ResumeResponse> resumes() {
        return resumeService.list();
    }

    @PostMapping(value = "/resumes", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse upload(@RequestParam("file") MultipartFile file) {
        try {
            return resumeService.upload(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw ApiException.badRequest("The upload could not be read.");
        }
    }

    @PutMapping("/resumes/active/skill-profile")
    public SkillProfileResponse updateSkillProfile(@Valid @RequestBody SkillProfileUpdate update) {
        return resumeService.updateProfile(update);
    }

    @PostMapping("/resumes/active/skill-profile/refresh")
    public SkillProfileResponse refreshSkillProfile() {
        return resumeService.refreshProfile();
    }

    @GetMapping("/resumes/active/master")
    public MasterResumeResponse master() {
        return resumeService.master();
    }

    @PutMapping("/resumes/active/master")
    public MasterResumeResponse saveMaster(@RequestBody ResumeContent content) {
        return resumeService.saveMaster(content);
    }

    @PostMapping("/resumes/active/master/fill")
    public MasterResumeResponse fillMaster() {
        return resumeService.fillMasterFromCv();
    }
}
