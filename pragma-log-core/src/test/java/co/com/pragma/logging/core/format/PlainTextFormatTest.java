package co.com.pragma.logging.core.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlainTextFormatTest {

    private static final Instant INSTANT = Instant.parse("2026-11-02T15:04:05.123Z");

    @Test
    void writesTimestampLevelThreadLoggerMessageAndFields() {
        PlainTextFormat format = new PlainTextFormat(TimestampFormat.of(TimestampFormat.PLAIN_DEFAULT, "UTC"),
                false, Map.of());
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("requestId", "abc");
        fields.put("note", "two words");
        fields.put("empty", "");

        String line = format.format(INSTANT, "INFO", "main", "co.com.pragma.Sample", "Hello", fields, null);

        assertThat(line).isEqualTo("2026-11-02 15:04:05.123  INFO [main] co.com.pragma.Sample : Hello"
                + " requestId=abc note=\"two words\" empty=\"\"" + System.lineSeparator());
    }

    @Test
    void appendsStacktraceOnNextLines() {
        PlainTextFormat format = new PlainTextFormat(TimestampFormat.of("HH:mm", "UTC"), false, Map.of());

        String line = format.format(INSTANT, "ERROR", "main", "x", "Boom", Map.of(), "java.lang.Exception\n\tat x");

        assertThat(line).isEqualTo("15:04 ERROR [main] x : Boom" + System.lineSeparator()
                + "java.lang.Exception\n\tat x" + System.lineSeparator());
    }

    @Test
    void colorsLevelTimestampAndLoggerOnlyWhenEnabled() {
        PlainTextFormat format = new PlainTextFormat(TimestampFormat.of("HH:mm", "UTC"), true,
                Map.of("warn", "#ff8800"));

        String line = format.format(INSTANT, "WARN", "main", "x", "Careful", null, null);

        assertThat(line).startsWith("\u001b[2m15:04\u001b[0m \u001b[38;2;255;136;0m WARN\u001b[0m [main] "
                + "\u001b[36mx\u001b[0m : Careful");
    }

    @Test
    void rejectsUnknownLevelsAndColors() {
        var timestamp = TimestampFormat.of("HH:mm", "UTC");
        assertThatThrownBy(() -> new PlainTextFormat(timestamp, true, Map.of("FATAL", "red")))
                .hasMessageContaining("unknown level 'FATAL'");
        assertThatThrownBy(() -> new PlainTextFormat(timestamp, true, Map.of("INFO", "pinkish")))
                .hasMessageContaining("pragma.log.color.levels.INFO: unknown color 'pinkish'");
    }

    @Test
    void abbreviatesLongLoggerNamesKeepingTheClassName() {
        assertThat(PlainTextFormat.abbreviate("co.com.pragma.logging.examples.quarkus.CustomerResource"))
                .isEqualTo("c.c.p.l.e.quarkus.CustomerResource");
        assertThat(PlainTextFormat.abbreviate("a.b.VeryLongClassNameThatAloneIsLongerThanFortyChars"))
                .isEqualTo("a.b.VeryLongClassNameThatAloneIsLongerThanFortyChars");
    }
}
