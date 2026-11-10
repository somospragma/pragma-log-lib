package co.com.pragma.logging.core.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventLayoutTest {

    private static EventFields sample() {
        return new EventFields()
                .standard("timestamp", "t")
                .standard("level", "INFO")
                .standard("message", "m")
                .dynamic("requestId", "r-1")
                .dynamic("orderId", "o-1")
                .fixed("app", "orders");
    }

    @Test
    @DisplayName("without settings, fields keep their natural order")
    void defaults() {
        assertThat(EventLayout.defaults().arrange(sample()).fields().keySet())
                .containsExactly("timestamp", "level", "message", "requestId", "orderId", "app");
    }

    @Test
    @DisplayName("standard and fixed fields win over MDC or key-values with the same name")
    void precedence() {
        EventFields fields = new EventFields().standard("level", "INFO").dynamic("level", "fake").fixed("app", "a")
                .dynamic("app", "fake").dynamic("x", 1).dynamic("x", 2);

        assertThat(EventLayout.defaults().arrange(fields).fields())
                .containsEntry("level", "INFO").containsEntry("app", "a").containsEntry("x", 2);
    }

    @Test
    @DisplayName("hidden and renamed fields (FLD-02, FLD-03)")
    void hideAndRename() {
        EventLayout layout = new EventLayout(Set.of("orderId"), Map.of("level", "severity"), List.of(), List.of(),
                null, null);

        assertThat(layout.arrange(sample()).fields().keySet())
                .containsExactly("timestamp", "severity", "message", "requestId", "app");
    }

    @Test
    @DisplayName("$.a.b names nest fields; a name already taken keeps the field flat (FLD-05b)")
    @SuppressWarnings("unchecked")
    void nested() {
        EventLayout layout = new EventLayout(Set.of(), Map.of("requestId", "$.http.request.id",
                "orderId", "$.http.order", "app", "$.message.app"), List.of(), List.of(), null, null);

        Map<String, Object> fields = layout.arrange(sample()).fields();

        Map<String, Object> http = (Map<String, Object>) fields.get("http");
        assertThat(((Map<String, Object>) http.get("request")).get("id")).isEqualTo("r-1");
        assertThat(http.get("order")).isEqualTo("o-1");
        assertThat(fields.get("message")).isEqualTo("m");
        assertThat(fields.get("message.app")).isEqualTo("orders");
    }

    @Test
    @DisplayName("ordered fields come first, by original or new name; the rest keep their order (FLD-04)")
    void order() {
        EventLayout layout = new EventLayout(Set.of(), Map.of("message", "msg"), List.of("msg", "app", "missing",
                "level"), List.of(), null, null);

        assertThat(layout.arrange(sample()).fields().keySet())
                .containsExactly("msg", "app", "level", "timestamp", "requestId", "orderId");
    }

    @Test
    @DisplayName("missing required fields get the placeholder and are reported (FLD-07)")
    void required() {
        EventLayout layout = new EventLayout(Set.of(), Map.of(), List.of(), List.of("requestId", "userId", "level"),
                "n/a", null);

        EventLayout.Arranged arranged = layout.arrange(sample().dynamic("level", null));

        assertThat(arranged.fields()).containsEntry("userId", "n/a").containsEntry("requestId", "r-1");
        assertThat(arranged.missingRequired()).containsExactly("userId");
        assertThat(new EventLayout(Set.of(), Map.of(), List.of(), List.of("x"), null, null).arrange(sample()).fields())
                .containsEntry("x", EventLayout.DEFAULT_PLACEHOLDER);
    }

    @Test
    @DisplayName("extra groups MDC and key-values, except fields the configuration names (FLD-06)")
    void extra() {
        EventLayout layout = new EventLayout(Set.of(), Map.of(), List.of("timestamp"), List.of("requestId"), null,
                "extra");

        Map<String, Object> fields = layout.arrange(sample().dynamic("channel", "web")).fields();

        assertThat(fields.keySet()).containsExactly("timestamp", "level", "message", "requestId", "extra", "app");
        assertThat(fields.get("extra")).isEqualTo(Map.of("orderId", "o-1", "channel", "web"));
        assertThat(new EventLayout(Set.of(), Map.of(), List.of(), List.of(), null, "extra")
                .arrange(new EventFields().standard("level", "INFO")).fields()).doesNotContainKey("extra");
    }

    @Test
    @DisplayName("the extra object can be nested and ordered")
    void extraNestedAndOrdered() {
        EventLayout layout = new EventLayout(Set.of(), Map.of(), List.of("$.context.extra", "level"), List.of(),
                null, "$.context.extra");

        Map<String, Object> fields = layout.arrange(sample()).fields();

        assertThat(fields.keySet()).first().isEqualTo("context");
        assertThat(fields.get("context")).isEqualTo(Map.of("extra", Map.of("requestId", "r-1", "orderId", "o-1")));
    }

    @Test
    @DisplayName("malformed paths and blank names are rejected")
    void invalid() {
        assertThatThrownBy(() -> new EventLayout(Set.of(), Map.of("a", "$.x..y"), List.of(), List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("$.x..y");
        assertThatThrownBy(() -> new EventLayout(Set.of(), Map.of("a", " "), List.of(), List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("accessors expose the configuration")
    void accessors() {
        EventLayout layout = new EventLayout(Set.of("a"), Map.of("b", "c"), List.of("d"), List.of("e"), "p", " x ");

        assertThat(layout.hidden()).containsExactly("a");
        assertThat(layout.isHidden("a")).isTrue();
        assertThat(layout.nameOf("b")).isEqualTo("c");
        assertThat(layout.renames()).containsEntry("b", "c");
        assertThat(layout.order()).containsExactly("d");
        assertThat(layout.required()).containsExactly("e");
        assertThat(layout.placeholder()).isEqualTo("p");
        assertThat(layout.extraField()).isEqualTo("x");
    }
}
