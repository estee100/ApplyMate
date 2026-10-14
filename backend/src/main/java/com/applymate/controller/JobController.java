package com.applymate.controller;

import com.applymate.dto.JobDtos.*;
import com.applymate.model.JobType;
import com.applymate.service.CurrentUser;
import com.applymate.service.JobService;
import com.applymate.service.RateLimiter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private static final String SLOW_DOWN = "You're searching quickly. Wait a moment and try again.";

    private final JobService jobs;
    private final RateLimiter limiter;
    private final CurrentUser currentUser;

    public JobController(JobService jobs, RateLimiter limiter, CurrentUser currentUser) {
        this.jobs = jobs;
        this.limiter = limiter;
        this.currentUser = currentUser;
    }

    @GetMapping("/feed")
    public FeedResponse feed(@RequestParam(required = false) String q,
                             @RequestParam(required = false) String location,
                             @RequestParam(required = false) JobType jobType,
                             @RequestParam(defaultValue = "false") boolean remoteOnly,
                             @RequestParam(required = false) Integer postedWithinDays,
                             @RequestParam(required = false) Integer page) {
        limiter.check("feed:" + currentUser.id(), 30, Duration.ofMinutes(1), SLOW_DOWN);
        return jobs.feed(q, location, jobType, remoteOnly, postedWithinDays, page);
    }

    @GetMapping("/mine")
    public List<JobCard> mine() {
        return jobs.mine();
    }

    @GetMapping("/saved")
    public List<JobCard> saved() {
        return jobs.saved();
    }

    @PostMapping("/paste-link")
    @ResponseStatus(HttpStatus.CREATED)
    public JobDetail pasteLink(@Valid @RequestBody PasteLinkRequest request) {
        limiter.check("paste:" + currentUser.id(), 10, Duration.ofMinutes(1), SLOW_DOWN);
        return jobs.pasteLink(request.url());
    }

    @PostMapping("/paste-text")
    @ResponseStatus(HttpStatus.CREATED)
    public JobDetail pasteText(@Valid @RequestBody PasteTextRequest request) {
        return jobs.pasteText(request);
    }

    @GetMapping("/{id}")
    public JobDetail detail(@PathVariable Long id) {
        return jobs.detail(id);
    }

    @DeleteMapping("/{id}/mine")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMine(@PathVariable Long id) {
        jobs.removeMine(id);
    }

    @PutMapping("/{id}/saved")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@PathVariable Long id) {
        jobs.save(id);
    }

    @DeleteMapping("/{id}/saved")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsave(@PathVariable Long id) {
        jobs.unsave(id);
    }

    @PostMapping("/{id}/match")
    public MatchResponse match(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean refresh) {
        return jobs.match(id, refresh);
    }
}
