package co.com.pragma.logging.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.pragma.logging.core.masking.MaskingSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PragmaLogSettingsTest {

    @Test
    @DisplayName("defaults use the default application name")
    void defaultsUseDefaultAppName() {
        assertThat(PragmaLogSettings.defaults().appName()).isEqualTo(PragmaLogSettings.DEFAULT_APP_NAME);
    }

    @Test
    @DisplayName("a blank application name is rejected with a clear message")
    void blankAppNameIsRejected() {
        assertThatThrownBy(() -> new PragmaLogSettings("  ", MaskingSettings.defaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pragma.log.appName");
    }

    @Test
    @DisplayName("a null application name is rejected with a clear message")
    void nullAppNameIsRejected() {
        assertThatThrownBy(() -> new PragmaLogSettings(null, MaskingSettings.defaults()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("pragma.log.appName");
    }
}
