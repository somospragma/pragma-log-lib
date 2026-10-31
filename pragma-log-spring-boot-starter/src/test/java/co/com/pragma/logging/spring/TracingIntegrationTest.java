package co.com.pragma.logging.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * With Micrometer Tracing on the classpath, traceId and spanId reach the JSON logs without configuration
 * (CTX-01), in Spring MVC and across Reactor threads in WebFlux.
 */
@ExtendWith(OutputCaptureExtension.class)
class TracingIntegrationTest {

    @RestController
    static class MvcController {
        private static final Logger log = LoggerFactory.getLogger(MvcController.class);

        @GetMapping("/traced")
        String traced() {
            log.info("Traced MVC call");
            return "ok";
        }
    }

    @RestController
    static class ReactiveController {
        private static final Logger log = LoggerFactory.getLogger(ReactiveController.class);

        @GetMapping("/traced")
        Mono<String> traced() {
            return Mono.just("ok")
                    .delayElement(Duration.ofMillis(20))
                    .doOnNext(value -> log.info("Traced reactive call"));
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(MvcController.class)
    static class MvcApp {
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(ReactiveController.class)
    static class ReactiveApp {
    }

    @AfterEach
    void clearLoggingSystemProperties() {
        System.clearProperty("CONSOLE_LOG_STRUCTURED_FORMAT");
        System.clearProperty("FILE_LOG_STRUCTURED_FORMAT");
    }

    @Test
    @DisplayName("Spring MVC logs carry traceId and spanId")
    void mvc(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = start(MvcApp.class, "servlet")) {
            get(context, "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

            JsonNode log = jsonLine(output, "Traced MVC call");
            assertThat(log.get("traceId").asString()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
            assertThat(log.get("spanId").asString()).matches("[0-9a-f]{16}");
            assertThat(jsonLine(output, "HTTP GET /traced").get("traceId").asString())
                    .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        }
    }

    @Test
    @DisplayName("WebFlux logs carry traceId and spanId on other Reactor threads")
    void webflux(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = start(ReactiveApp.class, "reactive")) {
            get(context, "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");

            JsonNode log = jsonLine(output, "Traced reactive call");
            assertThat(log.get("thread").asString()).startsWith("parallel-");
            assertThat(log.get("traceId").asString()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
            assertThat(log.get("spanId").asString()).matches("[0-9a-f]{16}");
        }
    }

    private static ConfigurableApplicationContext start(Class<?> app, String type) {
        return new SpringApplicationBuilder(app)
                .properties("server.port=0", "spring.application.name=orders-service", "spring.main.banner-mode=off",
                        "spring.main.web-application-type=" + type, "management.tracing.sampling.probability=1.0",
                        "management.tracing.propagation.type=w3c")
                .run();
    }

    private static void get(ConfigurableApplicationContext context, String traceparent) throws Exception {
        int port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/traced"))
                .header("traceparent", traceparent)
                .build();
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode jsonLine(CapturedOutput output, String messagePrefix) {
        return output.getOut().lines()
                .filter(line -> line.startsWith("{"))
                .map(line -> JsonMapper.shared().readTree(line))
                .filter(json -> json.path("message").asString("").startsWith(messagePrefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No JSON log starting with '" + messagePrefix + "' in:\n"
                        + output.getOut()));
    }
}
