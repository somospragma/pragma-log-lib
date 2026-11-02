package co.com.pragma.logging.core.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AnsiColorsTest {

    @Test
    void resolvesNamesAndHexValues() {
        assertThat(AnsiColors.code("Red")).isEqualTo("\u001b[31m");
        assertThat(AnsiColors.code("bright-blue")).isEqualTo("\u001b[94m");
        assertThat(AnsiColors.code("#00aa88")).isEqualTo("\u001b[38;2;0;170;136m");
        assertThat(AnsiColors.code("#f80")).isEqualTo("\u001b[38;2;255;136;0m");
    }

    @Test
    void rejectsUnknownColors() {
        assertThatThrownBy(() -> AnsiColors.code("#12345")).hasMessageContaining("unknown color '#12345'");
        assertThatThrownBy(() -> AnsiColors.code(" ")).hasMessageContaining("must not be blank");
    }
}
