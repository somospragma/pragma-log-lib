package co.com.pragma.logging.spring;

import java.util.Map;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Spring WebFlux: puts the {@link RequestLogContext} in the Reactor {@code Context}. With automatic context
 * propagation, {@link PragmaMdcThreadLocalAccessor} copies it to the MDC on every thread that handles the
 * request, so plain {@code log.info(...)} calls inside operators carry it (CTX-05). No ThreadLocal is set
 * here and nothing blocks.
 */
public class PragmaLogContextWebFilter implements WebFilter {

    private final PragmaLogProperties.Http properties;

    public PragmaLogContextWebFilter(PragmaLogProperties.Http properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        Map<String, String> context = RequestLogContext.build(properties, request.getMethod().name(),
                request.getPath().value(), name -> request.getHeaders().getFirst(name));
        exchange.getResponse().getHeaders()
                .set(properties.getRequestIdHeader(), context.get(RequestLogContext.REQUEST_ID));
        return chain.filter(exchange)
                .contextWrite(reactorContext -> reactorContext.put(PragmaMdcThreadLocalAccessor.KEY, context));
    }
}
