package co.com.pragma.logging.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.pragma.logging.core.masking.MaskType;
import co.com.pragma.logging.core.masking.MaskedLogField;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Starts a real Spring Boot web application with the starter and inspects what it writes to the console. */
@ExtendWith(OutputCaptureExtension.class)
class StarterIntegrationTest {

    record Customer(String name, @MaskedLogField(type = MaskType.MIDDLE, length = 4) String cardNumber,
            String password) {
    }

    @RestController
    static class LoginController {
        private static final Logger log = LoggerFactory.getLogger(LoginController.class);

        @GetMapping("/login")
        String login() {
            log.info("Login {}", new Customer("Ana", "4111111111111111", "s3cr3t"));
            return "ok";
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(LoginController.class)
    static class TestApp {
    }

    /** Spring Boot keeps the logging format in system properties; several apps share this test JVM. */
    @AfterEach
    void clearLoggingSystemProperties() {
        System.clearProperty("CONSOLE_LOG_STRUCTURED_FORMAT");
        System.clearProperty("FILE_LOG_STRUCTURED_FORMAT");
    }

    @Test
    @DisplayName("logs carry only the Pragma schema, not Logback context properties")
    void noContextNoise(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = start()) {

            get(context, "req-9");

            JsonNode log = jsonLine(output, "Login ");
            assertThat(log.has("PID")).isFalse();
            assertThat(log.has("CONSOLE_LOG_PATTERN")).isFalse();
            assertThat(log.has("tags")).isFalse();
        }
    }

    @Test
    @DisplayName("logs are JSON with masking, app name, fixed fields and HTTP context, without any XML")
    void jsonLogsWithRequestContext(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = start(
                "pragma.log.fields.env=test", "pragma.log.http.headers=X-Client-Id")) {

            HttpResponse<String> response = get(context, "req-42");

            assertThat(response.headers().firstValue("X-Request-Id")).hasValue("req-42");
            JsonNode log = jsonLine(output, "Login ");
            assertThat(log.get("app").asString()).isEqualTo("orders-service");
            assertThat(log.get("env").asString()).isEqualTo("test");
            assertThat(log.get("requestId").asString()).isEqualTo("req-42");
            assertThat(log.get("http.method").asString()).isEqualTo("GET");
            assertThat(log.get("http.path").asString()).isEqualTo("/login");
            assertThat(log.get("http.header.x-client-id").asString()).isEqualTo("mobile");
            assertThat(log.get("message").asString()).contains("4111********1111").contains("\"password\":\"******\"");
            assertThat(output.getOut()).doesNotContain("s3cr3t").doesNotContain("4111111111111111");
        }
    }

    @Test
    @DisplayName("unsafe incoming request ids are replaced to prevent log injection")
    void unsafeRequestIdIsReplaced(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = start()) {

            HttpResponse<String> response = get(context, "bad id\" injected");

            String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();
            assertThat(requestId).matches("[0-9a-f-]{36}");
            assertThat(jsonLine(output, "Login ").get("requestId").asString()).isEqualTo(requestId);
        }
    }

    @Test
    @DisplayName("an invalid configuration stops the application at startup with a clear message (ADR-09)")
    void invalidConfigurationFailsFast() {
        assertThatThrownBy(() -> start("pragma.log.masking.default-type=NOPE").close())
                .hasStackTraceContaining("Invalid pragma.log configuration")
                .hasStackTraceContaining("NOPE");
    }

    @Test
    @DisplayName("format PLAIN keeps the regular Spring Boot console output")
    void plainFormat(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = start("pragma.log.format=plain")) {

            get(context, "req-1");

            assertThat(output.getOut().lines().filter(line -> line.contains("Login ")))
                    .isNotEmpty()
                    .noneMatch(line -> line.startsWith("{"));
        }
    }

    private static ConfigurableApplicationContext start(String... properties) {
        String[] all = Arrays.copyOf(new String[] {
                "server.port=0", "spring.application.name=orders-service", "spring.main.banner-mode=off"},
                3 + properties.length);
        System.arraycopy(properties, 0, all, 3, properties.length);
        return new SpringApplicationBuilder(TestApp.class).properties(all).run();
    }

    private static HttpResponse<String> get(ConfigurableApplicationContext context, String requestId) throws Exception {
        int port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login"))
                .header("X-Request-Id", requestId)
                .header("X-Client-Id", "mobile")
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
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
