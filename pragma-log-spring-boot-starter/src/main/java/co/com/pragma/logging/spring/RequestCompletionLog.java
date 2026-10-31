package co.com.pragma.logging.spring;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;

/**
 * One log per finished request with {@code http.status} and {@code http.durationMs} (ENR-01), shared by the
 * Servlet and WebFlux filters. It carries the request context like any other log of the request.
 */
final class RequestCompletionLog {

    /** Logger of the request log, so it can be tuned with logging.level like any other. */
    static final String LOGGER_NAME = "co.com.pragma.logging.http";
    static final String HTTP_STATUS = "http.status";
    static final String HTTP_DURATION_MS = "http.durationMs";

    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);
    private static final AntPathMatcher PATHS = new AntPathMatcher();

    private final boolean enabled;
    private final List<String> excludePaths;

    RequestCompletionLog(PragmaLogProperties.RequestLog properties) {
        this.enabled = properties.isEnabled();
        this.excludePaths = List.copyOf(properties.getExcludePaths());
    }

    /** Whether a request to this path gets a completion log. */
    boolean appliesTo(String path) {
        return enabled && log.isInfoEnabled() && excludePaths.stream().noneMatch(pattern -> PATHS.match(pattern, path));
    }

    /**
     * Writes the completion log with the request context in the MDC, whatever thread calls it.
     *
     * @param status      response status, or {@code null} when the client went away before it was known
     * @param startNanos  {@link System#nanoTime()} when the request started
     */
    void write(Map<String, String> context, Integer status, long startNanos) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        String request = context.get(RequestLogContext.HTTP_METHOD) + " " + context.get(RequestLogContext.HTTP_PATH);
        Map<String, String> previous = RequestLogContext.putInMdc(context);
        try {
            if (status == null) {
                log.atInfo().addKeyValue(HTTP_DURATION_MS, durationMs)
                        .log("HTTP {} cancelled after {} ms", request, durationMs);
            } else {
                log.atInfo().addKeyValue(HTTP_STATUS, status).addKeyValue(HTTP_DURATION_MS, durationMs)
                        .log("HTTP {} {} in {} ms", request, status, durationMs);
            }
        } finally {
            RequestLogContext.restoreMdc(previous);
        }
    }
}
