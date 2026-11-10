package co.com.pragma.logging.core.masking;

/**
 * Custom masking algorithm, registered by name and selected with
 * {@code @MaskedLogField(type = MaskType.CUSTOM, strategy = "NAME")}.
 *
 * <p>Implementations must be pure functions and thread-safe, and need a public no-arg constructor
 * when they are registered by class name.
 */
@FunctionalInterface
public interface MaskingStrategy {

    /**
     * Returns the masked text.
     *
     * @param value    non-null text to mask
     * @param maskChar configured mask character
     * @param length   length from the annotation, or the configured default
     * @return the masked text
     */
    String mask(String value, char maskChar, int length);
}
