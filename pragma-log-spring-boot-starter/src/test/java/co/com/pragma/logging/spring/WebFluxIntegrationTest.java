package co.com.pragma.logging.spring;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.pragma.logging.core.enrich.LogEnrichment;
import co.com.pragma.logging.core.enrich.LogField;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
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

        @PostMapping("/payments")
        Mono<Map<String, Object>> pay(@RequestBody Mono<Map<String, Object>> payment) {
            return payment.map(body -> Map.<String, Object>of("status", "paid", "token", "t-987654"));
        }
    }

    /** {@code @LogEnrichment} on a method returning a Mono: the fields reach operators that run later (ENR-05). */
    static class RefundService {
        private static final Logger log = LoggerFactory.getLogger(RefundService.class);

        @LogEnrichment("operation=refund")
        public Mono<String> refund(@LogField("paymentId") String paymentId) {
            return Mono.just(paymentId)
                    .delayElement(Duration.ofMillis(20))
                    .map(value -> {
                        log.info("Refund done {}", value);
                        return value;
                    });
        }
    }

    @RestController
    static class RefundController {
        private final RefundService refunds;

        RefundController(RefundService refunds) {
            this.refunds = refunds;
        }

        @GetMapping("/refunds/{id}")
        Mono<String> refund(@PathVariable("id") String id) {
            return refunds.refund(id).doOnNext(value -> LoggerFactory.getLogger(RefundController.class)
                    .info("Refund answered {}", value));
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({OrderController.class, RefundController.class})
    static class TestApp {

        @Bean
        RefundService refundService() {
            return new RefundService();
        }
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

    @Test
    @DisplayName("request and response bodies are captured as they flow and masked (ENR-04)")
    void bodies(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = start("pragma.log.http.body.enabled=true",
                "pragma.log.http.request-log.entry=true")) {
            int port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/payments"))
                    .header("Content-Type", "application/json")
                    .header("X-Request-Id", "req-p")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"amount\":5,\"cardNumber\":\"4111111111111111\"}"))
                    .build();
            HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(jsonLines(output, "HTTP POST /payments started").get(0).get("requestId").asString())
                    .isEqualTo("req-p");
            JsonNode done = jsonLines(output, "HTTP POST /payments 200").get(0);
            assertThat(done.get("http.request.body").get("amount").asInt()).isEqualTo(5);
            assertThat(done.get("http.request.body").get("cardNumber").asString()).matches("\\*+");
            assertThat(done.get("http.response.body").get("status").asString()).isEqualTo("paid");
            assertThat(done.get("http.response.body").get("token").asString()).matches("\\*+");
            assertThat(output.getOut()).doesNotContain("4111111111111111").doesNotContain("t-987654");
        }
    }

    @Test
    @DisplayName("@LogEnrichment fields follow a Mono to other threads and do not leak afterwards (ENR-05)")
    void enrichmentFollowsMono(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = start()) {
            getPath(context, "/refunds/r-9", "req-r").join();

            JsonNode done = jsonLines(output, "Refund done r-9").get(0);
            assertThat(done.get("operation").asString()).isEqualTo("refund");
            assertThat(done.get("paymentId").asString()).isEqualTo("r-9");
            assertThat(done.get("requestId").asString()).isEqualTo("req-r");
            assertThat(done.get("thread").asString()).startsWith("parallel-");
            JsonNode answered = jsonLines(output, "Refund answered r-9").get(0);
            assertThat(answered.has("operation")).isFalse();
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

    /** Waits a little: the request completion log is written right after the response is sent. */
    private static List<JsonNode> jsonLines(CapturedOutput output, String messagePrefix) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            List<JsonNode> lines = output.getOut().lines()
                    .filter(line -> line.startsWith("{"))
                    .map(line -> JsonMapper.shared().readTree(line))
                    .filter(json -> json.path("message").asString("").startsWith(messagePrefix))
                    .toList();
            if (!lines.isEmpty()) {
                return lines;
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("No JSON log starting with '" + messagePrefix + "' in:\n" + output.getOut());
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
    }
}
