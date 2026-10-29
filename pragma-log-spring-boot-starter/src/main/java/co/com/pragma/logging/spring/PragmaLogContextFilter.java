package co.com.pragma.logging.spring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds HTTP request context to the MDC of every log written while a request is handled (CTX-02, CTX-04, ENR-01):
 * {@code requestId}, {@code http.method}, {@code http.path} and allow-listed headers.
 *
 * <p>Only its own MDC keys are touched, and their previous values are restored afterwards (CTX-07).
 */
public class PragmaLogContextFilter extends OncePerRequestFilter {

    static final String REQUEST_ID = "requestId";
    static final String HTTP_METHOD = "http.method";
    static final String HTTP_PATH = "http.path";
    static final String HEADER_PREFIX = "http.header.";

    /** Incoming ids are reused only when they are short and safe, to avoid log injection. */
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final PragmaLogProperties.Http properties;

    public PragmaLogContextFilter(PragmaLogProperties.Http properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = requestId(request.getHeader(properties.getRequestIdHeader()));
        response.setHeader(properties.getRequestIdHeader(), requestId);

        Map<String, String> context = new LinkedHashMap<>();
        context.put(REQUEST_ID, requestId);
        context.put(HTTP_METHOD, request.getMethod());
        context.put(HTTP_PATH, request.getRequestURI());
        for (String header : properties.getHeaders()) {
            String value = request.getHeader(header);
            if (value != null) {
                context.put(HEADER_PREFIX + header.toLowerCase(Locale.ROOT), value);
            }
        }

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

    private static String requestId(String incoming) {
        return incoming != null && SAFE_REQUEST_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
    }
}
