package co.com.pragma.logging.quarkus;

import co.com.pragma.logging.core.http.HttpLogContext;
import co.com.pragma.logging.core.http.HttpLogSettings;
import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.event.Observes;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jboss.logmanager.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Adds the {@link HttpLogContext} to the MDC of every log written while a request is handled (CTX-06, ENR-02,
 * ENR-03), writes the start log when enabled (ENR-08) and the completion log with status, duration and the bodies
 * captured by {@link PragmaLogBodyCapture} (ENR-01, ENR-04). Quarkus keeps the MDC in the Vert.x duplicated context
 * of the request, so it follows the request across event-loop and worker threads and is gone when the request
 * ends. Registered only when the application has an HTTP server.
 */
public class PragmaLogHttpFilter {

    /** Runs before the application's own filters. */
    static final int PRIORITY = 1_000;

    private static final Logger requestLog = LoggerFactory.getLogger(HttpLogContext.COMPLETION_LOGGER);

    void register(@Observes Filters filters, PragmaLogConfig config) {
        PragmaLogConfig.Http http = config.http();
        if (config.enabled() && http.enabled()) {
            HttpLogSettings settings = new HttpLogSettings(http.requestIdHeader(), http.headers().orElse(List.of()),
                    http.excludeHeaders(), http.queryParams().orElse(List.of()));
            filters.register(context -> handle(context, http, settings), PRIORITY);
        }
    }

    private static void handle(RoutingContext routing, PragmaLogConfig.Http http, HttpLogSettings settings) {
        long start = System.nanoTime();
        HttpServerRequest request = routing.request();
        Map<String, String> context = HttpLogContext.build(settings, request.method().name(), request.path(),
                request::getHeader, () -> request.headers().names(), request::getParam,
                () -> HttpLogContext.queryParameters(request.query()).keySet());
        routing.response().putHeader(http.requestIdHeader(), context.get(HttpLogContext.REQUEST_ID));
        context.forEach(MDC::put);
        PragmaLogConfig.RequestLog log = http.requestLog();
        if (log.enabled() && requestLog.isInfoEnabled()
                && log.excludePaths().stream().noneMatch(pattern -> PathPattern.matches(pattern, request.path()))) {
            if (log.entry()) {
                requestLog.info(HttpLogContext.entryMessage(context));
            }
            routing.addEndHandler(done -> writeCompletion(context,
                    done.succeeded() ? routing.response().getStatusCode() : null, start,
                    PragmaLogBodyCapture.captured(routing)));
        }
        routing.next();
    }

    private static void writeCompletion(Map<String, String> context, Integer status, long start,
            Map<String, Object> bodies) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        context.forEach(MDC::put);
        LoggingEventBuilder event = requestLog.atInfo();
        if (status != null) {
            event = event.addKeyValue(HttpLogContext.HTTP_STATUS, status);
        }
        event = event.addKeyValue(HttpLogContext.HTTP_DURATION_MS, durationMs);
        for (Map.Entry<String, Object> body : bodies.entrySet()) {
            event = event.addKeyValue(body.getKey(), body.getValue());
        }
        event.log(HttpLogContext.completionMessage(context, status, durationMs));
    }
}
