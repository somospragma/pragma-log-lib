package co.com.pragma.logging.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.pragma.logging.core.alert.LogAlerts;
import co.com.pragma.logging.core.enrich.LogEnrichers;
import co.com.pragma.logging.core.format.CsvEventFormatter;
import co.com.pragma.logging.core.format.JsonEventFormatter;
import co.com.pragma.logging.core.format.LogEventFormatter;
import co.com.pragma.logging.core.format.TimestampFormat;
import co.com.pragma.logging.core.format.XmlEventFormatter;
import co.com.pragma.logging.core.layout.EventLayout;
import co.com.pragma.logging.core.layout.StructuredEventWriter;
import co.com.pragma.logging.core.masking.LogMasker;
import co.com.pragma.logging.core.masking.MaskType;
import co.com.pragma.logging.core.masking.MaskedLogField;
import co.com.pragma.logging.core.masking.MaskingSettings;
import co.com.pragma.logging.core.masking.PatternMasker;
import co.com.pragma.logging.core.masking.TextSanitizer;
import co.com.pragma.logging.quarkus.slf4j.Slf4jLogRecord;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.ExtLogRecord.FormatStyle;
import org.jboss.logmanager.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PragmaStructuredFormatterTest {

    record Card(String holder, @MaskedLogField(type = MaskType.MIDDLE, length = 4) String number, String password) {
    }

    /** toString exposes everything, like many real classes do. */
    private final Card card = new Card("Ana", "4111111111111111", "s3cr3t");

    private PragmaStructuredFormatter formatter(Set<String> hidden, Map<String, String> renames) {
        return formatter(new EventLayout(hidden, renames, List.of(), List.of(), null, null), new JsonEventFormatter(false));
    }

    private PragmaStructuredFormatter formatter(EventLayout layout, LogEventFormatter format) {
        return new PragmaStructuredFormatter(new LogMasker(MaskingSettings.defaults()),
                new TextSanitizer(PatternMasker.of(List.of("CARD"), '*'), 0),
                new LogEnrichers(List.of(event -> event.put("region", "us-east-1"))), LogAlerts.none(),
                new StructuredEventWriter(layout, format, LogAlerts.none()),
                TimestampFormat.of(TimestampFormat.JSON_DEFAULT, null), Map.of("app", "orders-service"), true, 50,
                16_384, false);
    }

    private JsonNode format(ExtLogRecord record) {
        record.setLoggerName("co.com.pragma.Sample");
        String line = formatter(Set.of(), Map.of()).format(record);
        assertThat(line).endsWith("\n").doesNotContain("s3cr3t").doesNotContain("4111111111111111");
        return JsonMapper.shared().readTree(line);
    }

    @Test
    @DisplayName("SLF4J records: arguments and key-values are masked, key-values stay structured")
    void slf4jRecord() {
        ExtLogRecord record = new Slf4jLogRecord(Level.INFO, "Paid with {} for order {}", new Object[] {card, 7},
                List.of(new KeyValuePair("card", card), new KeyValuePair("token", "abc"),
                        new KeyValuePair("amount", 12)), "test");

        JsonNode json = format(record);

        assertThat(json.get("message").asString()).contains("4111********1111").endsWith("for order 7");
        assertThat(json.get("card").get("number").asString()).isEqualTo("4111********1111");
        assertThat(json.get("token").asString()).isEqualTo("***");
        assertThat(json.get("amount").asInt()).isEqualTo(12);
        assertThat(json.get("app").asString()).isEqualTo("orders-service");
        assertThat(record.getMessage()).contains("s3cr3t"); // other formatters still see plain SLF4J text
    }

    @Test
    @DisplayName("JBoss Logging printf and MessageFormat parameters are masked; %d still works")
    void jbossLoggingParameters() {
        ExtLogRecord printf = new ExtLogRecord(Level.WARN, "Card %s, attempt %d", FormatStyle.PRINTF, "test");
        printf.setParameters(new Object[] {card, 3});
        ExtLogRecord messageFormat = new ExtLogRecord(Level.INFO, "Card {0}", FormatStyle.MESSAGE_FORMAT, "test");
        messageFormat.setParameters(new Object[] {card});

        JsonNode warn = format(printf);

        assertThat(warn.get("message").asString()).contains("4111********1111").endsWith("attempt 3");
        assertThat(warn.get("level").asString()).isEqualTo("WARN");
        assertThat(format(messageFormat).get("message").asString()).contains("\"password\":\"******\"");
    }

    @Test
    @DisplayName("records from slf4j-jboss-logmanager are re-masked from their formatted text")
    void jbossSlf4jFallback() {
        ExtLogRecord record = new ExtLogRecord(java.util.logging.Level.INFO, "Paid with " + card + " today",
                FormatStyle.NO_FORMAT, RecordMasking.JBOSS_SLF4J_LOGGER);
        record.setParameters(new Object[] {card});

        assertThat(format(record).get("message").asString())
                .startsWith("Paid with {").contains("4111********1111").endsWith(" today");
    }

    @Test
    @DisplayName("MDC is masked by name; JUL levels map to SLF4J names; exceptions get a summary")
    void mdcLevelsAndErrors() {
        org.jboss.logmanager.MDC.put("requestId", "req-1");
        org.jboss.logmanager.MDC.put("http.header.authorization", "Bearer abc");
        try {
            ExtLogRecord record = new ExtLogRecord(java.util.logging.Level.SEVERE, "Failed", "test");
            record.setThrown(new IllegalStateException("boom", new RuntimeException("root")));

            JsonNode json = format(record);

            assertThat(json.get("level").asString()).isEqualTo("ERROR");
            assertThat(json.get("requestId").asString()).isEqualTo("req-1");
            assertThat(json.get("http.header.authorization").asString()).isEqualTo("**********");
            assertThat(json.get("error").get("rootCause").asString()).isEqualTo("java.lang.RuntimeException: root");
            assertThat(json.get("stacktrace").asString()).contains("Caused by: java.lang.RuntimeException: root");
        } finally {
            org.jboss.logmanager.MDC.clear();
        }
    }

    @Test
    @DisplayName("fields can be hidden and renamed")
    void hideAndRename() {
        ExtLogRecord record = new ExtLogRecord(Level.INFO, "Hello", "test");
        record.setLoggerName("co.com.pragma.Sample");

        JsonNode json = JsonMapper.shared().readTree(
                formatter(Set.of("thread", "app"), Map.of("level", "severity")).format(record));

        assertThat(json.has("thread")).isFalse();
        assertThat(json.has("app")).isFalse();
        assertThat(json.get("severity").asString()).isEqualTo("INFO");
    }

    @Test
    @DisplayName("XML and CSV with the same masking; CSV header at the start of a file (FMT-04)")
    void xmlAndCsv() {
        ExtLogRecord record = new Slf4jLogRecord(Level.INFO, "Paid with {}", new Object[] {card},
                List.of(new KeyValuePair("orderId", "o-1")), "test");
        record.setLoggerName("co.com.pragma.Sample");

        String xml = formatter(EventLayout.defaults(), new XmlEventFormatter()).format(record);
        PragmaStructuredFormatter csv = formatter(EventLayout.defaults(),
                new CsvEventFormatter(List.of("level", "orderId", "message"), ',', true));

        assertThat(xml).startsWith("<event>").contains("<orderId>o-1</orderId>").contains("4111********1111")
                .doesNotContain("4111111111111111");
        assertThat(csv.getHead(null)).isEqualTo("level,orderId,message\n");
        assertThat(csv.format(record)).startsWith("INFO,o-1,").doesNotContain("4111111111111111");
        assertThat(formatter(EventLayout.defaults(), new XmlEventFormatter()).getHead(null)).isEmpty();
    }

    @Test
    @DisplayName("order, nested names, required fields and the extra object (FLD-04..07)")
    void layout() {
        ExtLogRecord record = new Slf4jLogRecord(Level.INFO, "Hello", null,
                List.of(new KeyValuePair("orderId", "o-1"), new KeyValuePair("requestId", "r-1")), "test");
        record.setLoggerName("co.com.pragma.Sample");
        EventLayout layout = new EventLayout(Set.of("thread", "logger"), Map.of("requestId", "$.http.request.id"),
                List.of("level", "message"), List.of("requestId", "userId"), null, "extra");

        JsonNode json = JsonMapper.shared().readTree(formatter(layout, new JsonEventFormatter(false)).format(record));

        assertThat(json.propertyNames()).containsExactly("level", "message", "timestamp", "extra", "http", "app",
                "userId");
        assertThat(json.get("http").get("request").get("id").asString()).isEqualTo("r-1");
        assertThat(json.get("extra").get("orderId").asString()).isEqualTo("o-1");
        assertThat(json.get("userId").asString()).isEqualTo(EventLayout.DEFAULT_PLACEHOLDER);
    }
}
