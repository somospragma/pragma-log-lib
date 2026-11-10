package co.com.pragma.logging.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.configuration.MemorySize;
import io.quarkus.runtime.configuration.MemorySizeConverter;
import io.smallrye.config.WithDefault;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Formatter;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Formatter creation from {@link PragmaLogConfig}, with the defaults of its annotations and some overrides. */
class PragmaLogRecorderTest {

    /**
     * A {@link PragmaLogConfig} as SmallRye would build it: {@code @WithDefault} values, empty optionals and maps,
     * and overrides by property path such as {@code csv.header}.
     */
    private static PragmaLogConfig config(Map<String, Object> overrides) {
        return proxy(PragmaLogConfig.class, "", overrides);
    }

    private static <T> T proxy(Class<T> type, String prefix, Map<String, Object> overrides) {
        Object instance = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return method.getName().equals("toString") ? type.getSimpleName() : null;
            }
            String path = prefix + kebab(method.getName());
            if (overrides.containsKey(path)) {
                Object value = overrides.get(path);
                return method.getReturnType() == Optional.class ? Optional.of(value) : value;
            }
            return defaultValue(method, path, overrides);
        });
        return type.cast(instance);
    }

    private static Object defaultValue(Method method, String path, Map<String, Object> overrides) {
        Class<?> type = method.getReturnType();
        WithDefault annotation = method.getAnnotation(WithDefault.class);
        if (annotation != null) {
            String value = annotation.value();
            if (type == boolean.class) {
                return Boolean.parseBoolean(value);
            }
            if (type == int.class) {
                return Integer.parseInt(value);
            }
            if (type.isEnum()) {
                return enumValue(type, value);
            }
            if (type == List.class) {
                return Arrays.asList(value.split(","));
            }
            if (type == MemorySize.class) {
                return new MemorySizeConverter().convert(value);
            }
            return value;
        }
        if (type == Optional.class) {
            return Optional.empty();
        }
        if (type == Map.class) {
            return Map.of();
        }
        if (type.isInterface()) {
            return proxy(type, path + ".", overrides);
        }
        throw new IllegalStateException("No default for " + path + " (" + method.getGenericReturnType() + ")");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> type, String value) {
        return Enum.valueOf((Class) type, value.toUpperCase(Locale.ROOT));
    }

    private static String kebab(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }

    private static Formatter formatter(Map<String, Object> overrides) {
        return new PragmaLogRecorder(new RuntimeValue<>(config(overrides))).formatter(false).getValue().orElseThrow();
    }

    private static String format(Formatter formatter) {
        ExtLogRecord record = new ExtLogRecord(Level.INFO, "Hello", "test");
        record.setLoggerName("co.com.pragma.Sample");
        return formatter.format(record);
    }

    @Test
    @DisplayName("JSON with the defaults; plain text when format=plain")
    void defaults() {
        assertThat(format(formatter(Map.of()))).startsWith("{\"timestamp\":").contains("\"message\":\"Hello\"");
        assertThat(formatter(Map.of("format", PragmaLogConfig.Format.PLAIN)))
                .isInstanceOf(PragmaTextFormatter.class);
        assertThat(new PragmaLogRecorder(new RuntimeValue<>(config(Map.of("enabled", false)))).formatter(true)
                .getValue()).isEmpty();
    }

    @Test
    @DisplayName("XML, CSV, pretty JSON and the field layout come from the configuration")
    void formatsAndLayout() {
        assertThat(format(formatter(Map.of("format", PragmaLogConfig.Format.XML)))).startsWith("<event>");
        Formatter csv = formatter(Map.of("format", PragmaLogConfig.Format.CSV, "csv.columns", List.of("level",
                "message"), "csv.header", true));
        assertThat(csv.getHead(null)).isEqualTo("level,message\n");
        assertThat(format(csv)).isEqualTo("INFO,Hello\n");
        assertThat(format(formatter(Map.of("json.pretty", true))).lines().count()).isGreaterThan(3);
        assertThat(format(formatter(Map.of("field-order", List.of("message"), "required-fields", List.of("userId"),
                "required-placeholder", "n/a")))).startsWith("{\"message\":\"Hello\"").contains("\"userId\":\"n/a\"");
    }

    @Test
    @DisplayName("invalid format settings stop startup with the property name (ADR-09)")
    void invalid() {
        assertThatThrownBy(() -> formatter(Map.of("format", PragmaLogConfig.Format.CUSTOM)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("pragma.log.custom-formatter");
        assertThatThrownBy(() -> formatter(Map.of("format", PragmaLogConfig.Format.CSV, "csv.delimiter", "::")))
                .hasMessageContaining("pragma.log.csv.delimiter");
        assertThatThrownBy(() -> formatter(Map.of("rename-fields", Map.of("a", "$.x..y"))))
                .hasMessageContaining("$.x..y");
    }

    @Test
    @DisplayName("the test configuration covers every property of PragmaLogConfig")
    void proxyCoversTheConfiguration() {
        PragmaLogConfig config = config(Map.of());
        for (Method method : PragmaLogConfig.class.getMethods()) {
            if (method.getParameterCount() == 0 && !method.isDefault()) {
                try {
                    method.invoke(config);
                } catch (ReflectiveOperationException e) {
                    throw new LinkageError("No test value for " + method.getName(), e);
                }
            }
        }
    }
}
