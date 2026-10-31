package co.com.pragma.logging.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PathPatternTest {

    @Test
    void matchesSubtreesSegmentsAndExactPaths() {
        assertThat(PathPattern.matches("/q/**", "/q/health/live")).isTrue();
        assertThat(PathPattern.matches("/q/**", "/q")).isTrue();
        assertThat(PathPattern.matches("/q/**", "/quotes")).isFalse();
        assertThat(PathPattern.matches("/orders/*/items", "/orders/7/items")).isTrue();
        assertThat(PathPattern.matches("/orders/*/items", "/orders/7/8/items")).isFalse();
        assertThat(PathPattern.matches("/health", "/health")).isTrue();
        assertThat(PathPattern.matches("/health", "/healthz")).isFalse();
    }
}
