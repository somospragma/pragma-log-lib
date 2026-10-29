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
