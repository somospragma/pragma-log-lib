package co.com.pragma.logging.core.masking;

/** How a sensitive value is masked. */
public enum MaskType {
    /** Every character is replaced by the mask character. */
    FULL,
    /** The first {@code length} characters are masked. */
    PREFIX,
    /** The last {@code length} characters are masked. */
    SUFFIX,
    /** {@code length} characters stay visible at each end; everything in between is masked. */
    MIDDLE,
    /** The field is removed from the output. */
    HIDDEN,
    /** A strategy registered by name is used (see {@link MaskedLogField#strategy()}). */
    CUSTOM
}
