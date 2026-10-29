package co.com.pragma.logging.core.masking;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field (or record component) whose value must be masked whenever the owning object is logged.
 *
 * <p>Works on any field type. Scalar values (strings, numbers, booleans, enums, dates, UUIDs) are masked
 * character by character; any other value is replaced by a fixed mask. Inherited fields are honoured.
 *
 * <pre>{@code
 * record Card(@MaskedLogField(type = MaskType.MIDDLE, length = 4) String number,
 *             @MaskedLogField(type = MaskType.HIDDEN) String cvv) {}
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
public @interface MaskedLogField {

    /** Masking type. */
    MaskType type() default MaskType.FULL;

    /** Number of characters used by PREFIX, SUFFIX and MIDDLE. A negative value uses the configured default. */
    int length() default -1;

    /** Name of a registered custom strategy; only used when {@link #type()} is {@link MaskType#CUSTOM}. */
    String strategy() default "";
}
