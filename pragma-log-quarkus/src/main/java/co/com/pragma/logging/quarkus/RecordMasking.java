package co.com.pragma.logging.quarkus;

import co.com.pragma.logging.core.masking.LogMasker;
import co.com.pragma.logging.core.masking.TextSanitizer;
import co.com.pragma.logging.quarkus.slf4j.Slf4jLogRecord;
import java.text.MessageFormat;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jboss.logmanager.ExtLogRecord;
import org.slf4j.event.KeyValuePair;
import org.slf4j.helpers.MessageFormatter;

/**
 * Masked view of a JBoss log record shared by the JSON and text formatters: message arguments (MSK-07) and
 * MDC and key-value fields (MSK-09).
 */
final class RecordMasking {

    /** Logger class of slf4j-jboss-logmanager, which formats SLF4J arguments before any formatter. */
    static final String JBOSS_SLF4J_LOGGER = "org.slf4j.impl.Slf4jLogger";

    private final LogMasker masker;
    private final TextSanitizer sanitizer;

    RecordMasking(LogMasker masker, TextSanitizer sanitizer) {
        this.masker = masker;
        this.sanitizer = sanitizer;
    }

    /** Pattern masking and truncation (MSK-10, FLD-08), also for stack traces and fallback messages. */
    TextSanitizer sanitizer() {
        return sanitizer;
    }

    /** The message with masked arguments and patterns, truncated. */
    String message(ExtLogRecord record) {
        return sanitizer.text(formattedMessage(record));
    }

    /** The message with masked arguments; a template that cannot be applied is written as is. */
    private String formattedMessage(ExtLogRecord record) {
        if (record instanceof Slf4jLogRecord slf4j) {
            Object[] arguments = slf4j.arguments();
            if (arguments == null || arguments.length == 0) {
                return slf4j.template();
            }
            Object[] masked = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                masked[i] = arguments[i] instanceof Throwable ? arguments[i].toString() : masker.toText(arguments[i]);
            }
            return MessageFormatter.basicArrayFormat(slf4j.template(), masked);
        }
        String template = record.getMessage();
        Object[] parameters = record.getParameters();
        if (template == null || parameters == null || parameters.length == 0) {
            return template;
        }
        if (JBOSS_SLF4J_LOGGER.equals(record.getLoggerClassName())) {
            return remaskFormatted(template, parameters);
        }
        Object[] masked = maskParameters(parameters);
        try {
            return switch (record.getFormatStyle()) {
                case PRINTF -> String.format(template, masked);
                case MESSAGE_FORMAT -> template.indexOf('{') >= 0 ? MessageFormat.format(template, masked) : template;
                case NO_FORMAT -> template;
            };
        } catch (IllegalArgumentException e) { // includes IllegalFormatException
            return template;
        }
    }

    /**
     * Defense in depth when slf4j-jboss-logmanager is bound instead of our SLF4J binding (e.g. on a test
     * classpath): its message is already formatted, so the text of each object argument is replaced by its
     * masked form, in order. Key-values cannot be recovered on that path.
     */
    private String remaskFormatted(String formatted, Object[] parameters) {
        StringBuilder message = new StringBuilder(formatted);
        int from = 0;
        for (Object parameter : parameters) {
            if (isSimple(parameter) || parameter instanceof Throwable || parameter.getClass().isArray()) {
                continue;
            }
            String text = String.valueOf(parameter);
            int at = message.indexOf(text, from);
            if (at >= 0) {
                String masked = masker.toText(parameter);
                message.replace(at, at + text.length(), masked);
                from = at + masked.length();
            }
        }
        return message.toString();
    }

    private static boolean isSimple(Object value) {
        return value == null || value instanceof Number || value instanceof CharSequence || value instanceof Boolean
                || value instanceof Character || value instanceof Enum || value instanceof TemporalAccessor
                || value instanceof Date;
    }

    /** JBoss Logging / JUL parameters: simple values stay as they are so printf conversions like %d still work. */
    private Object[] maskParameters(Object[] parameters) {
        Object[] masked = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Object parameter = parameters[i];
            if (isSimple(parameter)) {
                masked[i] = parameter;
            } else if (parameter instanceof Throwable) {
                masked[i] = parameter.toString();
            } else {
                masked[i] = masker.toText(parameter);
            }
        }
        return masked;
    }

    /** MDC entries, then SLF4J key-values, masked by name; objects become JSON trees (MSK-09). */
    Map<String, Object> fields(ExtLogRecord record) {
        Map<String, Object> fields = new LinkedHashMap<>();
        Map<String, String> mdc = record.getMdcCopy();
        if (mdc != null) {
            mdc.forEach((key, value) -> put(fields, key, value));
        }
        if (record instanceof Slf4jLogRecord slf4j) {
            for (KeyValuePair pair : slf4j.keyValues()) {
                Object value = pair.value;
                put(fields, pair.key, value == null || value instanceof CharSequence || value instanceof Number
                        || value instanceof Boolean ? value : masker.toTree(value));
            }
        }
        if (!sanitizer.isEmpty()) {
            fields.replaceAll((key, value) -> sanitizer.value(value));
        }
        return fields;
    }

    private void put(Map<String, Object> fields, String key, Object value) {
        if (value == null || !masker.isSensitiveName(key)) {
            fields.put(key, value);
        } else if (!masker.hidesSensitiveNames()) {
            fields.put(key, masker.maskByName(value));
        }
    }

    /** SLF4J level name of a JUL / JBoss level. */
    static String level(java.util.logging.Level level) {
        int value = level.intValue();
        if (value >= java.util.logging.Level.SEVERE.intValue()) {
            return "ERROR";
        }
        if (value >= java.util.logging.Level.WARNING.intValue()) {
            return "WARN";
        }
        if (value >= java.util.logging.Level.INFO.intValue()) {
            return "INFO";
        }
        if (value >= java.util.logging.Level.FINE.intValue()) {
            return "DEBUG";
        }
        return "TRACE";
    }
}
