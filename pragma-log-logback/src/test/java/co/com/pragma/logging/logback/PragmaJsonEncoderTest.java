package co.com.pragma.logging.logback;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PragmaJsonEncoderTest {

    private final LoggerContext context = new LoggerContext();
    private final Logger logger = context.getLogger("co.com.pragma.Sample");
    private PragmaJsonEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new PragmaJsonEncoder();
        encoder.setContext(context);
        encoder.setAppName("orders-service");
        encoder.start();
    }

    @AfterEach
    void tearDown() {
        encoder.stop();
    }

    @Test
    @DisplayName("emits a single-line JSON with the Pragma standard fields")
    void emitsStandardFields() {
        JsonNode json = encode(event("User created", Map.of()));

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
        LoggingEvent event = event("Order placed", Map.of("requestId", "req-1"));
        event.setKeyValuePairs(List.of(new KeyValuePair("orderId", "ORD-9")));

        JsonNode json = encode(event);

        assertThat(json.get("requestId").asString()).isEqualTo("req-1");
        assertThat(json.get("orderId").asString()).isEqualTo("ORD-9");
    }

    private LoggingEvent event(String message, Map<String, String> mdc) {
        LoggingEvent event = new LoggingEvent(Logger.FQCN, logger, Level.INFO, message, null, null);
        event.setMDCPropertyMap(mdc);
        return event;
    }

    private JsonNode encode(LoggingEvent event) {
        String line = new String(encoder.encode(event), StandardCharsets.UTF_8);
        assertThat(line.strip()).doesNotContain("\n");
        return JsonMapper.shared().readTree(line);
    }
}
