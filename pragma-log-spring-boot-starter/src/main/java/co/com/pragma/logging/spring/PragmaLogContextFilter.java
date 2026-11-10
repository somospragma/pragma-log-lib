package co.com.pragma.logging.spring;

import co.com.pragma.logging.core.http.HttpBodyCapture;
import co.com.pragma.logging.core.http.HttpLogContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring MVC: adds the {@link RequestLogContext} to the MDC of every log written while a request is
 * handled (CTX-04), and writes the {@link RequestCompletionLog} when it ends (ENR-01), with the request and
 * response bodies when enabled (ENR-04). Only its own MDC keys are touched, and their previous values are restored
 * (CTX-07).
 */
public class PragmaLogContextFilter extends OncePerRequestFilter {

    private final PragmaLogProperties.Http properties;
    private final RequestCompletionLog completionLog;
    private final HttpBodyCapture bodies;

    public PragmaLogContextFilter(PragmaLogProperties.Http properties) {
        this.properties = properties;
        this.completionLog = new RequestCompletionLog(properties.getRequestLog());
        this.bodies = properties.getBody().capture();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        Map<String, String> context = RequestLogContext.build(properties, request.getMethod(),
                request.getRequestURI(), request::getHeader, () -> Collections.list(request.getHeaderNames()),
                request.getQueryString());
        response.setHeader(properties.getRequestIdHeader(), context.get(RequestLogContext.REQUEST_ID));
        boolean logged = completionLog.appliesTo(request.getRequestURI());
        if (logged) {
            completionLog.writeEntry(context);
        }
        CapturingRequestWrapper capturedRequest = logged && bodies.request() && bodies.captures(request.getContentType())
                ? new CapturingRequestWrapper(request, bodies.maxSize())
                : null;
        CapturingResponseWrapper capturedResponse = logged && bodies.enabled() && bodies.response()
                ? new CapturingResponseWrapper(response, bodies.maxSize())
                : null;

        Map<String, String> previous = RequestLogContext.putInMdc(context);
        boolean failed = true;
        try {
            chain.doFilter(capturedRequest != null ? capturedRequest : request,
                    capturedResponse != null ? capturedResponse : response);
            failed = false;
        } finally {
            try {
                if (logged) {
                    logCompletion(request, response, context, start, failed, capturedRequest, capturedResponse);
                }
            } finally {
                RequestLogContext.restoreMdc(previous);
            }
        }
    }

    private void logCompletion(HttpServletRequest request, HttpServletResponse response, Map<String, String> context,
            long start, boolean failed, CapturingRequestWrapper capturedRequest,
            CapturingResponseWrapper capturedResponse) {
        if (request.isAsyncStarted()) {
            // DeferredResult, Callable, streaming: the status is known only when the async processing ends.
            request.getAsyncContext().addListener(new AsyncListener() {
                @Override
                public void onComplete(AsyncEvent event) {
                    completionLog.write(context, response.getStatus(), start,
                            bodies(response, capturedRequest, capturedResponse));
                }

                @Override
                public void onTimeout(AsyncEvent event) {
                }

                @Override
                public void onError(AsyncEvent event) {
                }

                @Override
                public void onStartAsync(AsyncEvent event) {
                }
            });
        } else {
            // An exception that escapes the filter chain becomes a 500 in the error page, after this filter.
            int status = failed && response.getStatus() < 500 ? 500 : response.getStatus();
            completionLog.write(context, status, start, bodies(response, capturedRequest, capturedResponse));
        }
    }

    /** The captured bodies, as key-values the encoder masks; empty when nothing was captured. */
    private Map<String, Object> bodies(HttpServletResponse response, CapturingRequestWrapper capturedRequest,
            CapturingResponseWrapper capturedResponse) {
        Map<String, Object> captured = new LinkedHashMap<>();
        if (capturedRequest != null) {
            BodyBuffer body = capturedRequest.body();
            putIfPresent(captured, HttpLogContext.REQUEST_BODY,
                    bodies.value(body.bytes(), body.total(), capturedRequest.getContentType()));
        }
        if (capturedResponse != null && bodies.captures(response.getContentType())) {
            capturedResponse.flushWriter();
            BodyBuffer body = capturedResponse.body();
            putIfPresent(captured, HttpLogContext.RESPONSE_BODY,
                    bodies.value(body.bytes(), body.total(), response.getContentType()));
        }
        return captured;
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
