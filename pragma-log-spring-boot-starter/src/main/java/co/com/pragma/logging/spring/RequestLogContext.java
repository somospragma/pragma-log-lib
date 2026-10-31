package co.com.pragma.logging.spring;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * HTTP request context added to the logs (CTX-02, ENR-01), shared by the Servlet and WebFlux filters:
 * {@code requestId}, {@code http.method}, {@code http.path} and allow-listed headers.
 */
final class RequestLogContext {

    static final String REQUEST_ID = "requestId";
    static final String HTTP_METHOD = "http.method";
    static final String HTTP_PATH = "http.path";
    static final String HEADER_PREFIX = "http.header.";

    /** Incoming ids are reused only when they are short and safe, to avoid log injection. */
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private RequestLogContext() {
    }

    /**
     * @param properties HTTP settings
     * @param method     request method
     * @param path       request path, without query string
     * @param headers    returns the first value of a request header, or {@code null}
     * @return the context entries, {@link #REQUEST_ID} first
     */
    static Map<String, String> build(PragmaLogProperties.Http properties, String method, String path,
            UnaryOperator<String> headers) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put(REQUEST_ID, requestId(headers.apply(properties.getRequestIdHeader())));
        context.put(HTTP_METHOD, method);
        context.put(HTTP_PATH, path);
        for (String header : properties.getHeaders()) {
            String value = headers.apply(header);
            if (value != null) {
                context.put(HEADER_PREFIX + header.toLowerCase(Locale.ROOT), value);
            }
        }
        return Collections.unmodifiableMap(context);
    }

    /** Whether an MDC key belongs to the request context, so it can be cleared without touching others. */
    static boolean isContextKey(String key) {
        return REQUEST_ID.equals(key) || HTTP_METHOD.equals(key) || HTTP_PATH.equals(key)
                || key.startsWith(HEADER_PREFIX);
    }

    /** Puts the context in the MDC of the current thread; returns the values it replaced, for {@link #restoreMdc}. */
    static Map<String, String> putInMdc(Map<String, String> context) {
        Map<String, String> previous = new LinkedHashMap<>();
        context.forEach((key, value) -> {
            previous.put(key, MDC.get(key));
            MDC.put(key, value);
        });
        return previous;
    }

    /** Restores the MDC entries replaced by {@link #putInMdc}, leaving every other entry alone (CTX-07). */
    static void restoreMdc(Map<String, String> previous) {
        previous.forEach((key, value) -> {
            if (value == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, value);
            }
        });
    }

    private static String requestId(String incoming) {
        return incoming != null && SAFE_REQUEST_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
    }
}
