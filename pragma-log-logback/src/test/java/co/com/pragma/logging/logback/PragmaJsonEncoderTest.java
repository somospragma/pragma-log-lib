package co.com.pragma.logging.logback;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.status.Status;
import co.com.pragma.logging.core.masking.MaskType;
import co.com.pragma.logging.core.masking.MaskedLogField;
import co.com.pragma.logging.core.masking.MaskingStrategy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PragmaJsonEncoderTest {

    private final LoggerContext context = new LoggerContext();
    private final Logger logger = context.getLogger("co.com.pragma.Sample");
    private PragmaJsonEncoder encoder;

    static class User {
        String name = "Ana";
        @MaskedLogField
        String password = "s3cr3t-pass";
        @MaskedLogField(type = MaskType.MIDDLE, length = 2)
        String document = "1020304050";

        @Override
        public String toString() {
            return "User{password=s3cr3t-pass, document=1020304050}";
        }
    }

    public static class InitialsStrategy implements MaskingStrategy {
        @Override
        public String mask(String value, char maskChar, int length) {
            return value.substring(0, 1) + maskChar;
        }
    }

    static class Profile {
        @MaskedLogField(type = MaskType.CUSTOM, strategy = "INITIALS")
        String fullName = "Ana Perez";
    }

    @AfterEach
    void tearDown() {
        if (encoder != null) {
            encoder.stop();
        }
    }

    private PragmaJsonEncoder startEncoder(java.util.function.Consumer<PragmaJsonEncoder> config) {
        encoder = new PragmaJsonEncoder();
        encoder.setContext(context);
        encoder.setAppName("orders-service");
        config.accept(encoder);
        encoder.start();
        return encoder;
    }

    private PragmaJsonEncoder startEncoder() {
        return startEncoder(e -> { });
    }

    @Test
    @DisplayName("emits a single-line JSON with the Pragma standard fields")
    void emitsStandardFields() {
        startEncoder();

        JsonNode json = encode(event("User created"));

        assertThat(json.get("timestamp").asString()).isNotBlank();
        assertThat(json.get("level").asString()).isEqualTo("INFO");
        assertThat(json.get("logger").asString()).isEqualTo("co.com.pragma.Sample");
        assertThat(json.get("thread").asString()).isNotBlank();
        assertThat(json.get("message").asString()).isEqualTo("User created");
        assertThat(json.get("app").asString()).isEqualTo("orders-service");
        assertThat(json.has("@version")).isFalse();
        assertThat(json.has("level_value")).isFalse();
    }

    @Test
    @DisplayName("includes MDC entries and SLF4J key-value pairs as top-level fields")
    void includesMdcAndKeyValues() {
        startEncoder();
        LoggingEvent event = eventWithMdc(Map.of("requestId", "req-1"), "Order placed");
        event.setKeyValuePairs(List.of(new KeyValuePair("orderId", "ORD-9")));

        JsonNode json = encode(event);

        assertThat(json.get("requestId").asString()).isEqualTo("req-1");
        assertThat(json.get("orderId").asString()).isEqualTo("ORD-9");
    }

    @Nested
    @DisplayName("masking")
    class Masking {

        @Test
        @DisplayName("objects in {} placeholders are masked, never rendered with toString() (MSK-07)")
        void masksMessageArguments() {
            startEncoder();

            String line = encodeLine(event("Login {}", new User()));

            assertThat(line).doesNotContain("s3cr3t-pass").doesNotContain("1020304050");
            assertThat(json(line).get("message").asString())
                    .isEqualTo("Login {\"name\":\"Ana\",\"password\":\"***********\",\"document\":\"10******50\"}");
        }

        @Test
        @DisplayName("the original event and its arguments are not modified (MSK-08)")
        void doesNotMutateEvent() {
            startEncoder();
            User user = new User();
            LoggingEvent event = event("Login {}", user);

            encode(event);

            assertThat(event.getArgumentArray()).containsExactly(user);
            assertThat(user.password).isEqualTo("s3cr3t-pass");
        }

        @Test
        @DisplayName("MDC entries with sensitive names are masked (MSK-09)")
        void masksSensitiveMdc() {
            startEncoder();
            LoggingEvent event = eventWithMdc(Map.of("Authorization", "Bearer abc", "requestId", "req-1"), "Call");

            JsonNode json = encode(event);

            assertThat(json.get("Authorization").asString()).isEqualTo("**********");
            assertThat(json.get("requestId").asString()).isEqualTo("req-1");
        }

        @Test
        @DisplayName("key-values are masked by name and objects are written as masked JSON")
        void masksKeyValues() {
            startEncoder();
            LoggingEvent event = event("Signup");
            event.setKeyValuePairs(List.of(new KeyValuePair("password", "plain"), new KeyValuePair("user", new User())));

            JsonNode json = encode(event);

            assertThat(json.get("password").asString()).isEqualTo("*****");
            assertThat(json.get("user").get("name").asString()).isEqualTo("Ana");
            assertThat(json.get("user").get("password").asString()).isEqualTo("***********");
        }

        @Test
        @DisplayName("maskFields replaces the default sensitive names")
        void customMaskFields() {
            startEncoder(e -> e.setMaskFields("documentId"));
            LoggingEvent event = event("Lookup");
            event.setKeyValuePairs(List.of(new KeyValuePair("document_id", "123"), new KeyValuePair("password", "x")));

            JsonNode json = encode(event);

            assertThat(json.get("document_id").asString()).isEqualTo("***");
            assertThat(json.get("password").asString()).isEqualTo("x");
        }

        @Test
        @DisplayName("custom strategies are loaded by class name")
        void customStrategy() {
            startEncoder(e -> e.addMaskStrategy("INITIALS=" + InitialsStrategy.class.getName()));

            JsonNode json = encode(event("Profile {}", new Profile()));

            assertThat(json.get("message").asString()).isEqualTo("Profile {\"fullName\":\"A*\"}");
        }
    }

    @Nested
    @DisplayName("field layout")
    class FieldLayoutTests {

        @Test
        @DisplayName("fixed fields are added to every event (FLD-05)")
        void fixedFields() {
            startEncoder(e -> {
                e.addField("env=prod");
                e.addField("team=payments");
            });

            JsonNode json = encode(event("Hello"));

            assertThat(json.get("env").asString()).isEqualTo("prod");
            assertThat(json.get("team").asString()).isEqualTo("payments");
        }

        @Test
        @DisplayName("standard, fixed, MDC and key-value fields can be hidden (FLD-02)")
        void hideFields() {
            startEncoder(e -> {
                e.addField("env=prod");
                e.setHideFields("thread, app, env, internalId, debugInfo");
            });
            LoggingEvent event = eventWithMdc(Map.of("internalId", "x", "requestId", "r"), "Hello");
            event.setKeyValuePairs(List.of(new KeyValuePair("debugInfo", "y")));

            JsonNode json = encode(event);

            assertThat(json.has("thread")).isFalse();
            assertThat(json.has("app")).isFalse();
            assertThat(json.has("env")).isFalse();
            assertThat(json.has("internalId")).isFalse();
            assertThat(json.has("debugInfo")).isFalse();
            assertThat(json.get("requestId").asString()).isEqualTo("r");
        }

        @Test
        @DisplayName("standard, fixed, MDC and key-value fields can be renamed (FLD-03)")
        void renameFields() {
            startEncoder(e -> {
                e.addField("env=prod");
                e.addRenameField("level=severity");
                e.addRenameField("app=service.name");
                e.addRenameField("env=deployment.environment");
                e.addRenameField("requestId=request_id");
                e.addRenameField("orderId=order_id");
            });
            LoggingEvent event = eventWithMdc(Map.of("requestId", "r"), "Hello");
            event.setKeyValuePairs(List.of(new KeyValuePair("orderId", "o")));

            JsonNode json = encode(event);

            assertThat(json.get("severity").asString()).isEqualTo("INFO");
            assertThat(json.get("service.name").asString()).isEqualTo("orders-service");
            assertThat(json.get("deployment.environment").asString()).isEqualTo("prod");
            assertThat(json.get("request_id").asString()).isEqualTo("r");
            assertThat(json.get("order_id").asString()).isEqualTo("o");
            assertThat(json.has("level")).isFalse();
        }

        @Test
        @DisplayName("malformed definitions are reported, not applied")
        void malformedDefinitions() {
            startEncoder(e -> {
                e.addField("noEquals");
                e.addRenameField("level=");
            });

            JsonNode json = encode(event("Hello"));

            assertThat(json.get("level").asString()).isEqualTo("INFO");
            assertThat(errors()).anyMatch(s -> s.getMessage().contains("field must look like name=value"));
            assertThat(errors()).anyMatch(s -> s.getMessage().contains("renameField must look like name=value"));
        }
    }

    @Nested
    @DisplayName("exceptions")
    class Exceptions {

        private final IllegalStateException failure =
                new IllegalStateException("Gateway timeout", new java.net.SocketTimeoutException("Read timed out"));

        @Test
        @DisplayName("an error summary with the root cause is written (ERR-02, ERR-04)")
        void errorSummary() {
            startEncoder();

            JsonNode error = encode(errorEvent()).get("error");

            assertThat(error.get("type").asString()).isEqualTo("java.lang.IllegalStateException");
            assertThat(error.get("message").asString()).isEqualTo("Gateway timeout");
            assertThat(error.get("rootCause").asString()).isEqualTo("java.net.SocketTimeoutException: Read timed out");
        }

        @Test
        @DisplayName("the stack trace is written by default, with masking enabled (ERR-01 legacy bug)")
        void stacktraceByDefault() {
            startEncoder();

            JsonNode json = encode(errorEvent());

            assertThat(json.get("stacktrace").asString())
                    .startsWith("java.lang.IllegalStateException: Gateway timeout")
                    .contains("Caused by: java.net.SocketTimeoutException");
        }

        @Test
        @DisplayName("includeStacktrace=false keeps only the summary (ERR-01)")
        void withoutStacktrace() {
            startEncoder(e -> e.setIncludeStacktrace(false));

            JsonNode json = encode(errorEvent());

            assertThat(json.has("stacktrace")).isFalse();
            assertThat(json.get("error").get("type").asString()).isEqualTo("java.lang.IllegalStateException");
        }

        @Test
        @DisplayName("the stack trace length is bounded (ERR-03)")
        void boundedStacktrace() {
            startEncoder(e -> e.setStacktraceMaxLength(120));

            assertThat(encode(errorEvent()).get("stacktrace").asString()).hasSizeLessThanOrEqualTo(120);
        }

        @Test
        @DisplayName("events without exception have no error field")
        void noErrorField() {
            startEncoder();

            assertThat(encode(event("Fine")).has("error")).isFalse();
        }

        private LoggingEvent errorEvent() {
            LoggingEvent event = new LoggingEvent(Logger.FQCN, logger, Level.ERROR, "Payment failed", failure, null);
            event.setMDCPropertyMap(Map.of());
            return event;
        }
    }

    @Nested
    @DisplayName("resilience")
    class Resilience {

        @Test
        @DisplayName("if masking fails the event is still written, without arguments (ALR-05)")
        void fallbackOnMaskingFailure() {
            startEncoder();
            LoggingEvent event = eventWithMdc(new ExplodingMap(), "Payment for {} failed", new User());

            String line = encodeLine(event);
            JsonNode json = json(line);

            assertThat(json.get("message").asString()).isEqualTo("Payment for {} failed");
            assertThat(json.get(EventMasker.ERROR_FIELD).asString()).isEqualTo(IllegalStateException.class.getName());
            assertThat(line).doesNotContain("s3cr3t-pass");
            assertThat(errors()).anyMatch(s -> s.getMessage().contains("could not mask event"));
        }

        @Test
        @DisplayName("an invalid masking configuration is reported and secure defaults are used (CFG-05)")
        void invalidConfigurationUsesSecureDefaults() {
            startEncoder(e -> e.setMaskDefaultType("NOPE"));
            LoggingEvent event = event("Signup");
            event.setKeyValuePairs(List.of(new KeyValuePair("password", "plain")));

            JsonNode json = encode(event);

            assertThat(json.get("password").asString()).isEqualTo("*****");
            assertThat(errors()).anyMatch(s -> s.getMessage().contains("invalid masking configuration"));
        }
    }

    /** MDC map that fails when inspected, to force the masking fallback. */
    static class ExplodingMap extends HashMap<String, String> {
        private static final long serialVersionUID = 1L;

        @Override
        public Set<String> keySet() {
            throw new IllegalStateException("boom");
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    private List<Status> errors() {
        return context.getStatusManager().getCopyOfStatusList().stream()
                .filter(s -> s.getLevel() == Status.ERROR)
                .toList();
    }

    private LoggingEvent event(String message, Object... args) {
        return eventWithMdc(Map.of(), message, args);
    }

    private LoggingEvent eventWithMdc(Map<String, String> mdc, String message, Object... args) {
        LoggingEvent event = new LoggingEvent(Logger.FQCN, logger, Level.INFO, message, null,
                args.length == 0 ? null : args);
        event.setMDCPropertyMap(mdc);
        return event;
    }

    private String encodeLine(LoggingEvent event) {
        String line = new String(encoder.encode(event), StandardCharsets.UTF_8);
        assertThat(line.strip()).doesNotContain("\n");
        return line;
    }

    private JsonNode encode(LoggingEvent event) {
        return json(encodeLine(event));
    }

    private static JsonNode json(String line) {
        return JsonMapper.shared().readTree(line);
    }
}
