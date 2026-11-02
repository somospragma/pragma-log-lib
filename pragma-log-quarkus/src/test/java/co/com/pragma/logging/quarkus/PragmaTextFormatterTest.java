package co.com.pragma.logging.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.pragma.logging.core.format.PlainTextFormat;
import co.com.pragma.logging.core.format.TimestampFormat;
import co.com.pragma.logging.core.masking.LogMasker;
import co.com.pragma.logging.core.masking.MaskType;
import co.com.pragma.logging.core.masking.MaskedLogField;
import co.com.pragma.logging.core.masking.MaskingSettings;
import co.com.pragma.logging.quarkus.slf4j.Slf4jLogRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.ExtLogRecord.FormatStyle;
import org.jboss.logmanager.Level;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;

class PragmaTextFormatterTest {

    record Card(String holder, @MaskedLogField(type = MaskType.MIDDLE, length = 4) String number) {
    }

    private final Card card = new Card("Ana", "4111111111111111");

    private PragmaTextFormatter formatter(boolean colors, Set<String> hidden) {
        return new PragmaTextFormatter(new LogMasker(MaskingSettings.defaults()),
                new PlainTextFormat(TimestampFormat.of("HH:mm:ss", "UTC"), colors, Map.of()), hidden, true, 50,
                16_384, false);
    }

    private static <T extends ExtLogRecord> T at(T record) {
        record.setInstant(Instant.parse("2026-11-02T15:04:05Z"));
        record.setLoggerName("co.com.pragma.Sample");
        record.setThreadName("worker-1");
        return record;
    }

    @Test
    void slf4jArgumentsAndKeyValuesAreMasked() {
        ExtLogRecord record = at(new Slf4jLogRecord(Level.INFO, "Paid with {}", new Object[] {card},
                List.of(new KeyValuePair("token", "abc"), new KeyValuePair("card", card)), "test"));

        String line = formatter(false, Set.of()).format(record);

        assertThat(line).isEqualTo("15:04:05  INFO [worker-1] co.com.pragma.Sample : Paid with"
                + " {\"holder\":\"Ana\",\"number\":\"4111********1111\"} token=***"
                + " card={\"holder\":\"Ana\",\"number\":\"4111********1111\"}" + System.lineSeparator());
    }

    @Test
    void jbossParametersHiddenFieldsColorsAndStacktrace() {
        ExtLogRecord record = at(new ExtLogRecord(Level.ERROR, "Charge %s failed", FormatStyle.PRINTF, "test"));
        record.setParameters(new Object[] {card});
        record.setThrown(new IllegalStateException("gateway down"));

        String[] lines = formatter(true, Set.of()).format(record).split("\\R");

        assertThat(lines[0]).isEqualTo("\u001b[2m15:04:05\u001b[0m \u001b[31mERROR\u001b[0m [worker-1] "
                + "\u001b[36mco.com.pragma.Sample\u001b[0m : Charge {\"holder\":\"Ana\",\"number\":\"4111********1111\"}"
                + " failed");
        assertThat(lines[1]).isEqualTo("java.lang.IllegalStateException: gateway down");
    }
}
