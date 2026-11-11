package co.com.pragma.logging.spring;

import co.com.pragma.logging.core.http.HttpLogContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;

/**
 * One log per finished request with {@code http.status} and {@code http.durationMs} (ENR-01), and optionally one
 * when it starts (ENR-08), shared by the Servlet and WebFlux filters. They carry the request context like any other
 * log of the request, and the captured bodies when enabled (ENR-04).
 */
final class RequestCompletionLog {

    static final String LOGGER_NAME = HttpLogContext.COMPLETION_LOGGER;

    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);
    private static final AntPathMatcher PATHS = new AntPathMatcher();

    private final boolean enabled;
    private final boolean entry;
    private final List<String> excludePaths;

    RequestCompletionLog(PragmaLogProperties.RequestLog properties) {
        this.enabled = properties.isEnabled();
        this.entry = properties.isEntry();
        this.excludePaths = List.copyOf(properties.getExcludePaths());
    }

    /** Whether a request to this path gets request logs. */
    boolean appliesTo(String path) {
        return enabled && log.isInfoEnabled() && excludePaths.stream().noneMatch(pattern -> PATHS.match(pattern, path));
    }

    /** Writes the start log, when enabled, with the request context in the MDC. */
    void writeEntry(Map<String, String> context) {
        if (!entry) {
            return;
        }
        Map<String, String> previous = RequestLogContext.putInMdc(context);
        try {
            log.info(HttpLogContext.entryMessage(context));
        } finally {
            RequestLogContext.restoreMdc(previous);
        }
    }

    /**
     * Writes the completion log with the request context in the MDC, whatever thread calls it.
     *
     * @param status      response status, or {@code null} when the client went away before it was known
     * @param startNanos  {@link System#nanoTime()} when the request started
     * @param bodies      captured bodies by key-value name; values are masked by the encoder
     */
    void write(Map<String, String> context, Integer status, long startNanos, Map<String, Object> bodies) {
        write(context, status, startNanos, bodies, null);
    }

    /**
     * Same as {@link #write(Map, Integer, long, Map)}; when the request failed with a server error, the log is an
     * ERROR with the exception, so the stack trace of an unhandled exception carries the request context. The
     * container writes its own log of that exception after the request context is gone.
     *
     * @param error the exception that ended the request, or {@code null}
     */
    void write(Map<String, String> context, Integer status, long startNanos, Map<String, Object> bodies,
            Throwable error) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        Map<String, String> previous = RequestLogContext.putInMdc(context);
        try {
            boolean failed = error != null && (status == null || status >= 500);
            var event = failed ? log.atError().setCause(error) : log.atInfo();
            if (status != null) {
                event = event.addKeyValue(HttpLogContext.HTTP_STATUS, status);
            }
            event = event.addKeyValue(HttpLogContext.HTTP_DURATION_MS, durationMs);
            for (Map.Entry<String, Object> body : bodies.entrySet()) {
                event = event.addKeyValue(body.getKey(), body.getValue());
            }
            event.log(HttpLogContext.completionMessage(context, status, durationMs));
        } finally {
            RequestLogContext.restoreMdc(previous);
        }
    }

    void write(Map<String, String> context, Integer status, long startNanos) {
        write(context, status, startNanos, Map.of());
    }
}
