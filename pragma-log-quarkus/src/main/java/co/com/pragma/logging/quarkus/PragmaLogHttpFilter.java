package co.com.pragma.logging.quarkus;

import co.com.pragma.logging.core.http.HttpLogContext;
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
 * Adds the {@link HttpLogContext} to the MDC of every log written while a request is handled (CTX-06) and
 * writes the completion log with status and duration (ENR-01). Quarkus keeps the MDC in the Vert.x
 * duplicated context of the request, so it follows the request across event-loop and worker threads and
 * is gone when the request ends. Registered only when the application has an HTTP server.
 */
public class PragmaLogHttpFilter {

    /** Runs before the application's own filters. */
    static final int PRIORITY = 1_000;

    private static final Logger completionLog = LoggerFactory.getLogger(HttpLogContext.COMPLETION_LOGGER);

    void register(@Observes Filters filters, PragmaLogConfig config) {
        PragmaLogConfig.Http http = config.http();
        if (config.enabled() && http.enabled()) {
            filters.register(context -> handle(context, http), PRIORITY);
        }
    }

    private static void handle(RoutingContext routing, PragmaLogConfig.Http http) {
        long start = System.nanoTime();
        HttpServerRequest request = routing.request();
        Map<String, String> context = HttpLogContext.build(http.requestIdHeader(), http.headers().orElse(List.of()),
                request.method().name(), request.path(), request::getHeader);
        routing.response().putHeader(http.requestIdHeader(), context.get(HttpLogContext.REQUEST_ID));
        context.forEach(MDC::put);
        PragmaLogConfig.RequestLog requestLog = http.requestLog();
        if (requestLog.enabled() && completionLog.isInfoEnabled()
                && requestLog.excludePaths().stream().noneMatch(pattern -> PathPattern.matches(pattern, request.path()))) {
            routing.addEndHandler(done -> writeCompletion(context,
                    done.succeeded() ? routing.response().getStatusCode() : null, start));
        }
        routing.next();
    }

    private static void writeCompletion(Map<String, String> context, Integer status, long start) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        context.forEach(MDC::put);
        LoggingEventBuilder event = completionLog.atInfo();
        if (status != null) {
            event = event.addKeyValue(HttpLogContext.HTTP_STATUS, status);
        }
        event.addKeyValue(HttpLogContext.HTTP_DURATION_MS, durationMs)
                .log(HttpLogContext.completionMessage(context, status, durationMs));
    }
}
