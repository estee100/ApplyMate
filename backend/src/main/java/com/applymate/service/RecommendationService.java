package com.applymate.service;

import com.applymate.dto.JobDtos.JobCard;
import com.applymate.jobs.JobFeed;
import com.applymate.model.Job;
import com.applymate.model.JobSource;
import com.applymate.model.SkillProfile;
import com.applymate.model.User;
import com.applymate.repository.JobApplicationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Jobs that fit the skills on the active resume. Searches the same free sources as the
 * feed (through its shared cache, so repeat visits are free) for the resume's top job
 * titles, then ranks by how many of the candidate's skills each job mentions. The
 * ranking is instant and free; it is not an AI score and is never presented as one.
 */
@Service
public class RecommendationService {

    static final int MAX_TITLES = 2;
    static final int MAX_RESULTS = 20;
    static final int MAX_SKILLS = 15;
    /** Description-only mentions stop counting past this, or long remote adverts bury short local ones. */
    static final int DESCRIPTION_MATCH_CAP = 4;
    static final double LOCAL_BONUS = 2.0;
    static final double STRONG_TITLE_MATCH = 2.0;
    static final int MIN_LOCAL_RESULTS = 5;

    // A skill must stand alone: "Java" must not match "JavaScript", "C" must not match every c.
    private static final String BEFORE = "(?<![A-Za-z0-9+#])";
    private static final String AFTER = "(?![A-Za-z0-9+#])";
    // One-letter skills must not be part of R&D, C-suite, U.S., A/B; a sentence-ending full stop is fine.
    private static final String SINGLE_BEFORE = "(?<![A-Za-z0-9+#&./'-])";
    private static final String SINGLE_AFTER = "(?![A-Za-z0-9+#&/'-]|\\.\\w)";
    private static final Pattern LEVEL_WORDS = Pattern.compile(
            "\\b(intern(ship)?|co-?op|junior|jr|senior|sr|student|new grad(uate)?|entry[ -]level|lead|principal|staff"
                    + "|trainee|i{1,3}|iv|summer|fall|winter|spring|20\\d\\d)\\b", Pattern.CASE_INSENSITIVE);

    private final JobFeed feed;
    private final JobService jobs;
    private final ResumeService resumes;
    private final JobApplicationRepository applications;
    private final CurrentUser currentUser;

    public RecommendationService(JobFeed feed, JobService jobs, ResumeService resumes,
                                 JobApplicationRepository applications, CurrentUser currentUser) {
        this.feed = feed;
        this.jobs = jobs;
        this.resumes = resumes;
        this.applications = applications;
        this.currentUser = currentUser;
    }

    public record Recommendation(JobCard job, List<String> matchedSkills, int relevance) {}

    public record Recommendations(List<Recommendation> items, List<String> searchedTitles, String location,
                                  boolean includeRemote, String notice) {}

    public Recommendations recommend() {
        User user = currentUser.get();
        Optional<SkillProfile> profileOpt = resumes.activeProfile(user.getId());
        if (profileOpt.isEmpty()) {
            return new Recommendations(List.of(), List.of(), null, user.isIncludeRemote(),
                    "Upload your resume on the Profile page to get job recommendations based on your skills.");
        }
        SkillProfile profile = profileOpt.get();
        List<String> skills = profile.getSkills().stream().filter(s -> !s.isBlank()).limit(MAX_SKILLS).toList();
        List<String> titles = new ArrayList<>(profile.getJobTitles().stream().filter(t -> !t.isBlank()).limit(MAX_TITLES).toList());
        if (titles.isEmpty() && !skills.isEmpty()) titles.add(String.join(" ", skills.subList(0, Math.min(2, skills.size()))));
        String location = user.getPreferredLocation() != null && !user.getPreferredLocation().isBlank()
                ? user.getPreferredLocation()
                : profile.getLocations().stream().filter(l -> !l.isBlank()).findFirst().orElse(null);
        if (titles.isEmpty()) {
            return new Recommendations(List.of(), List.of(), location, user.isIncludeRemote(),
                    "Add a job title or a few skills to your skill profile on the Profile page to get recommendations.");
        }

        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        Set<String> searched = new HashSet<>();
        String notice = null;
        for (String title : titles) {
            searched.add(title.toLowerCase(Locale.ROOT));
            JobFeed.Result found = feed.search(JobFeed.Query.of(title, location, null, false, null, 1));
            ids.addAll(found.jobIds());
            if (notice == null) notice = found.notice();
            // Local boards rarely list "Software Developer Intern": retry the bare role when few local jobs came back.
            long local = jobs.load(found.jobIds()).stream().filter(j -> j.getSource() == JobSource.ADZUNA).count();
            String broader = broaden(title);
            if (location != null && local < MIN_LOCAL_RESULTS && !broader.isEmpty() && searched.add(broader.toLowerCase(Locale.ROOT))) {
                ids.addAll(feed.search(JobFeed.Query.of(broader, location, null, false, null, 1)).jobIds());
            }
        }

        Long userId = user.getId();
        List<Job> candidates = jobs.load(new ArrayList<>(ids));
        Set<Long> applied = new HashSet<>();
        if (!candidates.isEmpty()) {
            applications.findForUserAndJobs(userId, candidates.stream().map(Job::getId).toList())
                    .forEach(a -> applied.add(a.getJob().getId()));
        }

        record Ranked(Job job, List<String> matched, double score) {}
        List<Ranked> ranked = new ArrayList<>();
        for (Job job : candidates) {
            if (applied.contains(job.getId())) continue;
            if (!user.isIncludeRemote() && job.getSource() == JobSource.HIMALAYAS) continue;
            SkillMatch m = matchSkills(skills, job.getTitle(), job.getDescription());
            double bonus = titleBonus(job.getTitle(), titles);
            // Short adverts often list no skills, so a job plainly matching a resume title stays in.
            if (m.matched().isEmpty() && bonus < STRONG_TITLE_MATCH) continue;
            double local = location != null && job.getSource() == JobSource.ADZUNA ? LOCAL_BONUS : 0;
            ranked.add(new Ranked(job, m.matched(), m.score() + bonus + local));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed()
                .thenComparing(r -> r.job().getPostedDate(), Comparator.nullsLast(Comparator.<LocalDate>reverseOrder())));
        List<Ranked> top = ranked.stream().limit(MAX_RESULTS).toList();

        List<JobCard> cards = jobs.cards(userId, top.stream().map(Ranked::job).toList());
        List<Recommendation> items = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            items.add(new Recommendation(cards.get(i), top.get(i).matched(), relevance(top.get(i).matched().size(), skills.size())));
        }
        return new Recommendations(items, titles, location, user.isIncludeRemote(), notice);
    }

    record SkillMatch(List<String> matched, double score) {}

    /** The skills a job mentions; a title mention counts double, description-only mentions are capped. */
    static SkillMatch matchSkills(List<String> skills, String title, String description) {
        List<String> matched = new ArrayList<>();
        int titleHits = 0, descriptionHits = 0;
        for (String skill : skills) {
            Pattern p = skillPattern(skill.strip());
            if (p.matcher(title == null ? "" : title).find()) {
                matched.add(skill);
                titleHits++;
            } else if (p.matcher(description == null ? "" : description).find()) {
                matched.add(skill);
                descriptionHits++;
            }
        }
        return new SkillMatch(matched, 2.0 * titleHits + Math.min(descriptionHits, DESCRIPTION_MATCH_CAP));
    }

    static Pattern skillPattern(String skill) {
        if (skill.length() == 1) return Pattern.compile(SINGLE_BEFORE + Pattern.quote(skill) + SINGLE_AFTER);
        // Two-letter skills such as Go match only in exact case, or "go" would match half of every advert.
        int flags = skill.length() <= 2 ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        return Pattern.compile(BEFORE + Pattern.quote(skill) + AFTER, flags);
    }

    /** 0-100: the share of a realistic number of skills (3 to 10) a job mentions. */
    static int relevance(int matched, int skillCount) {
        int expected = Math.max(3, Math.min(skillCount, 10));
        return Math.min(100, (int) Math.round(100.0 * matched / expected));
    }

    /** "Software Developer Intern" becomes "Software Developer". */
    static String broaden(String title) {
        String without = LEVEL_WORDS.matcher(title).replaceAll(" ");
        return String.join(" ", without.replaceAll("[^A-Za-z0-9+#./ ]", " ").trim().split("\\s+")).trim();
    }

    /** Up to 3 points for sharing words with a job title from the resume. */
    static double titleBonus(String jobTitle, List<String> targets) {
        Set<String> words = words(jobTitle);
        double best = 0;
        for (String target : targets) {
            Set<String> wanted = new HashSet<>();
            for (String w : words(target)) if (w.length() > 2) wanted.add(w);
            if (wanted.isEmpty()) continue;
            long shared = wanted.stream().filter(words::contains).count();
            best = Math.max(best, 3.0 * shared / wanted.size());
        }
        return best;
    }

    private static Set<String> words(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) return out;
        for (String w : text.toLowerCase(Locale.ROOT).split("[^a-z]+")) if (!w.isEmpty()) out.add(w);
        return out;
    }
}
