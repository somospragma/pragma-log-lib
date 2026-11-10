package co.com.pragma.logging.quarkus;

import co.com.pragma.logging.core.http.HttpBodyCapture;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Captures request and response bodies of Quarkus REST endpoints for the completion log (ENR-04), when
 * {@code pragma.log.http.body.enabled} is true. The request body is read once and handed back to the endpoint
 * unchanged; JSON and form bodies become objects masked by field name. The response is the entity the endpoint
 * returned, masked like any logged object, so {@code @MaskedLogField} applies to it.
 */
@Provider
@Priority(Priorities.USER - 100)
public class PragmaLogBodyProvider implements ReaderInterceptor, ContainerResponseFilter {

    /** Looked up on first use: Quarkus REST creates providers before the runtime configuration exists. */
    @Inject
    Instance<PragmaLogConfig> config;

    @Inject
    CurrentVertxRequest request;

    private volatile HttpBodyCapture capture;

    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException {
        HttpBodyCapture bodies = capture();
        MediaType type = context.getMediaType();
        if (bodies.request() && type != null && bodies.captures(type.toString())) {
            byte[] body = context.getInputStream().readAllBytes();
            context.setInputStream(new ByteArrayInputStream(body));
            PragmaLogBodyCapture.request(request.getCurrent(), bodies.value(body, body.length, type.toString()));
        }
        return context.proceed();
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        HttpBodyCapture bodies = capture();
        MediaType type = response.getMediaType();
        if (!bodies.response() || !response.hasEntity() || type == null || !bodies.captures(type.toString())) {
            return;
        }
        Object entity = response.getEntity();
        if (entity instanceof CharSequence text) {
            byte[] bytes = text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            PragmaLogBodyCapture.response(this.request.getCurrent(), bodies.value(bytes, bytes.length, type.toString()));
        } else if (!(entity instanceof byte[]) && !(entity instanceof java.io.InputStream)) {
            PragmaLogBodyCapture.response(this.request.getCurrent(), entity);
        }
    }

    private HttpBodyCapture capture() {
        HttpBodyCapture current = capture;
        if (current == null) {
            PragmaLogConfig settings = config.get();
            PragmaLogConfig.Body body = settings.http().body();
            current = settings.enabled() && settings.http().enabled() && body.enabled()
                    ? new HttpBodyCapture(body.request(), body.response(), Math.toIntExact(body.maxSize().asLongValue()),
                            body.contentTypes())
                    : HttpBodyCapture.none();
            capture = current;
        }
        return current;
    }
}
