package co.com.pragma.logging.spring;

import co.com.pragma.logging.core.http.HttpBodyCapture;
import co.com.pragma.logging.core.http.HttpLogContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Spring WebFlux: puts the {@link RequestLogContext} in the Reactor {@code Context}. With automatic context
 * propagation, {@link PragmaMdcThreadLocalAccessor} copies it to the MDC on every thread that handles the
 * request, so plain {@code log.info(...)} calls inside operators carry it (CTX-05). No ThreadLocal is set
 * here and nothing blocks. Writes the {@link RequestCompletionLog} when the request ends (ENR-01), with the
 * request and response bodies when enabled (ENR-04): their first bytes are copied as they flow, nothing is read
 * ahead or buffered.
 */
public class PragmaLogContextWebFilter implements WebFilter {

    private final PragmaLogProperties.Http properties;

    /** Reads the properties on every request, so changes applied by a refresh take effect (CFG-06). */
    public PragmaLogContextWebFilter(PragmaLogProperties.Http properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long start = System.nanoTime();
        RequestCompletionLog completionLog = new RequestCompletionLog(properties.getRequestLog());
        HttpBodyCapture bodies = properties.getBody().capture();
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        Map<String, String> context = RequestLogContext.build(properties, request.getMethod().name(), path,
                name -> request.getHeaders().getFirst(name), () -> request.getHeaders().toSingleValueMap().keySet(),
                request.getURI().getRawQuery());
        exchange.getResponse().getHeaders()
                .set(properties.getRequestIdHeader(), context.get(RequestLogContext.REQUEST_ID));

        if (!completionLog.appliesTo(path)) {
            return chain.filter(exchange).contextWrite(reactorContext ->
                    reactorContext.put(PragmaMdcThreadLocalAccessor.KEY, context));
        }
        completionLog.writeEntry(context);
        BodyBuffer requestBody = bodies.request() && bodies.captures(contentType(request.getHeaders()))
                ? new BodyBuffer(bodies.maxSize())
                : null;
        BodyBuffer responseBody = bodies.enabled() && bodies.response() ? new BodyBuffer(bodies.maxSize()) : null;
        ResponseBodyObject responseObject = responseBody != null ? new ResponseBodyObject() : null;
        if (responseObject != null) {
            exchange.getAttributes().put(ResponseBodyObject.ATTRIBUTE, responseObject);
        }
        ServerWebExchange captured = requestBody == null && responseBody == null
                ? exchange
                : exchange.mutate()
                        .request(requestBody == null ? request : capture(request, requestBody))
                        .response(responseBody == null ? exchange.getResponse()
                                : capture(exchange.getResponse(), responseBody))
                        .build();

        Mono<Void> handled = chain.filter(captured)
                .doOnSuccess(done -> completionLog.write(context, status(exchange), start,
                        bodies(bodies, exchange, requestBody, responseBody, responseObject)))
                // Error handlers set the final status after the filters: take it from the exception.
                .doOnError(error -> completionLog.write(context, status(error), start,
                        bodies(bodies, exchange, requestBody, null, null), error))
                .doOnCancel(() -> completionLog.write(context, null, start));
        return handled.contextWrite(reactorContext -> reactorContext.put(PragmaMdcThreadLocalAccessor.KEY, context));
    }

    private static ServerHttpRequest capture(ServerHttpRequest request, BodyBuffer buffer) {
        return new ServerHttpRequestDecorator(request) {
            @Override
            public Flux<DataBuffer> getBody() {
                return super.getBody().doOnNext(data -> copy(data, buffer));
            }
        };
    }

    private static ServerHttpResponse capture(ServerHttpResponse response, BodyBuffer buffer) {
        return new ServerHttpResponseDecorator(response) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                return super.writeWith(Flux.from(body).doOnNext(data -> copy(data, buffer)));
            }
        };
    }

    /** Copies the readable bytes without moving the buffer's read position. */
    private static void copy(DataBuffer data, BodyBuffer buffer) {
        byte[] bytes = new byte[data.readableByteCount()];
        int position = data.readPosition();
        data.read(bytes);
        data.readPosition(position);
        buffer.write(bytes, 0, bytes.length);
    }

    private static Map<String, Object> bodies(HttpBodyCapture bodies, ServerWebExchange exchange,
            BodyBuffer requestBody, BodyBuffer responseBody, ResponseBodyObject responseObject) {
        Map<String, Object> captured = new LinkedHashMap<>();
        if (requestBody != null) {
            putIfPresent(captured, HttpLogContext.REQUEST_BODY, bodies.value(requestBody.bytes(), requestBody.total(),
                    contentType(exchange.getRequest().getHeaders())));
        }
        String responseType = contentType(exchange.getResponse().getHeaders());
        if (responseBody != null && bodies.captures(responseType)) {
            Object object = responseObject != null ? responseObject.value() : null;
            // The returned object is masked by its annotations; its bytes only by field names and patterns.
            putIfPresent(captured, HttpLogContext.RESPONSE_BODY, object != null ? object
                    : bodies.value(responseBody.bytes(), responseBody.total(), responseType));
        }
        return captured;
    }

    private static String contentType(HttpHeaders headers) {
        return headers.getFirst(HttpHeaders.CONTENT_TYPE);
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static int status(ServerWebExchange exchange) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        return status == null ? 200 : status.value();
    }

    private static int status(Throwable error) {
        return error instanceof ErrorResponse response ? response.getStatusCode().value() : 500;
    }
}
