package com.applymate.jobs;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;

/** Untrusted HTML reduced to plain text, keeping paragraph and list breaks. */
public final class HtmlText {

    private HtmlText() {}

    private static final String BREAK = "\u2028"; // line separator: never in real HTML text, so a safe marker

    public static String toText(String html, int limit) {
        if (html == null || html.isBlank()) return null;
        Document doc = Jsoup.parse(html);
        doc.select("script, style, noscript, svg, iframe, nav, footer, header, form").remove();
        doc.select("br").before(BREAK);
        // Bullet first, so the block break below lands before it rather than between dash and text.
        for (Element li : doc.select("li")) li.prepend("- ");
        for (Element block : doc.select("p, li, div, h1, h2, h3, h4, h5, h6, tr, section, article, ul, ol")) {
            block.prepend(BREAK);
            block.append(BREAK);
        }
        String text = Jsoup.clean(doc.body().html(), "", Safelist.none(),
                new Document.OutputSettings().prettyPrint(false));
        text = Parser.unescapeEntities(text, false)
                .replace(BREAK, "\n")
                .replaceAll("[ \\t\\u00A0]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
        if (text.isEmpty()) return null;
        return text.length() <= limit ? text : text.substring(0, limit);
    }
}
