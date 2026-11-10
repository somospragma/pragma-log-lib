package co.com.pragma.logging.core.http;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * Optional capture of request and response bodies in the request logs (ENR-04).
 *
 * <p>JSON and form bodies become objects, so the encoder masks them like any logged object: by
 * sensitive field names at any depth (password, token, cardNumber...) and by patterns. Other text
 * bodies are kept as text, where only patterns apply. Bodies longer than {@code maxSize} bytes are cut, and binary
 * content types are never captured.
 *
 * @param request      whether request bodies are captured
 * @param response     whether response bodies are captured
 * @param maxSize      bytes kept from each body
 * @param contentTypes captured content types; {@code type/*} and {@code +json} suffixes are accepted
 */
public record HttpBodyCapture(boolean request, boolean response, int maxSize, List<String> contentTypes) {

    public static final int DEFAULT_MAX_SIZE = 4096;

    public static final List<String> DEFAULT_CONTENT_TYPES = List.of("application/json", "application/*+json",
            "application/x-www-form-urlencoded", "application/xml", "text/*");

    private static final HttpBodyCapture NONE = new HttpBodyCapture(false, false, 0, List.of());

    /** Text added to a body that was cut. */
    static final String TRUNCATED = "...[truncated ";

    public HttpBodyCapture {
        if (maxSize < 0) {
            throw new IllegalArgumentException("pragma.log.http.body.max-size must be >= 0");
        }
        contentTypes = contentTypes.stream().map(type -> type.trim().toLowerCase(Locale.ROOT)).toList();
    }

    /** Captures nothing. */
    public static HttpBodyCapture none() {
        return NONE;
    }

    public boolean enabled() {
        return (request || response) && maxSize > 0;
    }

    /** Whether a body of this content type is captured; bodies without a content type are not. */
    public boolean captures(String contentType) {
        if (!enabled() || contentType == null || contentType.isBlank()) {
            return false;
        }
        String type = mediaType(contentType);
        for (String accepted : contentTypes) {
            if (accepted.equals(type)
                    || (accepted.endsWith("/*") && type.startsWith(accepted.substring(0, accepted.length() - 1)))
                    || (accepted.startsWith("application/*+") && type.startsWith("application/")
                            && type.endsWith(accepted.substring("application/*".length())))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the value logged for a body: a {@code Map} or {@code List} for JSON and forms, otherwise text;
     * {@code null} for an empty body.
     *
     * @param bytes       the first bytes of the body; more than {@code maxSize} means it was cut
     * @param totalLength full length in bytes, or {@code -1} when unknown
     * @param contentType content type, with an optional charset
     */
    public Object value(byte[] bytes, long totalLength, String contentType) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        long length = Math.max(totalLength, bytes.length);
        int kept = Math.min(bytes.length, maxSize);
        Charset charset = charset(contentType);
        String text = new String(bytes, 0, kept, charset);
        if (length > kept) {
            return text + TRUNCATED + (length - kept) + " bytes]";
        }
        String type = mediaType(contentType);
        try {
            if (type.equals("application/json") || type.endsWith("+json")) {
                return JsonMapper.shared().readValue(text, Object.class);
            }
            if (type.equals("application/x-www-form-urlencoded")) {
                return form(text, charset);
            }
        } catch (RuntimeException e) {
            // Not what the content type says: logged as text.
        }
        return text;
    }

    private static Map<String, String> form(String body, Charset charset) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String pair : body.split("&", -1)) {
            if (pair.isEmpty()) {
                continue;
            }
            int separator = pair.indexOf('=');
            String name = separator < 0 ? pair : pair.substring(0, separator);
            String value = separator < 0 ? "" : pair.substring(separator + 1);
            fields.put(URLDecoder.decode(name, charset), URLDecoder.decode(value, charset));
        }
        return fields;
    }

    private static String mediaType(String contentType) {
        int parameters = contentType.indexOf(';');
        return (parameters < 0 ? contentType : contentType.substring(0, parameters)).trim().toLowerCase(Locale.ROOT);
    }

    private static Charset charset(String contentType) {
        if (contentType != null) {
            for (String parameter : contentType.split(";", -1)) {
                String trimmed = parameter.trim();
                if (trimmed.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                    try {
                        return Charset.forName(trimmed.substring("charset=".length()).replace("\"", ""));
                    } catch (RuntimeException e) {
                        return StandardCharsets.UTF_8;
                    }
                }
            }
        }
        return StandardCharsets.UTF_8;
    }
}
