package co.com.pragma.logging.spring;

import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Spring WebFlux: puts the {@link RequestLogContext} in the Reactor {@code Context}. With automatic context
 * propagation, {@link PragmaMdcThreadLocalAccessor} copies it to the MDC on every thread that handles the
 * request, so plain {@code log.info(...)} calls inside operators carry it (CTX-05). No ThreadLocal is set
 * here and nothing blocks. Writes the {@link RequestCompletionLog} when the request ends (ENR-01).
 */
public class PragmaLogContextWebFilter implements WebFilter {

    private final PragmaLogProperties.Http properties;
    private final RequestCompletionLog completionLog;

    public PragmaLogContextWebFilter(PragmaLogProperties.Http properties) {
        this.properties = properties;
        this.completionLog = new RequestCompletionLog(properties.getRequestLog());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long start = System.nanoTime();
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        Map<String, String> context = RequestLogContext.build(properties, request.getMethod().name(), path,
                name -> request.getHeaders().getFirst(name));
        exchange.getResponse().getHeaders()
                .set(properties.getRequestIdHeader(), context.get(RequestLogContext.REQUEST_ID));

        Mono<Void> handled = chain.filter(exchange);
        if (completionLog.appliesTo(path)) {
            handled = handled
                    .doOnSuccess(done -> completionLog.write(context, status(exchange), start))
                    // Error handlers set the final status after the filters: take it from the exception.
                    .doOnError(error -> completionLog.write(context, status(error), start))
                    .doOnCancel(() -> completionLog.write(context, null, start));
        }
        return handled.contextWrite(reactorContext -> reactorContext.put(PragmaMdcThreadLocalAccessor.KEY, context));
    }

    private static int status(ServerWebExchange exchange) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        return status == null ? 200 : status.value();
    }

    private static int status(Throwable error) {
        return error instanceof ErrorResponse response ? response.getStatusCode().value() : 500;
    }
}
