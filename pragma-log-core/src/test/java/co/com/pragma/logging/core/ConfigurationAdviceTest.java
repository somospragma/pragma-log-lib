package co.com.pragma.logging.core;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.pragma.logging.core.http.HttpLogSettings;
import co.com.pragma.logging.core.layout.EventLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfigurationAdviceTest {

    private static ConfigurationAdvice.Settings settings(boolean masking, List<String> fields, List<String> patterns,
            int maxFieldLength, String format, boolean pretty, boolean csv, String custom, EventLayout layout,
            long maxLogSize, boolean truncate, boolean bodies, long bodyMaxSize, HttpLogSettings http) {
        return new ConfigurationAdvice.Settings(masking, fields, patterns, maxFieldLength, format, pretty, csv, custom,
                layout, maxLogSize, truncate, bodies, bodyMaxSize, http);
    }

    @Test
    @DisplayName("a consistent configuration has no warnings")
    void consistent() {
        assertThat(ConfigurationAdvice.check(settings(true, null, List.of("CARD"), 4000, "json", false, false, null,
                EventLayout.defaults(), 65_536, true, true, 4096,
                HttpLogSettings.of("X-Request-Id", List.of("X-Client-Id"))))).isEmpty();
        assertThat(ConfigurationAdvice.check(settings(true, null, List.of(), 0, null, false, false, null, null, 0,
                false, false, 0, null))).isEmpty();
    }

    @Test
    @DisplayName("inconsistent settings are reported with the properties involved (CFG-09)")
    void inconsistent() {
        EventLayout layout = new EventLayout(Set.of("userId", "thread"),
                Map.of("orderId", "id", "paymentId", "id", "region", "level"), List.of("thread"),
                List.of("userId"), null, "app");

        List<String> warnings = ConfigurationAdvice.check(settings(false, List.of(), List.of("CARD"), 10, "xml", true,
                true, "com.acme.Formatter", layout, 0, true, true, 8192,
                new HttpLogSettings("X-Request-Id", List.of("*"), List.of(), List.of())));

        assertThat(warnings)
                .anyMatch(w -> w.startsWith("pragma.log.masking.enabled is false"))
                .anyMatch(w -> w.startsWith("pragma.log.max-field-length is 10"))
                .anyMatch(w -> w.startsWith("pragma.log.json.pretty only applies to format json, not xml"))
                .anyMatch(w -> w.startsWith("pragma.log.csv.* only applies"))
                .anyMatch(w -> w.startsWith("pragma.log.custom-formatter only applies"))
                .anyMatch(w -> w.contains("required-fields lists userId"))
                .anyMatch(w -> w.contains("field-order lists thread"))
                .anyMatch(w -> w.contains("the same name id"))
                .anyMatch(w -> w.contains("to level, a standard field"))
                .anyMatch(w -> w.startsWith("pragma.log.extra-field is app"))
                .anyMatch(w -> w.startsWith("pragma.log.alerts.truncate needs"))
                .anyMatch(w -> w.startsWith("pragma.log.http.headers is \"*\""));
        assertThat(ConfigurationAdvice.check(settings(true, List.of(), List.of(), 0, "json", true, false, null, null,
                1000, false, true, 4096, null)))
                .anyMatch(w -> w.startsWith("pragma.log.masking.fields is empty"))
                .anyMatch(w -> w.contains("several lines"))
                .anyMatch(w -> w.startsWith("pragma.log.http.body.max-size (4096 bytes)"));
    }

    @Test
    @DisplayName("each warning is reported once per JVM")
    void reportOnce() {
        List<String> reported = new ArrayList<>();
        String unique = "test warning " + System.nanoTime();

        ConfigurationAdvice.reportOnce(List.of(unique), reported::add);
        ConfigurationAdvice.reportOnce(List.of(unique), reported::add);

        assertThat(reported).containsExactly(unique);
    }
}
