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

    @Test
    @DisplayName("* copies every header but the excluded ones; explicit headers are always copied (ENR-03)")
    void allHeaders() {
        Map<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put("X-Request-Id", "req-1");
        headers.put("X-Client-Id", "web");
        headers.put("Cookie", "c");
        headers.put("Authorization", "Bearer x");
        HttpLogSettings settings = new HttpLogSettings("X-Request-Id", List.of("*", "Authorization"),
                HttpLogSettings.DEFAULT_EXCLUDED_HEADERS, List.of());

        Map<String, String> context = HttpLogContext.build(settings, "GET", "/", headers::get, headers::keySet,
                name -> null, List::of);

        assertThat(context).containsKeys("http.header.x-client-id", "http.header.x-request-id",
                "http.header.authorization").doesNotContainKey("http.header.cookie");
        assertThat(HttpLogContext.isContextKey("http.header.cookie")).isTrue();
    }

    @Test
    @DisplayName("allow-listed or all query parameters, read from the URL only (ENR-02)")
    void queryParameters() {
        Map<String, String> query = HttpLogContext.queryParameters("page=2&size=10&q=caf%C3%A9+con+leche&page=9&flag&bad=%ZZ");
        assertThat(query).containsExactly(Map.entry("page", "2"), Map.entry("size", "10"),
                Map.entry("q", "café con leche"), Map.entry("flag", ""));
        assertThat(HttpLogContext.queryParameters(null)).isEmpty();

        HttpLogSettings some = new HttpLogSettings("X-Request-Id", List.of(), List.of(), List.of("page", "missing"));
        HttpLogSettings all = new HttpLogSettings("X-Request-Id", List.of(), List.of(), List.of("*"));
        assertThat(HttpLogContext.build(some, "GET", "/", name -> null, List::of, query::get, query::keySet))
                .containsEntry("http.query.page", "2").doesNotContainKey("http.query.size");
        assertThat(HttpLogContext.build(all, "GET", "/", name -> null, List::of, query::get, query::keySet))
                .containsKeys("http.query.page", "http.query.size", "http.query.q", "http.query.flag");
        assertThat(HttpLogContext.isContextKey("http.query.page")).isTrue();
    }

    @Test
    @DisplayName("start log message (ENR-08)")
    void entryMessage() {
        assertThat(HttpLogContext.entryMessage(Map.of("http.method", "POST", "http.path", "/orders")))
                .isEqualTo("HTTP POST /orders started");
    }
}
