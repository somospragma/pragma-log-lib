package co.com.pragma.logging.spring;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring MVC: adds the {@link RequestLogContext} to the MDC of every log written while a request is
 * handled (CTX-04), and writes the {@link RequestCompletionLog} when it ends (ENR-01). Only its own MDC keys
 * are touched, and their previous values are restored (CTX-07).
 */
public class PragmaLogContextFilter extends OncePerRequestFilter {

    private final PragmaLogProperties.Http properties;
    private final RequestCompletionLog completionLog;

    public PragmaLogContextFilter(PragmaLogProperties.Http properties) {
        this.properties = properties;
        this.completionLog = new RequestCompletionLog(properties.getRequestLog());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        Map<String, String> context = RequestLogContext.build(properties, request.getMethod(),
                request.getRequestURI(), request::getHeader);
        response.setHeader(properties.getRequestIdHeader(), context.get(RequestLogContext.REQUEST_ID));

        Map<String, String> previous = RequestLogContext.putInMdc(context);
        boolean failed = true;
        try {
            chain.doFilter(request, response);
            failed = false;
        } finally {
            try {
                logCompletion(request, response, context, start, failed);
            } finally {
                RequestLogContext.restoreMdc(previous);
            }
        }
    }

    private void logCompletion(HttpServletRequest request, HttpServletResponse response, Map<String, String> context,
            long start, boolean failed) {
        if (!completionLog.appliesTo(request.getRequestURI())) {
            return;
        }
        if (request.isAsyncStarted()) {
            // DeferredResult, Callable, streaming: the status is known only when the async processing ends.
            request.getAsyncContext().addListener(new AsyncListener() {
                @Override
                public void onComplete(AsyncEvent event) {
                    completionLog.write(context, response.getStatus(), start);
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
            completionLog.write(context, status, start);
        }
    }
}
