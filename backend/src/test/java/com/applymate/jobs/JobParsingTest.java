package com.applymate.jobs;

import com.applymate.config.AppProperties;
import com.applymate.error.ApiException;
import com.applymate.model.JobSource;
import com.applymate.model.JobType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobParsingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost/",
            "http://127.0.0.1/admin",
            "http://169.254.169.254/latest/meta-data/",
            "http://10.0.0.5/",
            "http://192.168.1.1/",
            "http://172.16.0.1/",
            "http://100.64.0.1/",
            "http://0.0.0.0/",
            "http://[::1]/",
            "http://[fd00::1]/",
            "http://[::ffff:127.0.0.1]/",
            "https://example.com:5432/",
            "file:///etc/passwd",
            "gopher://example.com/",
            "http://user:pass@example.com/",
            "not a url"
    })
    void unsafeUrlsAreRefused(String url) {
        assertThatThrownBy(() -> SafeFetcher.validate(url)).isInstanceOf(ApiException.class);
    }

    @Test
    void publicAddressesPass() throws Exception {
        assertThat(SafeFetcher.isPublicAddress(java.net.InetAddress.getByName("93.184.216.34"))).isTrue();
        assertThat(SafeFetcher.isPublicAddress(java.net.InetAddress.getByName("2606:4700::1111"))).isTrue();
    }

    @Test
    void jsonLdJobPostingInsideGraphIsParsed() {
        String description = "<p>We are hiring a backend developer.</p><ul><li>Java and Spring</li><li>PostgreSQL</li></ul>"
                + "<p>" + "You will build and run services. ".repeat(10) + "</p>";
        String html = """
                <html><head><script type="application/ld+json">
                {"@context":"https://schema.org","@graph":[{"@type":"WebPage"},
                 {"@type":"JobPosting","title":"Backend Developer","hiringOrganization":{"name":"Acme"},
                  "jobLocation":[{"address":{"addressLocality":"Edmonton","addressRegion":"AB","addressCountry":"CA"}}],
                  "description":%s}]}
                </script></head><body>Nav</body></html>""".formatted(quote(description));
        var parsed = new JobPageParser(mapper).parse(html);
        assertThat(parsed.title()).isEqualTo("Backend Developer");
        assertThat(parsed.company()).isEqualTo("Acme");
        assertThat(parsed.location()).isEqualTo("Edmonton, AB, CA");
        assertThat(parsed.description()).contains("- Java and Spring").doesNotContain("<li>");
        assertThat(parsed.usable()).isTrue();
    }

    @Test
    void knownContainerIsUsedWithoutJsonLd() {
        String html = "<html><body><h1>Data Analyst</h1><div class=\"job-description\">"
                + "<p>" + "Analyse data and build dashboards. ".repeat(10) + "</p></div></body></html>";
        var parsed = new JobPageParser(mapper).parse(html);
        assertThat(parsed.title()).isEqualTo("Data Analyst");
        assertThat(parsed.description()).startsWith("Analyse data");
    }

    @Test
    void htmlTextDropsScriptsAndKeepsBreaks() {
        String text = HtmlText.toText("<p>One</p><script>alert(1)</script><p>Two &amp; three</p>", 1000);
        assertThat(text).isEqualTo("One\n\nTwo & three");
    }

    @Test
    void adzunaAndHimalayasNormaliseAndUnsafeLinksAreDropped() throws Exception {
        var props = new AppProperties("http://localhost:3000", List.of(),
                new AppProperties.Ai("", List.of("m"), 25, 6),
                new AppProperties.Jobs("id", "key", "ca", 180),
                new AppProperties.Mail(false, "x"));
        JobSources sources = new JobSources(props, mapper);

        var adzuna = sources.normalizeAdzuna(mapper.readTree("""
                {"id":"123","title":"<strong>Java</strong> Developer","company":{"display_name":"Acme"},
                 "location":{"display_name":"Edmonton, Alberta"},"description":"Build APIs",
                 "redirect_url":"javascript:alert(1)","created":"2026-10-01T10:00:00Z",
                 "salary_min":70000,"salary_max":90000,"salary_is_predicted":"1","contract_time":"full_time"}"""));
        assertThat(adzuna.title()).isEqualTo("Java Developer");
        assertThat(adzuna.url()).isNull();
        assertThat(adzuna.salary()).isEqualTo("$70,000 - $90,000 (est.)");
        assertThat(adzuna.jobType()).isEqualTo(JobType.FULL_TIME);
        assertThat(adzuna.postedDate()).isEqualTo(LocalDate.of(2026, 10, 1));

        var himalayas = sources.normalizeHimalayas(mapper.readTree("""
                {"guid":"https://himalayas.app/jobs/1","title":"Remote Python Engineer","companyName":"Beta",
                 "companyLogo":"http://insecure.example/logo.png","applicationLink":"https://himalayas.app/apply/1",
                 "locationRestrictions":["Canada","United States","Mexico","Brazil"],"pubDate":1790000000,
                 "employmentType":"Full Time","description":"<p>Python</p>"}"""));
        assertThat(himalayas.source()).isEqualTo(JobSource.HIMALAYAS);
        assertThat(himalayas.companyLogoUrl()).isNull(); // https only for images
        assertThat(himalayas.location()).isEqualTo("Remote (Canada, United States, Mexico +1 more)");

        var duplicate = new NormalizedJob(JobSource.HIMALAYAS, "x", "java  developer", "ACME", null, null, null,
                null, null, null, LocalDate.of(2026, 10, 5), null);
        // Newest first: the Adzuna job (1 Oct) is newer than the Himalayas one (21 Sep).
        var merged = JobSources.merge(List.of(adzuna), List.of(duplicate, himalayas));
        assertThat(merged).extracting(NormalizedJob::title).containsExactly("Java Developer", "Remote Python Engineer");
    }

    private String quote(String s) {
        try {
            return mapper.writeValueAsString(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
