package co.com.pragma.logging.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.SocketTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ThrowablesTest {

    private final IllegalStateException error =
            new IllegalStateException("Gateway timeout", new SocketTimeoutException("Read timed out"));

    @Test
    @DisplayName("finds the root cause and describes it")
    void rootCause() {
        assertThat(Throwables.describe(Throwables.rootCause(error)))
                .isEqualTo("java.net.SocketTimeoutException: Read timed out");
        assertThat(Throwables.rootCause(new RuntimeException())).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("stack trace keeps the JDK layout, bounded per exception")
    void boundedStackTrace() {
        String text = Throwables.stackTrace(error, 1, 10_000, false);

        assertThat(text).startsWith("java.lang.IllegalStateException: Gateway timeout\n\tat ");
        assertThat(text).contains("Caused by: java.net.SocketTimeoutException: Read timed out");
        assertThat(text.lines().filter(line -> line.startsWith("\tat "))).hasSize(2);
        assertThat(text).contains("more");
    }

    @Test
    @DisplayName("root cause first and maximum length")
    void rootCauseFirstAndMaxLength() {
        assertThat(Throwables.stackTrace(error, 5, 10_000, true))
                .startsWith("java.net.SocketTimeoutException")
                .contains("Wrapped by: java.lang.IllegalStateException");
        assertThat(Throwables.stackTrace(error, 50, 40, false)).hasSize(40).endsWith("...");
    }

    @Test
    @DisplayName("cyclic cause chains do not loop")
    void cyclicChain() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);

        assertThat(Throwables.stackTrace(a, 1, 10_000, false)).contains("Caused by: java.lang.RuntimeException: b");
    }
}
