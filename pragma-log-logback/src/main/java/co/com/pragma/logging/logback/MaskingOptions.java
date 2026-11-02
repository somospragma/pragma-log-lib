package co.com.pragma.logging.logback;

import co.com.pragma.logging.core.masking.MaskType;
import co.com.pragma.logging.core.masking.MaskingSettings;
import co.com.pragma.logging.core.masking.MaskingStrategy;
import co.com.pragma.logging.core.masking.PatternMasker;
import co.com.pragma.logging.core.masking.TextSanitizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Masking properties shared by the JSON and text encoders, as set from {@code logback.xml}. */
final class MaskingOptions {

    private boolean enabled = true;
    private char character = MaskingSettings.DEFAULT_CHARACTER;
    private String defaultType = MaskType.FULL.name();
    private int defaultLength = MaskingSettings.DEFAULT_LENGTH;
    private Set<String> fields = MaskingSettings.DEFAULT_FIELDS;
    private int maxDepth = MaskingSettings.DEFAULT_MAX_DEPTH;
    private final Map<String, String> strategies = new LinkedHashMap<>();
    private final List<String> patterns = new ArrayList<>();
    private int maxFieldLength;

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @throws IllegalArgumentException when it is not exactly one character */
    void setCharacter(String character) {
        if (character == null || character.length() != 1) {
            throw new IllegalArgumentException("pragma-log: maskCharacter must be exactly one character, got '"
                    + character + "'");
        }
        this.character = character.charAt(0);
    }

    void setDefaultType(String defaultType) {
        this.defaultType = defaultType;
    }

    void setDefaultLength(int defaultLength) {
        this.defaultLength = defaultLength;
    }

    void setFields(String fields) {
        this.fields = Arrays.stream(fields.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    void addStrategy(String name, String className) {
        strategies.put(name, className);
    }

    void addPattern(String pattern) {
        patterns.add(pattern);
    }

    void setMaxFieldLength(int maxFieldLength) {
        this.maxFieldLength = maxFieldLength;
    }

    /**
     * Pattern masking (off with the masking master switch) and truncation.
     *
     * @throws IllegalArgumentException when the patterns or the length are invalid
     */
    TextSanitizer sanitizer() {
        return new TextSanitizer(enabled ? PatternMasker.of(patterns, character) : PatternMasker.none(),
                maxFieldLength);
    }

    /** @throws RuntimeException when the configuration is invalid */
    MaskingSettings build() {
        return new MaskingSettings(enabled, character, MaskType.valueOf(defaultType.trim().toUpperCase(Locale.ROOT)),
                defaultLength, fields, maxDepth, loadStrategies());
    }

    private Map<String, MaskingStrategy> loadStrategies() {
        Map<String, MaskingStrategy> loaded = new LinkedHashMap<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        strategies.forEach((name, className) -> {
            try {
                Class<?> type = Class.forName(className, true, classLoader);
                loaded.put(name, (MaskingStrategy) type.getDeclaredConstructor().newInstance());
            } catch (ReflectiveOperationException | ClassCastException e) {
                throw new IllegalArgumentException("cannot load mask strategy " + name + "=" + className, e);
            }
        });
        return loaded;
    }
}
