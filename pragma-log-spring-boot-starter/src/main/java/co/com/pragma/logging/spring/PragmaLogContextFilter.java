package co.com.pragma.logging.spring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring MVC: adds the {@link RequestLogContext} to the MDC of every log written while a request is
 * handled (CTX-04). Only its own MDC keys are touched, and their previous values are restored (CTX-07).
 */
public class PragmaLogContextFilter extends OncePerRequestFilter {

    private final PragmaLogProperties.Http properties;

    public PragmaLogContextFilter(PragmaLogProperties.Http properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Map<String, String> context = RequestLogContext.build(properties, request.getMethod(),
                request.getRequestURI(), request::getHeader);
        response.setHeader(properties.getRequestIdHeader(), context.get(RequestLogContext.REQUEST_ID));

        Map<String, String> previous = new LinkedHashMap<>();
        context.forEach((key, value) -> {
            previous.put(key, MDC.get(key));
            MDC.put(key, value);
        });
        try {
            chain.doFilter(request, response);
        } finally {
            previous.forEach((key, value) -> {
                if (value == null) {
                    MDC.remove(key);
                } else {
                    MDC.put(key, value);
                }
            });
        }
    }
}
