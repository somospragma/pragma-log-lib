package co.com.pragma.logging.core.masking;

/** Applies a {@link MaskType} to a text, falling back to safe values instead of throwing. */
final class TextMasker {

    static final String UNMASKABLE = "[unmaskable]";
    private static final int FIXED_MASK_LENGTH = 8;

    private final MaskingSettings settings;
    private final String fixedMask;

    TextMasker(MaskingSettings settings) {
        this.settings = settings;
        this.fixedMask = String.valueOf(settings.character()).repeat(FIXED_MASK_LENGTH);
    }

    /** Mask used for non-scalar values: it does not reveal their size or content. */
    String fixedMask() {
        return fixedMask;
    }

    String mask(String value, MaskType type, int length, String strategyName) {
        char maskChar = settings.character();
        int effectiveLength = length < 0 ? settings.defaultLength() : length;
        try {
            return switch (type) {
                case FULL, HIDDEN -> BuiltInMasks.full(value, maskChar);
                case PREFIX -> BuiltInMasks.prefix(value, maskChar, effectiveLength);
                case SUFFIX -> BuiltInMasks.suffix(value, maskChar, effectiveLength);
                case MIDDLE -> BuiltInMasks.middle(value, maskChar, effectiveLength);
                case CUSTOM -> custom(value, strategyName, effectiveLength);
            };
        } catch (RuntimeException e) {
            return UNMASKABLE;
        }
    }

    private String custom(String value, String strategyName, int length) {
        MaskingStrategy strategy = settings.strategies().get(strategyName);
        if (strategy == null) {
            // Unknown strategy: fail safe, never leak the value.
            return BuiltInMasks.full(value, settings.character());
        }
        String masked = strategy.mask(value, settings.character(), length);
        return masked != null ? masked : UNMASKABLE;
    }
}
