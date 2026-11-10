package co.com.pragma.logging.core.layout;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.pragma.logging.core.alert.LargeLogAlert;
import co.com.pragma.logging.core.alert.LogAlertListener;
import co.com.pragma.logging.core.alert.LogAlerts;
import co.com.pragma.logging.core.alert.MaskingErrorAlert;
import co.com.pragma.logging.core.alert.MissingFieldsAlert;
import co.com.pragma.logging.core.format.CsvEventFormatter;
import co.com.pragma.logging.core.format.JsonEventFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StructuredEventWriterTest {

    static class Recorder implements LogAlertListener {
        final List<Object> alerts = new ArrayList<>();

        @Override
        public void onLargeLog(LargeLogAlert alert) {
            alerts.add(alert);
        }

        @Override
        public void onMaskingError(MaskingErrorAlert alert) {
            alerts.add(alert);
        }

        @Override
        public void onMissingFields(MissingFieldsAlert alert) {
            alerts.add(alert);
        }
    }

    @Test
    @DisplayName("writes the arranged fields and reports missing required fields and large events")
    void write() {
        Recorder recorder = new Recorder();
        StructuredEventWriter writer = new StructuredEventWriter(new EventLayout(Set.of(), Map.of(), List.of(),
                List.of("requestId"), null, null), new JsonEventFormatter(false),
                new LogAlerts(10, false, List.of(recorder)));

        String line = writer.write(new EventFields().standard("message", "hello"), "a.B", "INFO");

        assertThat(line).isEqualTo("{\"message\":\"hello\",\"requestId\":\"unknown\"}\n");
        assertThat(recorder.alerts).containsExactly(new MissingFieldsAlert("a.B", "INFO", List.of("requestId")),
                new LargeLogAlert("a.B", "INFO", line.length(), 10));
        assertThat(writer.layout().required()).containsExactly("requestId");
        assertThat(writer.formatter()).isInstanceOf(JsonEventFormatter.class);
        assertThat(writer.header()).isNull();
    }

    @Test
    @DisplayName("a failing custom format writes JSON instead and reports it; a failing header is skipped")
    void failingFormat() {
        Recorder recorder = new Recorder();
        StructuredEventWriter writer = new StructuredEventWriter(EventLayout.defaults(), new FailingFormatter(),
                new LogAlerts(0, false, List.of(recorder)));

        assertThat(writer.write(new EventFields().standard("level", "INFO"), "a.B", "INFO"))
                .isEqualTo("{\"level\":\"INFO\"}\n");
        assertThat(recorder.alerts).singleElement().isInstanceOf(MaskingErrorAlert.class);
        assertThat(writer.header()).isNull();
        assertThat(new StructuredEventWriter(EventLayout.defaults(), new CsvEventFormatter(List.of("a"), ',', true),
                LogAlerts.none()).header()).isEqualTo("a\n");
    }

    static class FailingFormatter implements co.com.pragma.logging.core.format.LogEventFormatter {
        @Override
        public String format(Map<String, Object> event) {
            throw new IllegalStateException("broken");
        }

        @Override
        public String header() {
            throw new IllegalStateException("broken");
        }
    }
}
