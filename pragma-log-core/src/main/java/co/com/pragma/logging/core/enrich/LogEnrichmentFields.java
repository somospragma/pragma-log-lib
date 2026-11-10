package co.com.pragma.logging.core.enrich;

import co.com.pragma.logging.core.masking.LogMasker;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.temporal.TemporalAccessor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Fields of a {@link LogEnrichment} method call, shared by the Spring and Quarkus interceptors (ENR-05). */
public final class LogEnrichmentFields {

    private LogEnrichmentFields() {
    }

    /**
     * Returns the fields of a call: class fields, method fields, then {@link LogField} parameters.
     *
     * @param targetClass class of the bean, for the class annotation
     * @param masker      writes object parameters as masked JSON
     */
    public static Map<String, String> of(Class<?> targetClass, Method method, Object[] arguments, LogMasker masker) {
        Map<String, String> fields = new LinkedHashMap<>();
        LogEnrichment onClass = targetClass == null ? null : targetClass.getAnnotation(LogEnrichment.class);
        if (onClass == null) {
            onClass = method.getDeclaringClass().getAnnotation(LogEnrichment.class);
        }
        if (onClass != null) {
            fixed(onClass.value(), fields);
        }
        LogEnrichment onMethod = method.getAnnotation(LogEnrichment.class);
        if (onMethod != null) {
            fixed(onMethod.value(), fields);
        }
        Annotation[][] parameters = method.getParameterAnnotations();
        for (int i = 0; i < parameters.length && arguments != null && i < arguments.length; i++) {
            for (Annotation annotation : parameters[i]) {
                if (annotation instanceof LogField field && arguments[i] != null && !field.value().isBlank()) {
                    fields.put(field.value().trim(), text(arguments[i], masker));
                }
            }
        }
        return fields;
    }

    /** {@code name=value} entries; malformed ones are ignored. */
    private static void fixed(String[] definitions, Map<String, String> fields) {
        for (String definition : definitions) {
            int separator = definition.indexOf('=');
            if (separator > 0) {
                fields.put(definition.substring(0, separator).trim(), definition.substring(separator + 1).trim());
            }
        }
    }

    private static String text(Object value, LogMasker masker) {
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?> || value instanceof UUID
                || value instanceof TemporalAccessor) {
            return value.toString();
        }
        return masker.toText(value);
    }
}
