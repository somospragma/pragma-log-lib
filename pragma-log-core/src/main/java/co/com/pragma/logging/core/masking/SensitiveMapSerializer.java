package co.com.pragma.logging.core.masking;

import java.util.Map;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/** Writes maps masking the values whose key is a sensitive name (MSK-09). */
final class SensitiveMapSerializer extends ValueSerializer<Map<?, ?>> {

    private final LogMasker masker;

    SensitiveMapSerializer(LogMasker masker) {
        this.masker = masker;
    }

    @Override
    public void serialize(Map<?, ?> map, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeStartObject(map);
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String name = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value == null || !masker.isSensitiveName(name)) {
                gen.writeName(name);
                ctxt.writeValue(gen, value);
            } else if (!masker.hidesSensitiveNames()) {
                gen.writeName(name);
                gen.writeString(masker.maskByName(value));
            }
        }
        gen.writeEndObject();
    }

    @Override
    public boolean isEmpty(SerializationContext ctxt, Map<?, ?> value) {
        return value.isEmpty();
    }
}
