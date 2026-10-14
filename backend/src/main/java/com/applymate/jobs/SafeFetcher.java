package com.applymate.jobs;

import com.applymate.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches one page the user pasted a link to. The server makes a request to a string
 * the user controls, so this is an SSRF boundary: unchecked, {@code http://169.254.169.254/}
 * reads cloud instance metadata and {@code http://localhost:5432/} probes the database.
 *
 * <ul>
 *   <li>http/https only, default ports only</li>
 *   <li>every resolved address must be public (no loopback, private, link-local, CGNAT,
 *       unique-local, multicast or unspecified)</li>
 *   <li>redirects are followed by hand and every hop is re-validated</li>
 *   <li>response size and redirect count are capped</li>
 *   <li>refusals are deliberately vague about what resolves internally</li>
 * </ul>
 *
 * <p>Known limit: the JDK client resolves the host again when connecting, so a DNS
 * rebinding attack with a near-zero TTL could still race the check. Closing that needs
 * an egress proxy or connecting to the vetted IP directly.
 */
@Component
public class SafeFetcher {

    static final int MAX_BYTES = 3 * 1024 * 1024;
    static final int MAX_REDIRECTS = 4;
    private static final Pattern CHARSET = Pattern.compile("charset=([\\w-]+)", Pattern.CASE_INSENSITIVE);
    // A browser user agent: many boards serve a stub or a 403 to obvious bots, and this is
    // one page fetched on a user's behalf, not crawling.
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public record Page(String finalUrl, String html) {}

    public Page fetch(String rawUrl) {
        URI uri = validate(rawUrl);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpResponse<InputStream> response = send(uri);
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("location").orElse(null);
                close(response);
                if (location == null) break;
                // Re-validate: an open redirect to an internal address is the standard way round a naive check.
                uri = validate(uri.resolve(location).toString());
                continue;
            }
            if (status == 401 || status == 403 || status == 429) {
                close(response);
                throw ApiException.unprocessable(
                        "That site blocked the request. Copy the job description text and paste it instead.");
            }
            if (status >= 400) {
                close(response);
                throw ApiException.unprocessable("That page returned an error (" + status + ").");
            }
            String contentType = response.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
            if (!contentType.contains("html") && !contentType.contains("text")) {
                close(response);
                throw ApiException.unprocessable("That link is not a web page.");
            }
            return new Page(uri.toString(), read(response, contentType));
        }
        throw ApiException.unprocessable("That link redirected too many times.");
    }

    /** Public only for tests; rejects anything that is not a public http(s) URL. */
    public static URI validate(String raw) {
        if (raw == null || raw.isBlank()) throw ApiException.badRequest("Paste a link to the job posting.");
        String value = raw.strip();
        if (value.length() > 2048) throw ApiException.badRequest("That URL is too long.");
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw ApiException.badRequest("That does not look like a valid URL.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw ApiException.badRequest("Paste a full http:// or https:// link to the job posting.");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            throw ApiException.badRequest("That does not look like a valid URL.");
        }
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) throw unreachable();
        if (!isPublicHost(uri.getHost())) throw unreachable();
        return uri;
    }

    static boolean isPublicHost(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host.startsWith("[") ? host.substring(1, host.length() - 1) : host);
        } catch (UnknownHostException e) {
            return false;
        }
        if (addresses.length == 0) return false;
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) return false;
        }
        return true;
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = b[0] & 0xff, second = b[1] & 0xff;
            if (first == 0) return false;                                  // "this network"
            if (first == 100 && second >= 64 && second <= 127) return false; // CGNAT 100.64/10
            if (first == 192 && second == 0 && (b[2] & 0xff) == 0) return false; // IETF 192.0.0/24
            if (first == 198 && (second == 18 || second == 19)) return false;  // benchmarking
            if (first >= 240) return false;                                // reserved + broadcast
            return true;
        }
        // IPv6
        if ((b[0] & 0xfe) == 0xfc) return false;                           // unique local fc00::/7
        boolean v4Mapped = true;
        for (int i = 0; i < 10; i++) if (b[i] != 0) { v4Mapped = false; break; }
        if (v4Mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
            try {
                return isPublicAddress(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]}));
            } catch (UnknownHostException e) {
                return false;
            }
        }
        return true;
    }

    private HttpResponse<InputStream> send(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-CA,en;q=0.9")
                .GET().build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.unprocessable("Could not load that page.");
        } catch (IOException | IllegalArgumentException e) {
            throw ApiException.unprocessable("Could not load that page.");
        }
    }

    private static String read(HttpResponse<InputStream> response, String contentType) {
        try (InputStream in = response.body()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read, total = 0;
            while ((read = in.read(buffer)) != -1 && total < MAX_BYTES) {
                int keep = Math.min(read, MAX_BYTES - total);
                out.write(buffer, 0, keep);
                total += keep;
            }
            return out.toString(charset(contentType));
        } catch (IOException e) {
            throw ApiException.unprocessable("Could not load that page.");
        }
    }

    private static Charset charset(String contentType) {
        Matcher m = CHARSET.matcher(contentType);
        if (m.find()) {
            try {
                return Charset.forName(m.group(1));
            } catch (Exception ignored) {
                // fall through
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static void close(HttpResponse<InputStream> response) {
        try {
            response.body().close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }

    private static ApiException unreachable() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "That URL could not be reached.");
    }
}
