package co.com.pragma.logging.core.masking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PatternMaskerTest {

    @Test
    void masksCardsThatPassLuhnKeepingSeparators() {
        PatternMasker masker = PatternMasker.of(List.of("CARD"), '*');

        assertThat(masker.mask("paid with 4111111111111111 ok")).isEqualTo("paid with 4111********1111 ok");
        assertThat(masker.mask("card 4111 1111 1111 1111.")).isEqualTo("card 4111 **** **** 1111.");
        assertThat(masker.mask("order 1234567890123456")).isEqualTo("order 1234567890123456"); // fails Luhn
        assertThat(masker.mask("uuid 2026-11-02 phone 3001234567")).isEqualTo("uuid 2026-11-02 phone 3001234567");
    }

    @Test
    void masksEmailsAndJwts() {
        PatternMasker masker = PatternMasker.of(List.of("email", "JWT"), '*');

        assertThat(masker.mask("user ana.perez@acme.com logged"))
                .isEqualTo("user a********@acme.com logged");
        assertThat(masker.mask("Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJl end"))
                .isEqualTo("Bearer ******** end");
    }

    @Test
    void customRegexMasksTheFirstGroupOrTheWholeMatch() {
        PatternMasker masker = PatternMasker.of(List.of("password=(\\S+)", "\\b\\d{10}\\b"), '#');

        assertThat(masker.mask("login password=hunter2 doc 1020304050"))
                .isEqualTo("login password=####### doc ##########");
    }

    @Test
    void rejectsInvalidRegexAndBlankEntries() {
        assertThatThrownBy(() -> PatternMasker.of(List.of("(unclosed"), '*'))
                .hasMessageContaining("invalid regex '(unclosed'");
        assertThatThrownBy(() -> PatternMasker.of(List.of(" "), '*')).hasMessageContaining("blank");
    }

    @Test
    void noPatternsReturnsTheSameText() {
        assertThat(PatternMasker.of(List.of(), '*').mask("4111111111111111")).isEqualTo("4111111111111111");
        assertThat(PatternMasker.none().mask(null)).isNull();
    }
}
