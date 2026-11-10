package co.com.pragma.logging.core.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HttpBodyCaptureTest {

    private final HttpBodyCapture capture = new HttpBodyCapture(true, true, 64, HttpBodyCapture.DEFAULT_CONTENT_TYPES);

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("only enabled captures with text content types (ENR-04)")
    void contentTypes() {
        assertThat(capture.enabled()).isTrue();
        assertThat(capture.captures("application/json; charset=UTF-8")).isTrue();
        assertThat(capture.captures("application/problem+json")).isTrue();
        assertThat(capture.captures("text/csv")).isTrue();
        assertThat(capture.captures("application/x-www-form-urlencoded")).isTrue();
        assertThat(capture.captures("image/png")).isFalse();
        assertThat(capture.captures("application/octet-stream")).isFalse();
        assertThat(capture.captures(null)).isFalse();
        assertThat(HttpBodyCapture.none().enabled()).isFalse();
        assertThat(HttpBodyCapture.none().captures("application/json")).isFalse();
        assertThat(new HttpBodyCapture(true, false, 10, List.of("APPLICATION/JSON")).captures("application/json"))
                .isTrue();
    }

    @Test
    @DisplayName("JSON and forms become objects for the encoder to mask; other text stays text")
    @SuppressWarnings("unchecked")
    void values() {
        Object json = capture.value(bytes("{\"user\":\"ana\",\"password\":\"x\",\"items\":[1,2]}"), -1,
                "application/json");
        Object form = capture.value(bytes("user=ana&token=a%20b&flag"), -1, "application/x-www-form-urlencoded");

        assertThat(json).isInstanceOf(Map.class);
        assertThat(((Map<String, Object>) json)).containsEntry("user", "ana").containsKey("items");
        assertThat(form).isEqualTo(Map.of("user", "ana", "token", "a b", "flag", ""));
        assertThat(capture.value(bytes("hola"), 4, "text/plain; charset=ISO-8859-1")).isEqualTo("hola");
        assertThat(capture.value(bytes("{broken"), -1, "application/json")).isEqualTo("{broken");
        assertThat(capture.value(new byte[0], 0, "application/json")).isNull();
        assertThat(capture.value(null, 0, "application/json")).isNull();
        assertThat(capture.value(bytes("x"), 1, "text/plain; charset=nope")).isEqualTo("x");
    }

    @Test
    @DisplayName("bodies longer than max-size are cut and say how much was left out")
    void truncated() {
        String body = "{\"data\":\"" + "x".repeat(100) + "\"}";

        Object value = capture.value(bytes(body), body.length(), "application/json");

        assertThat(value).isInstanceOf(String.class);
        assertThat((String) value).hasSize(64 + "...[truncated 47 bytes]".length()).endsWith("...[truncated 47 bytes]");
        assertThatThrownBy(() -> new HttpBodyCapture(true, true, -1, List.of()))
                .hasMessageContaining("max-size");
    }
}
