package co.com.pragma.logging.core.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HttpLogContextTest {

    @Test
    @DisplayName("builds the request context with allow-listed headers only")
    void buildsContext() {
        Map<String, String> headers = Map.of("X-Request-Id", "req-1", "X-Client-Id", "web", "Cookie", "c");

        Map<String, String> context = HttpLogContext.build("X-Request-Id", List.of("X-Client-Id", "X-Missing"),
                "GET", "/orders", headers::get);

        assertThat(context).containsExactly(Map.entry("requestId", "req-1"), Map.entry("http.method", "GET"),
                Map.entry("http.path", "/orders"), Map.entry("http.header.x-client-id", "web"));
    }

    @Test
    @DisplayName("unsafe request ids are replaced by a UUID")
    void unsafeRequestId() {
        Map<String, String> context = HttpLogContext.build("X-Request-Id", List.of(), "GET", "/",
                name -> "bad\" id");

        assertThat(context.get("requestId")).matches("[0-9a-f-]{36}");
    }

    @Test
    @DisplayName("completion message with and without status")
    void completionMessage() {
        Map<String, String> context = Map.of("http.method", "GET", "http.path", "/orders/7");

        assertThat(HttpLogContext.completionMessage(context, 200, 12)).isEqualTo("HTTP GET /orders/7 200 in 12 ms");
        assertThat(HttpLogContext.completionMessage(context, null, 5)).isEqualTo("HTTP GET /orders/7 cancelled after 5 ms");
    }
}
