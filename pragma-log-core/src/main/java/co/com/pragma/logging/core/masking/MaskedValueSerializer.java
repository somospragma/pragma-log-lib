package co.com.pragma.logging.core.masking;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/** Writes an annotated property already masked. */
final class MaskedValueSerializer extends ValueSerializer<Object> {

    private final TextMasker textMasker;
    private final MaskedLogField annotation;

    MaskedValueSerializer(TextMasker textMasker, MaskedLogField annotation) {
        this.textMasker = textMasker;
        this.annotation = annotation;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
        if (LogMasker.isScalar(value)) {
            gen.writeString(textMasker.mask(String.valueOf(value), annotation.type(), annotation.length(),
                    annotation.strategy()));
        } else {
            gen.writeString(textMasker.fixedMask());
        }
    }
}
