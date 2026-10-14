package com.applymate.resume;

import com.applymate.ai.Schemas.*;
import com.applymate.util.Text;

import java.util.*;
import java.util.function.BiFunction;

/**
 * Resumes tailored from the master resume.
 *
 * <p>The master is the user's own record of their career, so a version for one job may
 * reword and reorder it but never change its facts. The model therefore returns edits
 * keyed to the master's numbered sections and entries ({@link MasterTailoring}), and
 * {@link #apply} writes them onto a copy: names, schools, employers, dates and locations
 * always come from the master, whatever the model sends back.
 */
public final class ResumeTailoring {

    private ResumeTailoring() {}

    /** model_used on a document copied straight from the master, without AI. */
    public static final String MASTER_COPY = "master-copy";

    /** The master as model input, with the indexes the edits must refer to. */
    public static String indexedText(ResumeContent master) {
        StringBuilder sb = new StringBuilder("Name: ").append(master.fullName()).append('\n');
        if (!master.headline().isEmpty()) sb.append("Headline: ").append(master.headline()).append('\n');
        if (!master.summary().isEmpty()) sb.append("Summary: ").append(master.summary()).append('\n');
        for (int s = 0; s < master.sections().size(); s++) {
            ResumeSection section = master.sections().get(s);
            sb.append("\n[section ").append(s).append("] ").append(section.heading()).append('\n');
            for (int e = 0; e < section.entries().size(); e++) {
                ResumeEntry entry = section.entries().get(e);
                StringJoiner facts = new StringJoiner(" | ");
                for (String value : List.of(entry.title(), entry.meta(), entry.subtitle(), entry.right(), entry.subtitleRight())) {
                    if (!value.isEmpty()) facts.add(value);
                }
                sb.append("  [entry ").append(e).append("] ").append(facts).append('\n');
                entry.bullets().forEach(b -> sb.append("    - ").append(b).append('\n'));
            }
            if (!section.bullets().isEmpty()) {
                sb.append("  [section bullets]\n");
                section.bullets().forEach(b -> sb.append("    - ").append(b).append('\n'));
            }
        }
        if (!master.skills().isEmpty()) {
            sb.append("\nSkills:\n");
            master.skills().forEach(line -> sb.append("  ").append(line).append('\n'));
        }
        return sb.toString();
    }

    /** A copy of the master with the edits applied. The master itself is not modified. */
    public static ResumeContent apply(ResumeContent masterRaw, MasterTailoring edits) {
        ResumeContent master = ResumeNormalizer.normalize(masterRaw);
        if (edits == null) return master;

        List<ResumeSection> sections = pick(master.sections(), edits.sections(), SectionTailoring::sectionIndex,
                (section, edit) -> {
                    List<ResumeEntry> entries = pick(section.entries(), edit.entries(), EntryTailoring::entryIndex,
                            (entry, entryEdit) -> new ResumeEntry(entry.title(), entry.meta(), entry.right(),
                                    entry.subtitle(), entry.subtitleRight(), reword(entry.bullets(), entryEdit.bullets())));
                    return new ResumeSection(section.heading(), entries, reword(section.bullets(), edit.bullets()));
                });

        // Headline and summary are optional in the template: only replace them when the master uses them.
        String headline = !master.headline().isEmpty() && !Text.isBlank(edits.headline())
                ? Text.clipOrEmpty(edits.headline(), 240) : master.headline();
        String summary = !master.summary().isEmpty() && !Text.isBlank(edits.summary())
                ? Text.clipOrEmpty(edits.summary(), 4000) : master.summary();

        return ResumeNormalizer.normalize(new ResumeContent(master.fullName(), headline, master.contactLine(), summary,
                sections, tailorSkills(master.skills(), edits.skills())));
    }

    /**
     * Reorders {@code items} as the edits list them, then appends any the edits left out.
     * Unknown and repeated indexes are ignored, so nothing can be invented, duplicated
     * or silently dropped.
     */
    static <T, E> List<T> pick(List<T> items, List<E> edits, java.util.function.ToIntFunction<E> index,
                               BiFunction<T, E, T> apply) {
        Set<Integer> used = new HashSet<>();
        List<T> ordered = new ArrayList<>();
        for (E edit : edits == null ? List.<E>of() : edits) {
            if (edit == null) continue;
            int i = index.applyAsInt(edit);
            if (i < 0 || i >= items.size() || !used.add(i)) continue;
            ordered.add(apply.apply(items.get(i), edit));
        }
        for (int i = 0; i < items.size(); i++) if (!used.contains(i)) ordered.add(items.get(i));
        return ordered;
    }

    /**
     * Swap in reworded bullets only where the master had bullets, and never more than it
     * had: extra bullets are where a model turns a skills list into accomplishments the
     * candidate never described.
     */
    static List<String> reword(List<String> original, List<String> proposed) {
        List<String> cleaned = ResumeNormalizer.bullets(proposed);
        if (cleaned.isEmpty() || original.isEmpty()) return original;
        return cleaned.subList(0, Math.min(cleaned.size(), original.size()));
    }

    private record SkillLine(String label, List<String> items) {}

    private static SkillLine split(String line) {
        int colon = line.indexOf(':');
        String label = colon >= 0 ? line.substring(0, colon).strip() : "";
        String rest = colon >= 0 ? line.substring(colon + 1) : line;
        List<String> items = Arrays.stream(rest.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        return new SkillLine(label, items);
    }

    /** Reorder and trim skill lines. Labels and items must already be in the master. */
    static List<String> tailorSkills(List<String> masterSkills, List<String> proposed) {
        List<SkillLine> masterLines = masterSkills.stream().map(ResumeTailoring::split).toList();
        Map<String, String> allowed = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        for (SkillLine line : masterLines) {
            labels.put(line.label().toLowerCase(Locale.ROOT), line.label());
            line.items().forEach(item -> allowed.put(item.toLowerCase(Locale.ROOT), item));
        }

        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : proposed == null ? List.<String>of() : proposed) {
            if (Text.isBlank(raw)) continue;
            SkillLine line = split(raw);
            String key = line.label().toLowerCase(Locale.ROOT);
            if (!labels.containsKey(key) || seen.contains(key)) continue;
            LinkedHashSet<String> kept = new LinkedHashSet<>();
            for (String item : line.items()) {
                String original = allowed.get(item.toLowerCase(Locale.ROOT));
                if (original != null) kept.add(original);
            }
            if (kept.isEmpty()) continue;
            seen.add(key);
            String joined = String.join(", ", kept);
            String label = labels.get(key);
            result.add(label.isEmpty() ? joined : label + ": " + joined);
        }
        if (result.isEmpty()) return masterSkills;
        // A line the model left out is kept as it was rather than dropped.
        for (int i = 0; i < masterLines.size(); i++) {
            if (seen.add(masterLines.get(i).label().toLowerCase(Locale.ROOT))) result.add(masterSkills.get(i));
        }
        return result;
    }
}
