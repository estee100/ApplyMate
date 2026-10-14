package com.applymate.resume;

import com.applymate.ai.Schemas.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeTailoringTest {

    private final ResumeContent master = new ResumeContent("Jordan Lee", "", "jordan@example.com", "",
            List.of(
                    new ResumeSection("Education", List.of(
                            new ResumeEntry("University of Alberta", "", "Edmonton", "BSc CS", "2021-2026", List.of())), List.of()),
                    new ResumeSection("Experience", List.of(
                            new ResumeEntry("Developer Intern", "", "2025", "Acme", "Edmonton", List.of("Built APIs", "Wrote tests")),
                            new ResumeEntry("Tutor", "", "2024", "UofA", "Edmonton", List.of("Taught Java"))), List.of())),
            List.of("Languages: Java, Python, SQL", "Tools: Docker, Git"));

    @Test
    void editsReorderAndRewordButNeverChangeFacts() {
        var edits = new MasterTailoring("Ignored headline", "Ignored summary",
                List.of(new SectionTailoring(1, List.of(
                        new EntryTailoring(1, List.of("Taught Java to 40 students")),
                        // The model tries to rename the employer by sending more bullets; facts are not fields.
                        new EntryTailoring(0, List.of("Designed REST APIs", "Tested them", "Invented a third bullet"))),
                        List.of())),
                List.of("Languages: SQL, Java, Rust", "Made Up: Kubernetes"));

        ResumeContent out = ResumeTailoring.apply(master, edits);

        // Experience moved first; Education kept after it rather than dropped.
        assertThat(out.sections()).extracting(ResumeSection::heading).containsExactly("Experience", "Education");
        var experience = out.sections().getFirst().entries();
        assertThat(experience).extracting(ResumeEntry::title).containsExactly("Tutor", "Developer Intern");
        assertThat(experience.get(1).subtitle()).isEqualTo("Acme");
        // Never more bullets than the master entry had.
        assertThat(experience.get(1).bullets()).containsExactly("Designed REST APIs", "Tested them");
        // No headline or summary in the master, so none added.
        assertThat(out.headline()).isEmpty();
        assertThat(out.summary()).isEmpty();
        // Rust and the invented label are dropped; untouched lines are kept.
        assertThat(out.skills()).containsExactly("Languages: SQL, Java", "Tools: Docker, Git");
        assertThat(out.fullName()).isEqualTo("Jordan Lee");
    }

    @Test
    void unknownAndRepeatedIndexesAreIgnored() {
        var edits = new MasterTailoring("", "",
                List.of(new SectionTailoring(7, List.of(), List.of()),
                        new SectionTailoring(0, List.of(), List.of()),
                        new SectionTailoring(0, List.of(), List.of()),
                        new SectionTailoring(-1, List.of(), List.of())),
                List.of());
        ResumeContent out = ResumeTailoring.apply(master, edits);
        assertThat(out.sections()).hasSize(2);
        assertThat(out.skills()).isEqualTo(master.skills());
    }

    @Test
    void indexedTextNumbersSectionsAndEntries() {
        String text = ResumeTailoring.indexedText(master);
        assertThat(text).contains("[section 1] Experience").contains("[entry 0] Developer Intern | Acme | 2025 | Edmonton")
                .contains("    - Built APIs");
    }
}
