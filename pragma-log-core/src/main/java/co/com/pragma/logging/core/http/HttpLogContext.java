package co.com.pragma.logging.core.http;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * HTTP request context added to every log of a request (CTX-02, ENR-01..03), the same in every framework:
 * {@code requestId}, {@code http.method}, {@code http.path}, allow-listed headers and query parameters.
 */
public final class HttpLogContext {

    public static final String REQUEST_ID = "requestId";
    public static final String HTTP_METHOD = "http.method";
    public static final String HTTP_PATH = "http.path";
    public static final String HEADER_PREFIX = "http.header.";
    /** Prefix of query parameters in the context (ENR-02). */
    public static final String QUERY_PREFIX = "http.query.";
    /** Key-value of the request logs with the captured request body (ENR-04). */
    public static final String REQUEST_BODY = "http.request.body";
    /** Key-value of the completion log with the captured response body (ENR-04). */
    public static final String RESPONSE_BODY = "http.response.body";
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
        return build(HttpLogSettings.of(requestIdHeader, headers), method, path, headerValues, List::of,
                name -> null, List::of);
    }

    /**
     * Returns the context entries of a request, {@link #REQUEST_ID} first.
     *
     * @param headerValues     first value of a request header, or {@code null}
     * @param headerNames      names of the request headers, used for {@code *}
     * @param queryValues      first value of a query parameter, or {@code null}
     * @param queryNames       names of the query parameters, used for {@code *}
     */
    public static Map<String, String> build(HttpLogSettings settings, String method, String path,
            UnaryOperator<String> headerValues, Supplier<Iterable<String>> headerNames,
            UnaryOperator<String> queryValues, Supplier<Iterable<String>> queryNames) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put(REQUEST_ID, requestId(headerValues.apply(settings.requestIdHeader())));
        context.put(HTTP_METHOD, method);
        context.put(HTTP_PATH, path);
        for (String header : names(settings.headers(), settings.allHeaders(), headerNames,
                settings.excludedHeaderNames())) {
            String value = headerValues.apply(header);
            if (value != null) {
                context.put(HEADER_PREFIX + header.toLowerCase(Locale.ROOT), value);
            }
        }
        for (String parameter : names(settings.queryParams(), settings.allQueryParams(), queryNames, Set.of())) {
            String value = queryValues.apply(parameter);
            if (value != null) {
                context.put(QUERY_PREFIX + parameter, value);
            }
        }
        return Collections.unmodifiableMap(context);
    }

    /** Explicit names, plus every available name but the excluded ones when {@code *} is listed. */
    private static Set<String> names(List<String> configured, boolean all, Supplier<Iterable<String>> available,
            Set<String> excluded) {
        Set<String> names = new LinkedHashSet<>();
        for (String name : configured) {
            if (!HttpLogSettings.ALL.equals(name)) {
                names.add(name);
            }
        }
        if (all) {
            for (String name : available.get()) {
                if (!excluded.contains(name.toLowerCase(Locale.ROOT))) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    /**
     * First value of each parameter of a raw query string ({@code a=1&b=2}), decoded. Only the URL is read: unlike
     * {@code ServletRequest.getParameter}, a form body is never consumed.
     */
    public static Map<String, String> queryParameters(String query) {
        if (query == null || query.isEmpty()) {
            return Map.of();
        }
        Map<String, String> parameters = new LinkedHashMap<>();
        for (String pair : query.split("&", -1)) {
            if (pair.isEmpty()) {
                continue;
            }
            int separator = pair.indexOf('=');
            try {
                String name = URLDecoder.decode(separator < 0 ? pair : pair.substring(0, separator),
                        StandardCharsets.UTF_8);
                String value = separator < 0 ? "" : URLDecoder.decode(pair.substring(separator + 1),
                        StandardCharsets.UTF_8);
                parameters.putIfAbsent(name, value);
            } catch (IllegalArgumentException e) {
                // Malformed escape: the parameter is left out.
            }
        }
        return parameters;
    }

    /** Whether a key belongs to the request context, so it can be cleared without touching others. */
    public static boolean isContextKey(String key) {
        return REQUEST_ID.equals(key) || HTTP_METHOD.equals(key) || HTTP_PATH.equals(key)
                || key.startsWith(HEADER_PREFIX) || key.startsWith(QUERY_PREFIX);
    }

    /** Message of the request start log (ENR-08). */
    public static String entryMessage(Map<String, String> context) {
        return "HTTP " + context.get(HTTP_METHOD) + " " + context.get(HTTP_PATH) + " started";
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
