package com.applymate.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationRulesTest {

    @Test
    void skillsMatchAsWholeWords() {
        assertThat(RecommendationService.skillPattern("Java").matcher("We use JavaScript").find()).isFalse();
        assertThat(RecommendationService.skillPattern("Java").matcher("Java 21 and Spring").find()).isTrue();
        assertThat(RecommendationService.skillPattern("C++").matcher("Modern C++ required").find()).isTrue();
        assertThat(RecommendationService.skillPattern("C#").matcher("C# and .NET").find()).isTrue();
    }

    @Test
    void shortSkillsAreCaseSensitiveAndAvoidFalseHits() {
        assertThat(RecommendationService.skillPattern("Go").matcher("ready to go live").find()).isFalse();
        assertThat(RecommendationService.skillPattern("Go").matcher("services in Go").find()).isTrue();
        assertThat(RecommendationService.skillPattern("R").matcher("R&D team in the U.S.").find()).isFalse();
        assertThat(RecommendationService.skillPattern("R").matcher("Python, SQL and R.").find()).isTrue();
        assertThat(RecommendationService.skillPattern("C").matcher("C-suite reporting").find()).isFalse();
    }

    @Test
    void titleMentionsCountDoubleAndDescriptionHitsAreCapped() {
        var skills = List.of("Java", "Spring", "SQL", "Docker", "AWS", "Kafka", "React");
        var m = RecommendationService.matchSkills(skills, "Java Developer",
                "Spring, SQL, Docker, AWS, Kafka and React");
        assertThat(m.matched()).hasSize(7);
        assertThat(m.score()).isEqualTo(2.0 + 4); // one title hit, six description hits capped at 4
    }

    @Test
    void broadenDropsLevelAndTermWords() {
        assertThat(RecommendationService.broaden("Software Developer Intern")).isEqualTo("Software Developer");
        assertThat(RecommendationService.broaden("Junior Data Analyst (Summer 2027)")).isEqualTo("Data Analyst");
    }

    @Test
    void relevanceIsMeasuredAgainstARealisticSkillCount() {
        assertThat(RecommendationService.relevance(3, 30)).isEqualTo(30);
        assertThat(RecommendationService.relevance(3, 2)).isEqualTo(100);
    }
}
