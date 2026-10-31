package co.com.pragma.logging.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Starts a real Spring WebFlux application and checks the request context reaches logs on every thread. */
@ExtendWith(OutputCaptureExtension.class)
class WebFluxIntegrationTest {

    @RestController
    static class OrderController {
        private static final Logger log = LoggerFactory.getLogger(OrderController.class);

        @GetMapping("/orders/{id}")
        Mono<String> find(@PathVariable("id") String id) {
            log.info("Handling order {}", id);
            return Mono.just(id)
                    .delayElement(Duration.ofMillis(20)) // continues on a Reactor "parallel" thread
                    .map(value -> {
                        log.info("Order loaded {}", value);
                        return value;
                    });
        }

        @GetMapping("/missing-order")
        Mono<String> missing() {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
        }

        @GetMapping("/failing-order")
        Mono<String> failing() {
            return Mono.delay(Duration.ofMillis(5)).then(Mono.error(new IllegalStateException("boom")));
        }

        @GetMapping("/users/{id}")
        Mono<String> user(@PathVariable("id") String id) {
            return Mono.just(id)
                    .delayElement(Duration.ofMillis(20))
                    .map(value -> {
                        log.info("User loaded {}", value);
                        return value;
                    })
                    .contextWrite(Context.of("userId", "user-" + id, "tenantId", 42));
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(OrderController.class)
    static class TestApp {
    }

    @AfterEach
    void clearLoggingSystemProperties() {
        System.clearProperty("CONSOLE_LOG_STRUCTURED_FORMAT");
        System.clearProperty("FILE_LOG_STRUCTURED_FORMAT");
    }

    @Test
    @DisplayName("the request context reaches logs written on other Reactor threads (CTX-05)")
    void contextFollowsThreadHops(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = start()) {

            HttpResponse<String> response = get(context, "7", "req-7").join();

            assertThat(response.headers().firstValue("X-Request-Id")).hasValue("req-7");
            JsonNode handling = jsonLines(output, "Handling order 7").get(0);
            JsonNode loaded = jsonLines(output, "Order loaded 7").get(0);
            assertThat(handling.get("requestId").asString()).isEqualTo("req-7");
            assertThat(loaded.get("requestId").asString()).isEqualTo("req-7");
            assertThat(loaded.get("http.path").asString()).isEqualTo("/orders/7");
            assertThat(loaded.get("thread").asString()).startsWith("parallel-");
        }
    }

    @Test
    @DisplayName("concurrent requests never mix their contexts")
    void noContextBleedUnderConcurrency(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = start()) {

            List<CompletableFuture<HttpResponse<String>>> calls = IntStream.range(0, 30)
                    .mapToObj(i -> get(context, String.valueOf(i), "req-" + i))
                    .toList();
            calls.forEach(CompletableFuture::join);

            for (int i = 0; i < 30; i++) {
                for (JsonNode log : jsonLines(output, "Order loaded " + i)) {
                    if (log.get("message").asString().equals("Order loaded " + i)) {
                        assertThat(log.get("requestId").asString()).isEqualTo("req-" + i);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("logs outside a request do not inherit a previous request context")
    void noLeakAfterRequest(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = start()) {
            get(context, "1", "req-1").join();

            Mono.delay(Duration.ofMillis(10))
                    .doOnNext(tick -> LoggerFactory.getLogger(WebFluxIntegrationTest.class).info("Background task"))
                    .block();

            assertThat(jsonLines(output, "Background task").get(0).has("requestId")).isFalse();
        }
    }

    @Test
    @DisplayName("application values in the Reactor Context reach the logs on any thread (CTX-08)")
    void contextKeysFollowThreadHops(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = start("pragma.log.context-keys=userId,tenantId")) {

            List<CompletableFuture<HttpResponse<String>>> calls = IntStream.range(0, 20)
                    .mapToObj(i -> getPath(context, "/users/" + i, "req-u" + i))
                    .toList();
            calls.forEach(CompletableFuture::join);

            for (int i = 0; i < 20; i++) {
                String message = "User loaded " + i;
                JsonNode log = jsonLines(output, message).stream()
                        .filter(json -> json.get("message").asString().equals(message))
                        .findFirst().orElseThrow();
                assertThat(log.get("userId").asString()).isEqualTo("user-" + i);
                assertThat(log.get("tenantId").asString()).isEqualTo("42");
                assertThat(log.get("requestId").asString()).isEqualTo("req-u" + i);
                assertThat(log.get("thread").asString()).startsWith("parallel-");
            }
        }
    }

    @Test
    @DisplayName("context keys work outside HTTP requests and do not leak afterwards")
    void contextKeysOutsideRequests(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = start("pragma.log.context-keys=userId")) {
            assertThat(context.getBean(PragmaContextKeysRegistrar.class)).isNotNull();
            Logger log = LoggerFactory.getLogger(WebFluxIntegrationTest.class);

            Mono.delay(Duration.ofMillis(10))
                    .doOnNext(tick -> log.info("Batch step"))
                    .contextWrite(Context.of("userId", "batch-user"))
                    .block();
            Mono.delay(Duration.ofMillis(10)).doOnNext(tick -> log.info("Unrelated step")).block();

            assertThat(jsonLines(output, "Batch step").get(0).get("userId").asString()).isEqualTo("batch-user");
            assertThat(jsonLines(output, "Unrelated step").get(0).has("userId")).isFalse();
        }
    }

    @Test
    @DisplayName("a context key reserved by pragma-log stops startup with a clear message")
    void reservedContextKeyFailsFast() {
        assertThatThrownBy(() -> start("pragma.log.context-keys=requestId").close())
                .hasStackTraceContaining("Invalid pragma.log.context-keys entry 'requestId'");
    }

    @Test
    @DisplayName("each request ends with one log carrying status and duration (ENR-01)")
    void requestCompletionLog(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = start()) {

            get(context, "3", "req-3").join();
            getPath(context, "/missing-order", "req-4").join();
            getPath(context, "/failing-order", "req-5").join();

            JsonNode ok = jsonLines(output, "HTTP GET /orders/3 200").get(0);
            assertThat(ok.get("http.status").asInt()).isEqualTo(200);
            assertThat(ok.get("http.durationMs").asLong()).isGreaterThanOrEqualTo(20);
            assertThat(ok.get("requestId").asString()).isEqualTo("req-3");
            assertThat(jsonLines(output, "HTTP GET /missing-order ").get(0).get("http.status").asInt()).isEqualTo(404);
            JsonNode failed = jsonLines(output, "HTTP GET /failing-order ").get(0);
            assertThat(failed.get("http.status").asInt()).isEqualTo(500);
            assertThat(failed.get("requestId").asString()).isEqualTo("req-5");
        }
    }

    private static ConfigurableApplicationContext start(String... extraProperties) {
        return new SpringApplicationBuilder(TestApp.class)
                .properties("server.port=0", "spring.application.name=orders-service", "spring.main.banner-mode=off",
                        "spring.main.web-application-type=reactive")
                .properties(extraProperties)
                .run();
    }

    private static CompletableFuture<HttpResponse<String>> get(ConfigurableApplicationContext context, String id,
            String requestId) {
        return getPath(context, "/orders/" + id, requestId);
    }

    private static CompletableFuture<HttpResponse<String>> getPath(ConfigurableApplicationContext context,
            String path, String requestId) {
        int port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("X-Request-Id", requestId)
                .build();
        return HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    private static List<JsonNode> jsonLines(CapturedOutput output, String messagePrefix) {
        List<JsonNode> lines = output.getOut().lines()
                .filter(line -> line.startsWith("{"))
                .map(line -> JsonMapper.shared().readTree(line))
                .filter(json -> json.path("message").asString("").startsWith(messagePrefix))
                .toList();
        if (lines.isEmpty()) {
            throw new AssertionError("No JSON log starting with '" + messagePrefix + "' in:\n" + output.getOut());
        }
        return lines;
    }
}
