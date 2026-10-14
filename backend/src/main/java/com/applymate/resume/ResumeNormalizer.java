package com.applymate.resume;

import com.applymate.ai.Schemas.ResumeContent;
import com.applymate.ai.Schemas.ResumeEntry;
import com.applymate.ai.Schemas.ResumeSection;
import com.applymate.util.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Clips a resume to the stored limits. Model output is clipped rather than rejected:
 * failing validation after the request is spent would waste the user's quota over a
 * few extra characters. The same limits apply to what the user saves.
 */
public final class ResumeNormalizer {

    private ResumeNormalizer() {}

    static final int MAX_SECTIONS = 12;
    static final int MAX_ENTRIES = 20;
    static final int MAX_BULLETS = 20;
    static final int MAX_SKILL_LINES = 40;
    static final int MAX_BULLET_CHARS = 600;

    public static ResumeContent normalize(ResumeContent data) {
        if (data == null) return empty();
        List<ResumeSection> sections = new ArrayList<>();
        for (ResumeSection section : take(data.sections(), MAX_SECTIONS)) {
            if (section == null) continue;
            List<ResumeEntry> entries = new ArrayList<>();
            for (ResumeEntry e : take(section.entries(), MAX_ENTRIES)) {
                if (e == null) continue;
                ResumeEntry entry = new ResumeEntry(
                        Text.clipOrEmpty(e.title(), 300),
                        Text.clipOrEmpty(e.meta(), 500),
                        Text.clipOrEmpty(e.right(), 240),
                        Text.clipOrEmpty(e.subtitle(), 500),
                        Text.clipOrEmpty(e.subtitleRight(), 240),
                        bullets(e.bullets()));
                if (!isEmpty(entry)) entries.add(entry);
            }
            String heading = Text.clipOrEmpty(section.heading(), 160);
            List<String> loose = bullets(section.bullets());
            if (!heading.isEmpty() || !entries.isEmpty() || !loose.isEmpty()) {
                sections.add(new ResumeSection(heading, entries, loose));
            }
        }
        List<String> skills = new ArrayList<>();
        for (String line : take(data.skills(), MAX_SKILL_LINES)) {
            String clipped = Text.clip(line, MAX_BULLET_CHARS);
            if (clipped != null) skills.add(clipped);
        }
        return new ResumeContent(
                Text.clipOrEmpty(data.fullName(), 160),
                Text.clipOrEmpty(data.headline(), 240),
                Text.clipOrEmpty(data.contactLine(), 1000),
                Text.clipOrEmpty(data.summary(), 4000),
                sections,
                skills);
    }

    public static ResumeContent empty() {
        return new ResumeContent("", "", "", "", List.of(), List.of());
    }

    static List<String> bullets(List<String> values) {
        List<String> out = new ArrayList<>();
        for (String value : take(values, MAX_BULLETS)) {
            String clipped = Text.clip(value, MAX_BULLET_CHARS);
            if (clipped != null) out.add(clipped);
        }
        return out;
    }

    private static boolean isEmpty(ResumeEntry e) {
        return e.title().isEmpty() && e.meta().isEmpty() && e.right().isEmpty() && e.subtitle().isEmpty()
                && e.subtitleRight().isEmpty() && e.bullets().isEmpty();
    }

    private static <T> List<T> take(List<T> values, int max) {
        if (values == null) return List.of();
        return values.size() <= max ? values : values.subList(0, max);
    }
}
