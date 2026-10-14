package com.applymate.resume;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordCoverageTest {

    private final KeywordCoverage coverage = new KeywordCoverage();

    private static final String ADVERT = """
            We're hiring a Backend Developer to build REST APIs in Java and Spring Boot on AWS.
            You'll work with PostgreSQL, Kafka and Kubernetes, and our in-house tool Fluxbase.
            Experience with Fluxbase is a plus. Familiarity with CI/CD and Docker expected.
            Please express interest by applying. Benefits include remote work.""";

    @Test
    void findsDictionaryTermsAndRepeatedUnknownToolNames() {
        var terms = coverage.extractTerms(ADVERT, List.of()).stream().map(List::getFirst).toList();
        assertThat(terms).contains("Java", "Spring Boot", "AWS", "PostgreSQL", "Kafka", "Kubernetes", "CI/CD", "Docker", "REST");
        // Not in the dictionary, but named twice: picked up as a requirement.
        assertThat(terms).contains("Fluxbase");
        // Ordinary words never become keywords.
        assertThat(terms).doesNotContain("Benefits", "Please", "Express");
    }

    @Test
    void aNameStartingOneSentenceStillCountsIfItAlsoAppearsMidSentence() {
        String advert = "Familiarity with Fluxbase, our platform. Fluxbase experience is a plus. Teamwork matters. Teamwork wins.";
        var terms = coverage.extractTerms(advert, List.of()).stream().map(List::getFirst).toList();
        assertThat(terms).contains("Fluxbase");
        // Only ever at the start of a sentence: an ordinary word, not a tool.
        assertThat(terms).doesNotContain("Teamwork");
    }

    @Test
    void reportsFoundAndMissingWithVariants() {
        String resume = "Built RESTful services with Java 21, Spring and Postgres. Shipped with Docker and K8s.";
        var result = coverage.check(ADVERT, List.of(), resume);
        // Variants count: RESTful = REST, Postgres = PostgreSQL, K8s = Kubernetes, Spring = Spring Boot.
        assertThat(result.found()).contains("Java", "REST", "PostgreSQL", "Kubernetes", "Spring Boot", "Docker");
        assertThat(result.missing()).contains("Kafka", "AWS", "CI/CD", "Fluxbase");
        assertThat(result.coveragePct()).isBetween(1, 99);
    }

    @Test
    void wholeWordsOnlyAndShortTermsAreCaseSensitive() {
        assertThat(KeywordCoverage.contains("Expert in JavaScript", "Java")).isFalse();
        assertThat(KeywordCoverage.contains("Strong C++ skills", "C++")).isTrue();
        assertThat(KeywordCoverage.contains("ready to go live", "Go")).isFalse();
        assertThat(KeywordCoverage.contains("services written in Go", "Go")).isTrue();
    }

    @Test
    void aiRequirementHintsAreAddedWhenShort() {
        var terms = coverage.extractTerms("Generic advert.", List.of("Event sourcing", "3+ years of experience building distributed payment systems"))
                .stream().map(List::getFirst).toList();
        assertThat(terms).contains("Event sourcing");
        assertThat(terms).noneMatch(t -> t.startsWith("3+ years"));
    }
}
