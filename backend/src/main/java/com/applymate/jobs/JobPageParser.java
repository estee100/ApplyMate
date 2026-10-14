package com.applymate.jobs;

import com.applymate.util.Text;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Pulls a job out of a fetched page, cheapest first:
 * <ol>
 *   <li>schema.org JSON-LD {@code JobPosting}, which most boards emit: exact data, no model call</li>
 *   <li>known description containers for boards without it</li>
 *   <li>otherwise the caller hands the page text to the model, which costs a request</li>
 * </ol>
 */
@Component
public class JobPageParser {

    private static final int MIN_DESCRIPTION = 200;

    private static final List<String> CONTAINERS = List.of(
            ".show-more-less-html__markup", ".description__text",
            "#jobDescriptionText",
            "[class*=job-description]", "[class*=jobDescription]", "[class*=posting-description]",
            "[data-automation-id=jobPostingDescription]",
            ".posting-page", "#content .job", "article");

    private final ObjectMapper mapper;

    public JobPageParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public record Parsed(String title, String company, String location, String description) {
        public boolean usable() {
            return description != null && description.length() >= MIN_DESCRIPTION;
        }
    }

    public Parsed parse(String html) {
        Document doc = Jsoup.parse(html);
        Parsed fromJsonLd = jsonLd(doc);
        if (fromJsonLd != null && fromJsonLd.usable()) return fromJsonLd;
        Parsed fromContainer = containers(doc);
        if (fromContainer != null) {
            // JSON-LD may have the title and company even when its description is thin.
            if (fromJsonLd != null) {
                return new Parsed(firstNonBlank(fromJsonLd.title(), fromContainer.title()),
                        fromJsonLd.company(), fromJsonLd.location(), fromContainer.description());
            }
            return fromContainer;
        }
        return fromJsonLd;
    }

    /** Whole-page text for the model, as a last resort. */
    public String pageText(String html, int limit) {
        String text = HtmlText.toText(html, limit);
        return text == null ? "" : text;
    }

    Parsed jsonLd(Document doc) {
        for (Element script : doc.select("script[type=application/ld+json]")) {
            JsonNode posting;
            try {
                posting = findPosting(mapper.readTree(script.data()));
            } catch (Exception e) {
                continue;
            }
            if (posting == null) continue;
            JsonNode org = posting.path("hiringOrganization");
            String company = org.isTextual() ? org.asText() : org.path("name").asText("");
            JsonNode loc = posting.path("jobLocation");
            if (loc.isArray()) loc = loc.path(0);
            JsonNode address = loc.path("address");
            List<String> parts = new ArrayList<>();
            for (String key : List.of("addressLocality", "addressRegion", "addressCountry")) {
                JsonNode part = address.path(key);
                String value = part.isTextual() ? part.asText() : part.path("name").asText("");
                if (!value.isBlank()) parts.add(value.strip());
            }
            String location = String.join(", ", parts);
            if (location.isEmpty() && posting.path("jobLocationType").asText("").toUpperCase().contains("TELECOMMUTE")) {
                location = "Remote";
            }
            return new Parsed(
                    Text.clipOrEmpty(HtmlText.toText(posting.path("title").asText(""), 500), 500),
                    Text.clipOrEmpty(company, 255),
                    Text.clipOrEmpty(location, 255),
                    HtmlText.toText(posting.path("description").asText(""), JobSources.MAX_DESCRIPTION));
        }
        return null;
    }

    /** JobPosting can be top level, inside @graph, or in a list. */
    private static JsonNode findPosting(JsonNode node) {
        if (node == null) return null;
        if (node.isObject()) {
            JsonNode type = node.path("@type");
            boolean isPosting = type.isArray()
                    ? java.util.stream.StreamSupport.stream(type.spliterator(), false).anyMatch(t -> "JobPosting".equals(t.asText()))
                    : "JobPosting".equals(type.asText());
            if (isPosting) return node;
            for (JsonNode child : node) {
                JsonNode found = findPosting(child);
                if (found != null) return found;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findPosting(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private Parsed containers(Document doc) {
        for (String selector : CONTAINERS) {
            for (Element element : doc.select(selector)) {
                String description = HtmlText.toText(element.html(), JobSources.MAX_DESCRIPTION);
                if (description != null && description.length() >= MIN_DESCRIPTION) {
                    Element h1 = doc.selectFirst("h1");
                    String title = h1 != null ? h1.text() : doc.title();
                    return new Parsed(Text.clipOrEmpty(title, 500), "", "", description);
                }
            }
        }
        return null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
