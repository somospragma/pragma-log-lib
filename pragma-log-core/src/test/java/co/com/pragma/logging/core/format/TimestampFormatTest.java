package co.com.pragma.logging.core.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TimestampFormatTest {

    private static final Instant INSTANT = Instant.parse("2026-11-02T15:04:05.123Z");

    @Test
    void appliesPatternConstantAndZone() {
        assertThat(TimestampFormat.of(TimestampFormat.JSON_DEFAULT, "America/Bogota").format(INSTANT))
                .isEqualTo("2026-11-02T10:04:05.123-05:00");
        assertThat(TimestampFormat.of("[ISO_INSTANT]", null).format(INSTANT)).isEqualTo("2026-11-02T15:04:05.123Z");
        assertThat(TimestampFormat.of("dd/MM/yyyy HH:mm:ss", "UTC").format(INSTANT)).isEqualTo("02/11/2026 15:04:05");
    }

    @Test
    void rejectsInvalidPatternsAndZones() {
        assertThatThrownBy(() -> TimestampFormat.of("yyyy-MM-dd'T", "UTC"))
                .hasMessageContaining("pragma.log.timestamp.pattern is not valid");
        assertThatThrownBy(() -> TimestampFormat.of("[NOPE]", "UTC"))
                .hasMessageContaining("[NOPE] is not a DateTimeFormatter constant");
        assertThatThrownBy(() -> TimestampFormat.of(TimestampFormat.JSON_DEFAULT, "Mars/Olympus"))
                .hasMessageContaining("pragma.log.timestamp.zone is not a valid zone id");
    }
}
