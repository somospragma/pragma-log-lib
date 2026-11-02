package co.com.pragma.logging.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.pragma.logging.core.format.TimestampFormat;
import co.com.pragma.logging.core.masking.LogMasker;
import co.com.pragma.logging.core.masking.MaskType;
import co.com.pragma.logging.core.masking.MaskedLogField;
import co.com.pragma.logging.core.masking.MaskingSettings;
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

class PragmaJsonFormatterTest {

    record Card(String holder, @MaskedLogField(type = MaskType.MIDDLE, length = 4) String number, String password) {
    }

    /** toString exposes everything, like many real classes do. */
    private final Card card = new Card("Ana", "4111111111111111", "s3cr3t");

    private PragmaJsonFormatter formatter(Set<String> hidden, Map<String, String> renames) {
        return new PragmaJsonFormatter(new LogMasker(MaskingSettings.defaults()),
                TimestampFormat.of(TimestampFormat.JSON_DEFAULT, null), hidden, renames,
                Map.of("app", "orders-service"), true, 50, 16_384, false);
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
}
