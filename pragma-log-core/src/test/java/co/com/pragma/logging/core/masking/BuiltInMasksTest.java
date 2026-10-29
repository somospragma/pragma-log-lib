package co.com.pragma.logging.core.masking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BuiltInMasksTest {

    @Test
    @DisplayName("FULL masks every character")
    void fullMasksEverything() {
        assertThat(BuiltInMasks.full("password", '*')).isEqualTo("********");
    }

    @ParameterizedTest(name = "{0} with {1} -> {2}")
    @CsvSource({
            "secret123, 4, ****et123",
            "abc, 4, ***",
            "abc, 0, abc"
    })
    @DisplayName("PREFIX masks the first characters")
    void prefix(String value, int length, String expected) {
        assertThat(BuiltInMasks.prefix(value, '*', length)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} with {1} -> {2}")
    @CsvSource({
            "secret123, 4, secre****",
            "abc, 4, ***"
    })
    @DisplayName("SUFFIX masks the last characters")
    void suffix(String value, int length, String expected) {
        assertThat(BuiltInMasks.suffix(value, '*', length)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} with {1} -> {2}")
    @CsvSource({
            "4111111111111111, 4, 4111********1111",
            "12345678, 4, ********",
            "123456789, 4, 1234*6789"
    })
    @DisplayName("MIDDLE keeps both ends visible and preserves the length")
    void middle(String value, int length, String expected) {
        assertThat(BuiltInMasks.middle(value, '*', length)).isEqualTo(expected).hasSameSizeAs(value);
    }
}
