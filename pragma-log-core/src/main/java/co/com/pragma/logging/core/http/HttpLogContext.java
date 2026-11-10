package co.com.pragma.logging.core.http;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * HTTP request context added to every log of a request (CTX-02, ENR-01), the same in every framework:
 * {@code requestId}, {@code http.method}, {@code http.path} and allow-listed headers.
 */
public final class HttpLogContext {

    public static final String REQUEST_ID = "requestId";
    public static final String HTTP_METHOD = "http.method";
    public static final String HTTP_PATH = "http.path";
    public static final String HEADER_PREFIX = "http.header.";
    /** Key-value of the request completion log. */
    public static final String HTTP_STATUS = "http.status";
    /** Key-value of the request completion log. */
    public static final String HTTP_DURATION_MS = "http.durationMs";
    /** Logger of the request completion log, so it can be tuned like any other. */
    public static final String COMPLETION_LOGGER = "co.com.pragma.logging.http";

    /** Incoming ids are reused only when they are short and safe, to avoid log injection. */
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private HttpLogContext() {
    }

    /**
     * Returns the context entries of a request, {@link #REQUEST_ID} first.
     *
     * @param requestIdHeader header that carries the request id
     * @param headers         allow-listed headers copied to the context
     * @param method          request method
     * @param path            request path, without query string
     * @param headerValues    returns the first value of a request header, or {@code null}
     * @return the context entries, {@link #REQUEST_ID} first
     */
    public static Map<String, String> build(String requestIdHeader, List<String> headers, String method, String path,
            UnaryOperator<String> headerValues) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put(REQUEST_ID, requestId(headerValues.apply(requestIdHeader)));
        context.put(HTTP_METHOD, method);
        context.put(HTTP_PATH, path);
        for (String header : headers) {
            String value = headerValues.apply(header);
            if (value != null) {
                context.put(HEADER_PREFIX + header.toLowerCase(Locale.ROOT), value);
            }
        }
        return Collections.unmodifiableMap(context);
    }

    /** Whether a key belongs to the request context, so it can be cleared without touching others. */
    public static boolean isContextKey(String key) {
        return REQUEST_ID.equals(key) || HTTP_METHOD.equals(key) || HTTP_PATH.equals(key)
                || key.startsWith(HEADER_PREFIX);
    }

    /** Message of the request completion log; {@code status} is {@code null} when the client went away. */
    public static String completionMessage(Map<String, String> context, Integer status, long durationMs) {
        String request = context.get(HTTP_METHOD) + " " + context.get(HTTP_PATH);
        return status == null
                ? "HTTP " + request + " cancelled after " + durationMs + " ms"
                : "HTTP " + request + " " + status + " in " + durationMs + " ms";
    }

    private static String requestId(String incoming) {
        return incoming != null && SAFE_REQUEST_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
    }
}
