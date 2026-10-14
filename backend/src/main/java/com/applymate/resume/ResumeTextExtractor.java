package com.applymate.resume;

import com.applymate.error.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Text from an uploaded resume. The file type is decided from its bytes, not from the
 * declared content type or extension, which the client controls.
 */
@Component
public class ResumeTextExtractor {

    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    /** Cap on decompressed DOCX XML, so a zip bomb cannot exhaust memory. */
    private static final int MAX_XML_BYTES = 20 * 1024 * 1024;
    private static final int MIN_TEXT_CHARS = 150;
    public static final int MAX_TEXT_CHARS = 60_000;

    public record Extracted(String text, String contentType) {}

    public Extracted extract(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw ApiException.badRequest("That file is empty.");
        if (bytes.length > MAX_FILE_BYTES) throw ApiException.badRequest("Resumes must be 5 MB or smaller.");

        Extracted result;
        if (startsWith(bytes, "%PDF")) {
            result = new Extracted(pdf(bytes), "application/pdf");
        } else if (startsWith(bytes, "PK\u0003\u0004")) {
            result = new Extracted(docx(bytes), "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        } else {
            result = new Extracted(plainText(bytes), "text/plain");
        }

        String text = tidy(result.text());
        if (text.length() < MIN_TEXT_CHARS) {
            throw ApiException.unprocessable(
                    "We couldn't read enough text from that file. If it is a scanned image, export it as a text PDF or DOCX.");
        }
        return new Extracted(text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text, result.contentType());
    }

    private static String pdf(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            if (doc.isEncrypted()) throw ApiException.unprocessable("That PDF is password protected.");
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        } catch (IOException e) {
            throw ApiException.unprocessable("That PDF could not be read.");
        }
    }

    private static String docx(byte[] bytes) {
        byte[] xml = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    xml = readCapped(zip);
                    break;
                }
            }
        } catch (IOException e) {
            throw ApiException.unprocessable("That Word document could not be read.");
        }
        if (xml == null) throw ApiException.unprocessable("Upload a PDF, a Word (.docx) file or plain text.");

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // XXE: an uploaded document must not be able to read server files or make requests.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            StringBuilder out = new StringBuilder();
            NodeList paragraphs = doc.getElementsByTagNameNS("*", "p");
            for (int i = 0; i < paragraphs.getLength(); i++) {
                appendRuns(paragraphs.item(i), out);
                out.append('\n');
            }
            return out.toString();
        } catch (Exception e) {
            throw ApiException.unprocessable("That Word document could not be read.");
        }
    }

    private static void appendRuns(Node node, StringBuilder out) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            String name = child.getLocalName();
            if ("t".equals(name)) out.append(child.getTextContent());
            else if ("tab".equals(name)) out.append('\t');
            else if ("br".equals(name)) out.append('\n');
            else if (child.hasChildNodes()) appendRuns(child, out);
        }
    }

    private static byte[] readCapped(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0, read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > MAX_XML_BYTES) throw ApiException.unprocessable("That Word document is too large to read.");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String plainText(byte[] bytes) {
        try {
            // Strict decode: a binary file (an image renamed .txt) fails here instead of
            // becoming a page of replacement characters sent to the model.
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            long control = text.chars().filter(c -> c < 32 && c != '\n' && c != '\r' && c != '\t').count();
            if (control > text.length() / 100) throw ApiException.badRequest("Upload a PDF, a Word (.docx) file or plain text.");
            return text;
        } catch (CharacterCodingException e) {
            throw ApiException.badRequest("Upload a PDF, a Word (.docx) file or plain text.");
        }
    }

    /** Collapse runs of spaces and blank lines that PDF extraction leaves behind. */
    static String tidy(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[ \\t\\u00A0]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    private static boolean startsWith(byte[] bytes, String magic) {
        byte[] m = magic.getBytes(StandardCharsets.ISO_8859_1);
        if (bytes.length < m.length) return false;
        for (int i = 0; i < m.length; i++) if (bytes[i] != m[i]) return false;
        return true;
    }
}
