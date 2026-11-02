package co.com.pragma.logging.core.masking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TextSanitizerTest {

    private final TextSanitizer sanitizer = new TextSanitizer(PatternMasker.of(List.of("CARD"), '*'), 20);

    @Test
    void masksBeforeTruncatingSoNoPartOfASecretSurvives() {
        assertThat(sanitizer.text("card 4111111111111111 and more text"))
                .isEqualTo("card 4111********111...[truncated 15 chars]");
        assertThat(sanitizer.text("short")).isEqualTo("short");
    }

    @Test
    void sanitizesTextNodesInsideTreesWithoutTouchingTheOriginal() {
        JsonNode tree = JsonMapper.shared().readTree("{\"note\":\"c 4111111111111111\",\"items\":[\"x\",7]}");

        JsonNode sanitized = (JsonNode) sanitizer.value(tree);

        assertThat(sanitized.toString()).isEqualTo("{\"note\":\"c 4111********1111\",\"items\":[\"x\",7]}");
        assertThat(tree.get("note").asString()).isEqualTo("c 4111111111111111");
        assertThat(sanitizer.value(42)).isEqualTo(42);
    }

    @Test
    void stackTracesAreMaskedButNotTruncated() {
        String trace = "java.lang.IllegalStateException: card 4111111111111111\n\tat a.B.c(B.java:1)";

        assertThat(sanitizer.maskOnly(trace))
                .isEqualTo("java.lang.IllegalStateException: card 4111********1111\n\tat a.B.c(B.java:1)");
    }

    @Test
    void rejectsNegativeLength() {
        assertThatThrownBy(() -> new TextSanitizer(PatternMasker.none(), -1)).hasMessageContaining(">= 0");
        assertThat(TextSanitizer.none().isEmpty()).isTrue();
    }
}
