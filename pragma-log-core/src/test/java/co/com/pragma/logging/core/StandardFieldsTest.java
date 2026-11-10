package co.com.pragma.logging.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StandardFieldsTest {

    @Test
    @DisplayName("the message is never empty: the exception or a placeholder replaces it (FLD-11)")
    void message() {
        assertThat(StandardFields.message("Hello", () -> "unused")).isEqualTo("Hello");
        assertThat(StandardFields.message(" ", () -> "java.lang.IllegalStateException: boom"))
                .isEqualTo("java.lang.IllegalStateException: boom");
        assertThat(StandardFields.message(null, () -> null)).isEqualTo(StandardFields.EMPTY_MESSAGE);
        assertThat(StandardFields.message("", () -> "")).isEqualTo(StandardFields.EMPTY_MESSAGE);
    }
}
