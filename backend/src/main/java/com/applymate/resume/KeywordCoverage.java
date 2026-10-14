package com.applymate.resume;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An ATS-style keyword check, with no AI and no quota: which terms from the job advert
 * does a resume or cover letter actually contain?
 *
 * <p>Terms come from three places: a curated dictionary of technical terms found in
 * the advert; acronyms and capitalised product names the advert repeats (catching
 * tools the dictionary has never heard of); and, when a match analysis exists, the
 * short requirement phrases the model pulled out. Each is then looked for in the
 * document as a whole word, counting known variants ("Node.js", "NodeJS") as one.
 */
@Component
public class KeywordCoverage {

    static final int MAX_TERMS = 30;

    /** Capitalised words that are just English, not tools. */
    private static final Set<String> COMMON = Set.of(
            "the", "and", "for", "with", "our", "you", "your", "we", "will", "are", "this", "that", "from", "have", "has",
            "who", "what", "about", "job", "role", "team", "teams", "work", "working", "company", "experience", "skills",
            "strong", "ability", "including", "requirements", "responsibilities", "qualifications", "preferred", "required",
            "plus", "bonus", "benefits", "salary", "location", "remote", "hybrid", "office", "canada", "united", "states",
            "monday", "friday", "january", "december", "equal", "opportunity", "employer", "apply", "please", "us", "an",
            "a", "in", "on", "of", "to", "or", "as", "at", "be", "by", "is", "it", "if", "all", "new", "help", "join",
            "build", "design", "develop", "senior", "junior", "intern", "lead", "staff", "manager", "engineer", "developer",
            "software", "full", "time", "part", "years", "year", "bachelor", "degree", "university", "inc", "ltd", "llc");

    private static final Pattern ACRONYM = Pattern.compile("\\b[A-Z][A-Z0-9+#/.]{1,8}\\b");
    /** CamelCase (GraphQL), dotted (Deno.js) or plain capitalised (Fluxbase) names. */
    private static final Pattern PROPER = Pattern.compile("\\b[A-Z][a-z]+(?:[A-Z][a-z0-9]+)+\\b|\\b[A-Z][a-z0-9]+\\.(?:js|io|ai|net)\\b|\\b[A-Z][a-z0-9]{2,}\\b");

    private final List<List<String>> dictionary;

    public KeywordCoverage() {
        this.dictionary = loadDictionary();
    }

    public record Coverage(int coveragePct, List<String> found, List<String> missing) {}

    public Coverage check(String advert, List<String> requirementHints, String documentText) {
        List<List<String>> terms = extractTerms(advert == null ? "" : advert, requirementHints);
        String doc = documentText == null ? "" : documentText;
        List<String> found = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (List<String> variants : terms) {
            boolean present = variants.stream().anyMatch(v -> contains(doc, v));
            (present ? found : missing).add(variants.getFirst());
        }
        int pct = terms.isEmpty() ? 100 : (int) Math.round(100.0 * found.size() / terms.size());
        return new Coverage(pct, found, missing);
    }

    /** Terms in advert order of importance: dictionary hits, then repeated names, then AI hints. */
    List<List<String>> extractTerms(String advert, List<String> hints) {
        LinkedHashMap<String, List<String>> terms = new LinkedHashMap<>();
        for (List<String> variants : dictionary) {
            if (variants.stream().anyMatch(v -> contains(advert, v))) terms.putIfAbsent(key(variants.getFirst()), variants);
        }
        Map<String, Integer> counts = new HashMap<>();
        for (Pattern p : List.of(ACRONYM, PROPER)) {
            Matcher m = p.matcher(advert);
            while (m.find()) {
                String word = m.group().replaceAll("[.]+$", "");
                if (word.length() < 2 || COMMON.contains(word.toLowerCase(Locale.ROOT))) continue;
                // Every sentence starts with a capital; only a mid-sentence capital suggests a name.
                if (p == PROPER && startsSentence(advert, m.start())) continue;
                counts.merge(word, 1, Integer::sum);
            }
        }
        counts.entrySet().stream()
                .filter(e -> e.getValue() >= 2) // mentioned twice: likely a real requirement, not noise
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> terms.putIfAbsent(key(e.getKey()), List.of(e.getKey())));
        for (String hint : hints == null ? List.<String>of() : hints) {
            String h = hint == null ? "" : hint.strip();
            // Short noun phrases only; long sentences ("3+ years building...") never appear verbatim.
            if (!h.isEmpty() && h.split("\\s+").length <= 3 && h.length() <= 40) terms.putIfAbsent(key(h), List.of(h));
        }
        return terms.values().stream().limit(MAX_TERMS).toList();
    }

    /** True when the word at {@code index} begins the text, a line, or follows a sentence end. */
    static boolean startsSentence(String text, int index) {
        int i = index - 1;
        while (i >= 0 && text.charAt(i) == ' ') i--;
        if (i < 0) return true;
        char c = text.charAt(i);
        return c == '.' || c == '!' || c == '?' || c == '\n' || c == ':' || c == '-' || c == '•';
    }

    /** Whole-word, case-insensitive; symbols in terms like C++ or CI/CD are matched literally. */
    static boolean contains(String text, String term) {
        if (term.isBlank()) return false;
        String quoted = Pattern.quote(term);
        int flags = term.length() <= 2 ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE; // "Go" and "R" need their case
        return Pattern.compile("(?<![A-Za-z0-9+#])" + quoted + "(?![A-Za-z0-9+#])", flags).matcher(text).find();
    }

    private static String key(String term) {
        return term.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+#]", "");
    }

    private static List<List<String>> loadDictionary() {
        try {
            String text = new String(new ClassPathResource("keywords/tech-terms.txt").getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            List<List<String>> out = new ArrayList<>();
            for (String line : text.split("\\R")) {
                String l = line.strip();
                if (l.isEmpty() || l.startsWith("#")) continue;
                out.add(Arrays.stream(l.split("\\|")).map(String::strip).filter(s -> !s.isEmpty()).toList());
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
