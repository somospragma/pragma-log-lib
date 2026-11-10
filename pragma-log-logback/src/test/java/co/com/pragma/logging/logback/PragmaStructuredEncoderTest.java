package co.com.pragma.logging.logback;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import co.com.pragma.logging.core.enrich.LogEnricher;
import co.com.pragma.logging.core.format.LogEventFormatter;
import co.com.pragma.logging.core.masking.MaskType;
import co.com.pragma.logging.core.masking.MaskedLogField;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Output formats and field layout of the Logback encoder (FMT-02..04, FLD-04..07, MSK-01b). */
class PragmaStructuredEncoderTest {

    private final LoggerContext context = new LoggerContext();
    private final Logger logger = context.getLogger("co.com.pragma.Sample");
    private PragmaStructuredEncoder encoder;

    @MaskedLogField(type = MaskType.FULL)
    record Card(String number, String holder) {
    }

    /** Custom format: level and message separated by a pipe. */
    public static class PipeFormatter implements LogEventFormatter {
        @Override
        public String format(Map<String, Object> event) {
            return event.get("level") + "|" + event.get("message") + "\n";
        }
    }

    /** Adds a region and a field with a sensitive name, which must come out masked. */
    public static class TenantEnricher implements LogEnricher {
        @Override
        public void enrich(EnrichedEvent event) {
            event.put("region", "co-bog");
            event.put("apiKey", "k-123456");
            event.put("requestId", "from-enricher");
        }
    }

    @AfterEach
    void tearDown() {
        if (encoder != null) {
            encoder.stop();
        }
    }

    private void start(Consumer<PragmaStructuredEncoder> config) {
        encoder = new PragmaStructuredEncoder();
        encoder.setContext(context);
        encoder.setAppName("orders-service");
        encoder.setIncludeHost(false);
        config.accept(encoder);
        encoder.start();
    }

    private String encode(LoggingEvent event) {
        return new String(encoder.encode(event), StandardCharsets.UTF_8);
    }

    private LoggingEvent event(String message, Object... args) {
        LoggingEvent event = new LoggingEvent(Logger.FQCN, logger, Level.INFO, message, null,
                args.length == 0 ? null : args);
        event.setMDCPropertyMap(Map.of("requestId", "r-1"));
        event.setThreadName("main");
        return event;
    }

    @Test
    @DisplayName("JSON by default; pretty JSON over several lines (FMT-02, FMT-03)")
    void json() {
        start(e -> { });
        assertThat(encode(event("Hello")).strip()).doesNotContain("\n");
        encoder.stop();

        start(e -> e.setPretty(true));
        String pretty = encode(event("Hello"));
        assertThat(pretty.lines().count()).isGreaterThan(5);
        assertThat(JsonMapper.shared().readTree(pretty).get("message").asString()).isEqualTo("Hello");
    }

    @Test
    @DisplayName("XML, one element per event, masked like JSON (FMT-04)")
    void xml() {
        start(e -> e.setFormat("xml"));

        String xml = encode(event("Paid with {}", new Card("4111111111111111", "Ana")));

        assertThat(xml).startsWith("<event><timestamp>").endsWith("</event>\n")
                .contains("<level>INFO</level>")
                .contains("<message>Paid with {&quot;number&quot;:&quot;****************&quot;,"
                        + "&quot;holder&quot;:&quot;***&quot;}</message>")
                .contains("<requestId>r-1</requestId>")
                .doesNotContain("4111111111111111");
    }

    @Test
    @DisplayName("CSV with the configured columns and the header at the start of the file (FMT-04)")
    void csv() {
        start(e -> {
            e.setFormat("CSV");
            e.setCsvColumns("level,requestId,message");
            e.setCsvDelimiter(";");
            e.setCsvHeader(true);
        });

        assertThat(new String(encoder.headerBytes(), StandardCharsets.UTF_8)).isEqualTo("level;requestId;message\n");
        assertThat(encode(event("a;b"))).isEqualTo("INFO;r-1;\"a;b\"\n");
        assertThat(encoder.footerBytes()).isNull();
    }

    @Test
    @DisplayName("custom formats are loaded by class name (FMT-04)")
    void custom() {
        start(e -> {
            e.setFormat("custom");
            e.setCustomFormatter(PipeFormatter.class.getName());
        });

        assertThat(encode(event("Hello"))).isEqualTo("INFO|Hello\n");
        assertThat(encoder.headerBytes()).isNull();
    }

    @Test
    @DisplayName("order, nested names, required fields and the extra object (FLD-04..07)")
    void layout() {
        start(e -> {
            e.setFieldOrder("level, message");
            e.addRenameField("requestId=$.http.request.id");
            e.setRequiredFields("requestId,userId");
            e.setRequiredPlaceholder("n/a");
            e.setExtraField("extra");
            e.setHideFields("thread,logger");
            e.addField("env=prod");
        });
        LoggingEvent event = event("Hello");
        event.setKeyValuePairs(List.of(new KeyValuePair("orderId", "o-1"), new KeyValuePair("amount", 10)));

        JsonNode json = JsonMapper.shared().readTree(encode(event));

        assertThat(json.propertyNames()).containsExactly("level", "message", "timestamp", "http", "extra", "app",
                "env", "userId");
        assertThat(json.get("http").get("request").get("id").asString()).isEqualTo("r-1");
        assertThat(json.get("extra").get("orderId").asString()).isEqualTo("o-1");
        assertThat(json.get("extra").get("amount").asInt()).isEqualTo(10);
        assertThat(json.get("userId").asString()).isEqualTo("n/a");
    }

    @Test
    @DisplayName("enrichers add masked fields to every format; MDC and key-values win (ENR-07)")
    void enrichers() {
        start(e -> {
            e.addEnricher(TenantEnricher.class.getName());
            e.addEnricher("com.acme.Missing");
        });

        JsonNode json = JsonMapper.shared().readTree(encode(event("Hello")));

        assertThat(json.get("region").asString()).isEqualTo("co-bog");
        assertThat(json.get("apiKey").asString()).matches("\\*+");
        assertThat(json.get("requestId").asString()).isEqualTo("r-1");
        assertThat(encoder.getConfigurationErrors()).singleElement().asString().contains("com.acme.Missing");
    }

    @Test
    @DisplayName("invalid format, delimiter, layout and timestamp are reported and replaced by safe defaults")
    void invalidConfiguration() {
        start(e -> {
            e.setFormat("yaml");
            e.addRenameField("a=$.x..y");
            e.setTimestampPattern("[NOPE]");
        });

        assertThat(encoder.getConfigurationErrors()).hasSize(3)
                .anyMatch(message -> message.contains("pragma.log.format"))
                .anyMatch(message -> message.contains("$.x..y"))
                .anyMatch(message -> message.contains("[NOPE]"));
        assertThat(JsonMapper.shared().readTree(encode(event("Still JSON"))).get("message").asString())
                .isEqualTo("Still JSON");
    }

    @Test
    @DisplayName("timestamp pattern and zone apply to every format (FMT-06)")
    void timestamp() {
        start(e -> {
            e.setTimestampPattern("[ISO_INSTANT]");
            e.setTimeZone("UTC");
            e.setFormat("csv");
            e.setCsvColumns("timestamp");
        });

        assertThat(encode(event("x"))).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z\\n");
    }
}
