package com.applymate.jobs;

import com.applymate.config.AppProperties;
import com.applymate.model.JobSource;
import com.applymate.model.JobType;
import com.applymate.util.Text;
import com.applymate.util.Urls;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The two free job sources. Pure HTTP and normalisation: no database access.
 *
 * <ul>
 *   <li><b>Adzuna</b>: free developer key; allows publishing its listings. Returns
 *       truncated description snippets.</li>
 *   <li><b>Himalayas</b>: free, no key, remote jobs only. Its terms ask for a link back
 *       to the listing and to be named as the source, which the Apply button does.</li>
 * </ul>
 * Everything returned is untrusted. Error messages are written here and never taken
 * from the underlying exception, because Adzuna's request URL carries the API key.
 */
@Component
public class JobSources {

    private static final Logger log = LoggerFactory.getLogger(JobSources.class);
    public static final int PAGE_SIZE = 20;
    public static final int MAX_DESCRIPTION = 20_000;
    private static final String USER_AGENT = "ApplyMate/1.0 (job search for individual users)";

    private final AppProperties.Jobs config;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public JobSources(AppProperties properties, ObjectMapper mapper) {
        this.config = properties.jobs();
        this.mapper = mapper;
    }

    public static class SourceException extends RuntimeException {
        public SourceException(String message) {
            super(message);
        }
    }

    public record Query(String q, String location, JobType jobType, Integer postedWithinDays, int page) {}

    public boolean adzunaEnabled() {
        return config.adzunaEnabled();
    }

    // --- Adzuna ----------------------------------------------------------------

    public List<NormalizedJob> searchAdzuna(Query query) {
        if (!config.adzunaEnabled()) return List.of();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", config.adzunaAppId());
        params.put("app_key", config.adzunaAppKey());
        params.put("results_per_page", String.valueOf(PAGE_SIZE));
        params.put("sort_by", "date");
        params.put("content-type", "application/json");
        if (!Text.isBlank(query.q())) params.put("what", query.q());
        if (!Text.isBlank(query.location())) params.put("where", query.location());
        if (query.postedWithinDays() != null) params.put("max_days_old", String.valueOf(query.postedWithinDays()));
        if (query.jobType() == JobType.FULL_TIME) params.put("full_time", "1");
        else if (query.jobType() == JobType.PART_TIME) params.put("part_time", "1");
        else if (query.jobType() == JobType.CONTRACT) params.put("contract", "1");

        String url = "https://api.adzuna.com/v1/api/jobs/" + config.country() + "/search/" + Math.max(1, query.page());
        JsonNode data = getJson(url, params, "Adzuna");
        List<NormalizedJob> out = new ArrayList<>();
        for (JsonNode item : data.path("results")) {
            NormalizedJob job = normalizeAdzuna(item);
            if (job != null) out.add(job);
        }
        return out;
    }

    NormalizedJob normalizeAdzuna(JsonNode item) {
        String id = item.path("id").asText(null);
        String title = plain(item.path("title").asText(null), 500);
        String company = plain(item.path("company").path("display_name").asText(null), 255);
        if (id == null || title == null || company == null) return null;
        String url = Urls.safeHttp(item.path("redirect_url").asText(null));
        return new NormalizedJob(
                JobSource.ADZUNA,
                Text.clip(id, 768),
                title,
                company,
                null,
                plain(item.path("location").path("display_name").asText(null), 255),
                adzunaSalary(item),
                url,
                "Adzuna",
                adzunaJobType(item),
                date(item.path("created").asText(null)),
                HtmlText.toText(item.path("description").asText(null), MAX_DESCRIPTION));
    }

    private String adzunaSalary(JsonNode item) {
        Long lo = item.hasNonNull("salary_min") ? item.get("salary_min").asLong() : null;
        Long hi = item.hasNonNull("salary_max") ? item.get("salary_max").asLong() : null;
        if (lo == null && hi == null) return null;
        String symbol = switch (config.country()) {
            case "gb" -> "£";
            case "in" -> "₹";
            case "de", "fr", "nl", "it", "es", "at", "be" -> "€";
            default -> "$";
        };
        String text = (lo != null && hi != null && !lo.equals(hi))
                ? symbol + money(lo) + " - " + symbol + money(hi)
                : symbol + money(lo != null ? lo : hi);
        // Adzuna estimates some salaries; label those rather than pass a guess off as fact.
        String predicted = item.path("salary_is_predicted").asText("0");
        if (predicted.equals("1") || predicted.equalsIgnoreCase("true")) text += " (est.)";
        return Text.clip(text, 120);
    }

    private static JobType adzunaJobType(JsonNode item) {
        String time = item.path("contract_time").asText("").toLowerCase(Locale.ROOT);
        String type = item.path("contract_type").asText("").toLowerCase(Locale.ROOT);
        if (time.equals("part_time")) return JobType.PART_TIME;
        if (type.equals("contract")) return JobType.CONTRACT;
        if (time.equals("full_time")) return JobType.FULL_TIME;
        return null;
    }

    // --- Himalayas -------------------------------------------------------------

    private static final Map<String, String> COUNTRY_NAMES = Map.of(
            "ca", "Canada", "us", "United States", "gb", "United Kingdom", "au", "Australia",
            "nz", "New Zealand", "in", "India", "de", "Germany", "fr", "France", "nl", "Netherlands");

    public List<NormalizedJob> searchHimalayas(Query query) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("sort", "recent");
        params.put("page", String.valueOf(Math.max(1, query.page())));
        if (!Text.isBlank(query.q())) params.put("q", query.q());
        String country = COUNTRY_NAMES.get(config.country());
        if (country != null) params.put("country", country);
        if (query.jobType() == JobType.FULL_TIME) params.put("employment_type", "Full Time");
        else if (query.jobType() == JobType.PART_TIME) params.put("employment_type", "Part Time");
        else if (query.jobType() == JobType.CONTRACT) params.put("employment_type", "Contractor");
        else if (query.jobType() == JobType.INTERNSHIP) params.put("employment_type", "Intern");

        JsonNode data = getJson("https://himalayas.app/jobs/api/search", params, "Himalayas");
        List<NormalizedJob> out = new ArrayList<>();
        LocalDate cutoff = query.postedWithinDays() == null ? null : LocalDate.now(ZoneOffset.UTC).minusDays(query.postedWithinDays());
        for (JsonNode item : data.path("jobs")) {
            NormalizedJob job = normalizeHimalayas(item);
            // Himalayas has no date filter, so apply it here.
            if (job != null && (cutoff == null || (job.postedDate() != null && !job.postedDate().isBefore(cutoff)))) {
                out.add(job);
            }
        }
        return out;
    }

    NormalizedJob normalizeHimalayas(JsonNode item) {
        String id = item.path("guid").asText(null);
        if (id == null) id = item.path("applicationLink").asText(null);
        String title = plain(item.path("title").asText(null), 500);
        String company = plain(item.path("companyName").asText(null), 255);
        if (id == null || title == null || company == null) return null;
        String url = Urls.safeHttp(item.path("applicationLink").asText(null));
        if (url == null) url = Urls.safeHttp(item.path("guid").asText(null));
        String description = HtmlText.toText(item.path("description").asText(null), MAX_DESCRIPTION);
        if (description == null) description = HtmlText.toText(item.path("excerpt").asText(null), MAX_DESCRIPTION);
        LocalDate posted = null;
        if (item.hasNonNull("pubDate") && item.get("pubDate").canConvertToLong()) {
            posted = Instant.ofEpochSecond(item.get("pubDate").asLong()).atZone(ZoneOffset.UTC).toLocalDate();
        }
        return new NormalizedJob(
                JobSource.HIMALAYAS,
                Text.clip(id, 768),
                title,
                company,
                Urls.safeHttps(item.path("companyLogo").asText(null)),
                himalayasLocation(item),
                himalayasSalary(item),
                url,
                "Himalayas",
                himalayasJobType(item.path("employmentType").asText("")),
                posted,
                description);
    }

    private static String himalayasLocation(JsonNode item) {
        List<String> places = new ArrayList<>();
        for (JsonNode place : item.path("locationRestrictions")) {
            String name = place.isTextual() ? place.asText() : place.path("name").asText(null);
            if (!Text.isBlank(name)) places.add(name.strip());
        }
        if (places.isEmpty()) return "Remote (worldwide)";
        String shown = places.stream().limit(3).collect(Collectors.joining(", "));
        int extra = places.size() - 3;
        return Text.clip("Remote (" + shown + (extra > 0 ? " +" + extra + " more" : "") + ")", 255);
    }

    private static String himalayasSalary(JsonNode item) {
        long lo = item.path("minSalary").asLong(0), hi = item.path("maxSalary").asLong(0);
        if (lo <= 0 && hi <= 0) return null;
        String currency = item.path("currency").asText("").toUpperCase(Locale.ROOT);
        String symbol = switch (currency) {
            case "USD" -> "$";
            case "CAD" -> "CA$";
            case "EUR" -> "€";
            case "GBP" -> "£";
            case "" -> "";
            default -> currency + " ";
        };
        String text = (lo > 0 && hi > 0 && lo != hi) ? symbol + money(lo) + " - " + symbol + money(hi) : symbol + money(Math.max(lo, hi));
        return Text.clip(text, 120);
    }

    private static JobType himalayasJobType(String value) {
        String kind = value.toLowerCase(Locale.ROOT);
        if (kind.contains("intern")) return JobType.INTERNSHIP;
        if (kind.contains("part")) return JobType.PART_TIME;
        if (kind.contains("contract") || kind.contains("temporary") || kind.contains("freelance")) return JobType.CONTRACT;
        if (kind.contains("full")) return JobType.FULL_TIME;
        return null;
    }

    // --- Shared ------------------------------------------------------------------

    private JsonNode getJson(String base, Map<String, String> params, String name) {
        String query = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "?" + query))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SourceException(name + " could not be reached");
        } catch (Exception e) {
            throw new SourceException(name + " could not be reached");
        }
        int status = response.statusCode();
        if (status == 429) throw new SourceException(name + " rate limit reached");
        if (status == 401 || status == 403) throw new SourceException(name + " rejected the credentials");
        if (status >= 400) {
            log.warn("{} returned HTTP {}", name, status); // status only: the URL would leak Adzuna's key
            throw new SourceException(name + " returned an error");
        }
        try {
            JsonNode data = mapper.readTree(response.body());
            if (data == null || !data.isObject()) throw new SourceException(name + " returned an unexpected response");
            return data;
        } catch (SourceException e) {
            throw e;
        } catch (Exception e) {
            throw new SourceException(name + " returned invalid JSON");
        }
    }

    private static String plain(String value, int limit) {
        if (value == null) return null;
        String text = HtmlText.toText(value, limit);
        return text == null ? null : Text.clip(text.replace('\n', ' '), limit);
    }

    private static LocalDate date(String value) {
        if (value == null) return null;
        try {
            return OffsetDateTime.parse(value).toLocalDate();
        } catch (Exception e) {
            try {
                return Instant.parse(value).atZone(ZoneOffset.UTC).toLocalDate();
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static String money(long value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    /**
     * Combine sources newest first, dropping cross-source duplicates (same title and
     * employer: the same vacancy has a different id in each source). Earlier lists win.
     */
    @SafeVarargs
    public static List<NormalizedJob> merge(List<NormalizedJob>... groups) {
        Set<String> seen = new HashSet<>();
        List<NormalizedJob> out = new ArrayList<>();
        for (List<NormalizedJob> group : groups) {
            for (NormalizedJob job : group) {
                if (seen.add(Text.normalizeKey(job.title()) + "|" + Text.normalizeKey(job.company()))) out.add(job);
            }
        }
        out.sort(Comparator.comparing(NormalizedJob::postedDate, Comparator.nullsFirst(Comparator.naturalOrder())).reversed());
        return out;
    }
}
