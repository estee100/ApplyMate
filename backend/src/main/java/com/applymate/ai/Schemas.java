package com.applymate.ai;

import java.util.List;

/**
 * Structured output types. Every model call is constrained to one of these, which is
 * a security control as much as a convenience: a response held to
 * {@code {matchPercentage: int, ...}} cannot be steered into arbitrary prose.
 */
public final class Schemas {

    private Schemas() {}

    public record SkillExtraction(
            @Desc("Concrete technical and professional skills, each a short name such as 'Python' or 'SQL'") List<String> skills,
            @Desc("Role titles this candidate could realistically apply for now, phrased like job boards phrase them") List<String> jobTitles,
            @Desc("Industries or problem domains they have worked in") List<String> domains,
            @Desc("Places they have worked or say they prefer") List<String> locations,
            @Desc("One of: intern, junior, mid, senior, lead") String seniority,
            @Desc("Total years of relevant professional experience") double yearsExperience,
            @Desc("Two or three sentences describing the candidate") String summary
    ) {}

    public record MatchAnalysis(
            @Desc("0-100, how well the candidate fits this role") int matchPercentage,
            @Desc("Requirements from the advert this candidate demonstrably satisfies, as short phrases") List<String> requirementsMet,
            @Desc("Requirements from the advert not evidenced in the resume, as short phrases") List<String> requirementsMissing,
            @Desc("Two or three sentences justifying the score") String rationale
    ) {}

    /** One school, job or project, laid out like a classic one-page resume. */
    public record ResumeEntry(
            @Desc("Position for a job, school for education, name for a project") String title,
            @Desc("Projects only: technologies used. Otherwise empty") String meta,
            @Desc("Dates for a job or project; location for education") String right,
            @Desc("Employer for a job, degree for education. Empty for projects") String subtitle,
            @Desc("Location for a job; dates for education") String subtitleRight,
            List<String> bullets
    ) {}

    public record ResumeSection(
            String heading,
            List<ResumeEntry> entries,
            @Desc("Only for sections that have no entries") List<String> bullets
    ) {}

    /** The master resume and every resume made for a job share this shape. */
    public record ResumeContent(
            String fullName,
            @Desc("One line positioning the candidate, or empty") String headline,
            @Desc("phone | email | linkedin | github, keeping empty parts") String contactLine,
            String summary,
            List<ResumeSection> sections,
            @Desc("Lines such as 'Languages: Python, SQL'") List<String> skills
    ) {}

    public record EntryTailoring(
            @Desc("The [entry N] number from the master resume") int entryIndex,
            @Desc("This entry's bullets, reworded for the job") List<String> bullets
    ) {}

    public record SectionTailoring(
            @Desc("The [section N] number from the master resume") int sectionIndex,
            @Desc("The section's entries, most relevant first") List<EntryTailoring> entries,
            @Desc("Reworded [section bullets], or empty if it has none") List<String> bullets
    ) {}

    /** Edits to the master resume. Facts such as dates and employers are deliberately not fields here. */
    public record MasterTailoring(
            String headline,
            String summary,
            @Desc("Every section, most relevant first") List<SectionTailoring> sections,
            @Desc("The master's skill lines, reordered and trimmed, labels unchanged") List<String> skills
    ) {}

    public record CoverLetter(
            String greeting,
            List<String> paragraphs,
            String closing
    ) {}

    public record PrepFocus(
            @Desc("One thing to prepare, named plainly") String topic,
            @Desc("Why this matters for this particular role") String why,
            @Desc("Concrete things to do before the interview") List<String> actions
    ) {}

    public record PrepQuestion(
            @Desc("A question this interview is likely to ask") String question,
            @Desc("What a strong answer covers, drawing on this candidate's own experience") String howToAnswer
    ) {}

    public record InterviewPlan(
            @Desc("Two sentences on what this interview will focus on") String summary,
            List<PrepFocus> focusAreas,
            List<PrepQuestion> likelyQuestions,
            @Desc("Questions for the candidate to ask the interviewer") List<String> questionsToAsk,
            @Desc("Gaps in the candidate's fit to be ready to answer honestly") List<String> watchOuts
    ) {}

    public record EmailDraft(
            @Desc("A short, specific subject line") String subject,
            @Desc("The email body, plain text, ready to paste; no placeholders like [Name] unless unavoidable") String body
    ) {}

    /** Pulled from a fetched job page when its markup gives nothing better. */
    public record JobExtraction(
            @Desc("The job title, or empty if not found") String title,
            @Desc("The hiring company, or empty if not found") String company,
            @Desc("Location, or empty if not stated") String location,
            @Desc("The full job description as plain text") String description
    ) {}
}
