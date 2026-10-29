package co.com.pragma.logging.core.masking;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import java.time.temporal.TemporalAccessor;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Turns any object into a masked representation before it reaches a log (ADR-07).
 *
 * <ul>
 *   <li>{@link MaskedLogField} is applied while serializing, so the original object is never modified.</li>
 *   <li>Fields listed in {@link MaskingSettings#fields()} are masked by name at any depth, maps included.</li>
 *   <li>Reference cycles and excessive nesting produce markers instead of errors.</li>
 *   <li>Objects that cannot be serialized become {@code <ClassName>}: {@code toString()} is never used.</li>
 * </ul>
 *
 * <p>Uses its own {@link JsonMapper}, isolated from any mapper of the application. Thread-safe.
 */
public final class LogMasker {

    private final MaskingSettings settings;
    private final TextMasker textMasker;
    private final JsonMapper mapper;

    public LogMasker(MaskingSettings settings) {
        this.settings = settings;
        this.textMasker = new TextMasker(settings);
        this.mapper = JsonMapper.builder()
                .changeDefaultVisibility(checker -> checker
                        .withVisibility(PropertyAccessor.FIELD, Visibility.ANY)
                        .withVisibility(PropertyAccessor.GETTER, Visibility.NONE)
                        .withVisibility(PropertyAccessor.IS_GETTER, Visibility.NONE))
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                // Keep fields in declaration order, as developers read them in the code.
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, false)
                .addModule(new SimpleModule("pragma-log-masking")
                        .setSerializerModifier(new MaskingSerializerModifier(this, textMasker)))
                .build();
    }

    public MaskingSettings settings() {
        return settings;
    }

    /** Masked structured representation of {@code value}, suitable as a JSON field value. */
    public JsonNode toTree(Object value) {
        try {
            return mapper.valueToTree(value);
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            return JsonNodeFactory.instance.stringNode(placeholder(value));
        }
    }

    /**
     * Serializes a sample annotated object without catching errors, to detect at startup a classpath where
     * object masking cannot work (e.g. an incompatible {@code jackson-annotations} version).
     *
     * @throws RuntimeException or {@link LinkageError} describing the problem
     */
    public void verify() {
        mapper.valueToTree(new SelfCheck("value"));
    }

    private record SelfCheck(@MaskedLogField String secret) {
    }

    /** Masked text of {@code value}, used to substitute {@code {}} placeholders in log messages (MSK-07). */
    public String toText(Object value) {
        if (value == null || isScalar(value)) {
            return String.valueOf(value);
        }
        JsonNode tree = toTree(value);
        return tree.isString() ? tree.asString() : tree.toString();
    }

    /** Whether a field, MDC or key-value name must be masked by name. */
    public boolean isSensitiveName(String name) {
        return settings.isSensitiveName(name);
    }

    /** Whether values with a sensitive name must be removed instead of masked. */
    public boolean hidesSensitiveNames() {
        return settings.defaultType() == MaskType.HIDDEN;
    }

    /** Masks a value whose name is sensitive, using the default type. */
    public String maskByName(Object value) {
        if (value != null && isScalar(value)) {
            return textMasker.mask(String.valueOf(value), settings.defaultType(), -1, "");
        }
        return textMasker.fixedMask();
    }

    static boolean isScalar(Object value) {
        return value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value instanceof UUID
                || value instanceof TemporalAccessor;
    }

    private static String placeholder(Object value) {
        return value == null ? "null" : "<" + value.getClass().getSimpleName() + ">";
    }
}
