package co.com.pragma.logging.core.masking;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/** Writes a property whose name is sensitive, masked with the default type (MSK-09). */
final class NameMaskedValueSerializer extends ValueSerializer<Object> {

    private final LogMasker masker;

    NameMaskedValueSerializer(LogMasker masker) {
        this.masker = masker;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeString(masker.maskByName(value));
    }
}
