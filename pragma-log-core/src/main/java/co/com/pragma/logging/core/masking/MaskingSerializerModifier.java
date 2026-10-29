package co.com.pragma.logging.core.masking;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.databind.ser.bean.BeanSerializerBase;
import tools.jackson.databind.type.MapType;

/**
 * Applies masking while serializing: {@link MaskedLogField} first, then sensitive names on bean
 * properties and map keys. Also guards bean serializers against cycles and deep nesting.
 */
final class MaskingSerializerModifier extends ValueSerializerModifier {

    private static final long serialVersionUID = 1L;

    private final transient LogMasker masker;
    private final transient TextMasker textMasker;

    MaskingSerializerModifier(LogMasker masker, TextMasker textMasker) {
        this.masker = masker;
        this.textMasker = textMasker;
    }

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription.Supplier beanDesc,
            List<BeanPropertyWriter> beanProperties) {
        if (!masker.settings().enabled()) {
            return beanProperties;
        }
        List<BeanPropertyWriter> result = new ArrayList<>(beanProperties.size());
        for (BeanPropertyWriter writer : beanProperties) {
            MaskedLogField annotation = writer.getAnnotation(MaskedLogField.class);
            if (annotation != null) {
                // The annotation always wins over name-based masking.
                if (annotation.type() != MaskType.HIDDEN) {
                    writer.assignSerializer(new MaskedValueSerializer(textMasker, annotation));
                    result.add(writer);
                }
            } else if (masker.isSensitiveName(writer.getName())) {
                if (!masker.hidesSensitiveNames()) {
                    writer.assignSerializer(new NameMaskedValueSerializer(masker));
                    result.add(writer);
                }
            } else {
                result.add(writer);
            }
        }
        return result;
    }

    @Override
    public ValueSerializer<?> modifyMapSerializer(SerializationConfig config, MapType valueType,
            BeanDescription.Supplier beanDesc, ValueSerializer<?> serializer) {
        ValueSerializer<?> mapSerializer = masker.settings().enabled() ? new SensitiveMapSerializer(masker) : serializer;
        return CycleGuardSerializer.wrap(mapSerializer, masker.settings().maxDepth());
    }

    @Override
    public ValueSerializer<?> modifySerializer(SerializationConfig config, BeanDescription.Supplier beanDesc,
            ValueSerializer<?> serializer) {
        return serializer instanceof BeanSerializerBase
                ? CycleGuardSerializer.wrap(serializer, masker.settings().maxDepth())
                : serializer;
    }
}
