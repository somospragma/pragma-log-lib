package co.com.pragma.logging.spring;

import co.com.pragma.logging.core.http.HttpLogContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.slf4j.MDC;

/**
 * HTTP request context added to the logs ({@link HttpLogContext}), shared by the Servlet and WebFlux filters,
 * plus the MDC handling both need.
 */
final class RequestLogContext {

    static final String REQUEST_ID = HttpLogContext.REQUEST_ID;
    static final String HTTP_METHOD = HttpLogContext.HTTP_METHOD;
    static final String HTTP_PATH = HttpLogContext.HTTP_PATH;

    private RequestLogContext() {
    }

    /**
     * Builds the context of a request.
     *
     * @param headers     first value of a header, or {@code null}
     * @param headerNames names of the request headers, for {@code *}
     * @param query       raw query string, or {@code null}
     */
    static Map<String, String> build(PragmaLogProperties.Http properties, String method, String path,
            UnaryOperator<String> headers, Supplier<Iterable<String>> headerNames, String query) {
        Map<String, String> parameters = HttpLogContext.queryParameters(query);
        return HttpLogContext.build(properties.settings(), method, path, headers, headerNames, parameters::get,
                parameters::keySet);
    }

    static boolean isContextKey(String key) {
        return HttpLogContext.isContextKey(key);
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
}
