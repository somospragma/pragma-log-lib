package co.com.pragma.logging.spring;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PragmaLogContextFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("context is available during the request and previous MDC values are restored (CTX-07)")
    void restoresMdc() throws Exception {
        PragmaLogProperties.Http properties = new PragmaLogProperties.Http();
        properties.setHeaders(List.of("X-Client-Id"));
        PragmaLogContextFilter filter = new PragmaLogContextFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        request.addHeader("X-Request-Id", "req-7");
        request.addHeader("X-Client-Id", "web");
        MDC.put("requestId", "outer");
        MDC.put("tenant", "acme");
        Map<String, String> seen = new HashMap<>();
        FilterChain chain = (req, res) -> seen.putAll(MDC.getCopyOfContextMap());

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(seen).containsEntry("requestId", "req-7")
                .containsEntry("http.method", "POST")
                .containsEntry("http.path", "/orders")
                .containsEntry("http.header.x-client-id", "web")
                .containsEntry("tenant", "acme");
        assertThat(MDC.getCopyOfContextMap()).containsOnly(Map.entry("requestId", "outer"), Map.entry("tenant", "acme"));
    }
}
