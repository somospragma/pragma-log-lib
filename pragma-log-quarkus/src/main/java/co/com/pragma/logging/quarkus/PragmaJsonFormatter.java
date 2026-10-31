package co.com.pragma.logging.quarkus;

import co.com.pragma.logging.core.StandardFields;
import co.com.pragma.logging.core.Throwables;
import co.com.pragma.logging.core.masking.LogMasker;
import co.com.pragma.logging.quarkus.slf4j.Slf4jLogRecord;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.MessageFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.logmanager.ExtFormatter;
import org.jboss.logmanager.ExtLogRecord;
import org.slf4j.event.KeyValuePair;
import org.slf4j.helpers.MessageFormatter;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON formatter of the Quarkus extension: one line per event with the Pragma schema, masking of message
 * arguments, MDC and key-values (MSK-07, MSK-09), the error summary and a bounded stack trace (ERR-01..04).
 * A failure while masking never loses the event: it is written without arguments and with
 * {@code pragmaLogError} (ALR-05).
 */
public final class PragmaJsonFormatter extends ExtFormatter {

    /** Field added to events that could not be masked; holds the error type. */
    static final String ERROR_FIELD = "pragmaLogError";

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    /** Logger class of slf4j-jboss-logmanager, which formats SLF4J arguments before any formatter. */
    static final String JBOSS_SLF4J_LOGGER = "org.slf4j.impl.Slf4jLogger";

    private final LogMasker masker;
    private final Set<String> hidden;
    private final Map<String, String> renames;
    private final Map<String, Object> staticFields;
    private final boolean includeStacktrace;
    private final int stacktraceMaxDepth;
    private final int stacktraceMaxLength;
    private final boolean stacktraceRootCauseFirst;
    private final ZoneId zone = ZoneId.systemDefault();

    /**
     * @param staticFields fields written in every event (app, version, host, pid, fixed fields), by schema name
     */
    PragmaJsonFormatter(LogMasker masker, Set<String> hidden, Map<String, String> renames,
            Map<String, Object> staticFields, boolean includeStacktrace, int stacktraceMaxDepth,
            int stacktraceMaxLength, boolean stacktraceRootCauseFirst) {
        this.masker = masker;
        this.hidden = Set.copyOf(hidden);
        this.renames = Map.copyOf(renames);
        this.staticFields = new LinkedHashMap<>(staticFields);
        this.includeStacktrace = includeStacktrace;
        this.stacktraceMaxDepth = stacktraceMaxDepth;
        this.stacktraceMaxLength = stacktraceMaxLength;
        this.stacktraceRootCauseFirst = stacktraceRootCauseFirst;
    }

    @Override
    public String format(ExtLogRecord record) {
        // LinkageError covers incompatible dependency versions: a logging call must never break the app (ADR-15).
        try {
            return write(record, null);
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            try {
                return write(record, e);
            } catch (RuntimeException | LinkageError | StackOverflowError again) {
                return "{\"" + ERROR_FIELD + "\":\"" + again.getClass().getName() + "\"}\n";
            }
        }
    }

    private String write(ExtLogRecord record, Throwable failure) {
        StringWriter out = new StringWriter(256);
        try (JsonGenerator json = JsonMapper.shared().createGenerator(out)) {
            json.writeStartObject();
            string(json, StandardFields.TIMESTAMP, TIMESTAMP.format(record.getInstant().atZone(zone)));
            string(json, StandardFields.MESSAGE, failure == null ? message(record) : record.getMessage());
            string(json, StandardFields.LOGGER, record.getLoggerName());
            string(json, StandardFields.THREAD, record.getThreadName());
            string(json, StandardFields.LEVEL, level(record.getLevel()));
            Throwable thrown = record.getThrown();
            if (thrown != null && includeStacktrace) {
                string(json, StandardFields.STACKTRACE,
                        Throwables.stackTrace(thrown, stacktraceMaxDepth, stacktraceMaxLength, stacktraceRootCauseFirst));
            }
            if (failure == null) {
                writeMdc(json, record.getMdcCopy());
                if (record instanceof Slf4jLogRecord slf4j) {
                    writeKeyValues(json, slf4j.keyValues());
                }
            }
            for (Map.Entry<String, Object> field : staticFields.entrySet()) {
                value(json, field.getKey(), field.getValue());
            }
            if (thrown != null) {
                writeError(json, thrown);
            }
            if (failure != null) {
                json.writeStringProperty(ERROR_FIELD, failure.getClass().getName());
            }
            json.writeEndObject();
        }
        return out.append('\n').toString();
    }

    /** The message with masked arguments; a template that cannot be applied is written as is. */
    private String message(ExtLogRecord record) {
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

    private void writeMdc(JsonGenerator json, Map<String, String> mdc) {
        if (mdc == null) {
            return;
        }
        for (Map.Entry<String, String> entry : mdc.entrySet()) {
            String key = entry.getKey();
            if (entry.getValue() == null || !masker.isSensitiveName(key)) {
                string(json, key, entry.getValue());
            } else if (!masker.hidesSensitiveNames()) {
                string(json, key, masker.maskByName(entry.getValue()));
            }
        }
    }

    private void writeKeyValues(JsonGenerator json, List<KeyValuePair> pairs) {
        for (KeyValuePair pair : pairs) {
            if (pair.value == null || !masker.isSensitiveName(pair.key)) {
                Object value = pair.value;
                value(json, pair.key, value == null || value instanceof CharSequence || value instanceof Number
                        || value instanceof Boolean ? value : masker.toTree(value));
            } else if (!masker.hidesSensitiveNames()) {
                string(json, pair.key, masker.maskByName(pair.value));
            }
        }
    }

    private void writeError(JsonGenerator json, Throwable thrown) {
        String name = nameOf(StandardFields.ERROR);
        if (name == null) {
            return;
        }
        json.writeName(name);
        json.writeStartObject();
        json.writeStringProperty("type", thrown.getClass().getName());
        if (thrown.getMessage() != null) {
            json.writeStringProperty("message", thrown.getMessage());
        }
        Throwable root = Throwables.rootCause(thrown);
        if (root != thrown) {
            json.writeStringProperty("rootCause", Throwables.describe(root));
        }
        json.writeEndObject();
    }

    private void string(JsonGenerator json, String field, String value) {
        String name = nameOf(field);
        if (name != null && value != null) {
            json.writeStringProperty(name, value);
        }
    }

    private void value(JsonGenerator json, String field, Object value) {
        String name = nameOf(field);
        if (name == null) {
            return;
        }
        json.writeName(name);
        if (value == null) {
            json.writeNull();
        } else if (value instanceof JsonNode node) {
            json.writeTree(node);
        } else if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            json.writeNumber(((Number) value).longValue());
        } else if (value instanceof BigDecimal decimal) {
            json.writeNumber(decimal);
        } else if (value instanceof BigInteger integer) {
            json.writeNumber(integer);
        } else if (value instanceof Number number) {
            json.writeNumber(number.doubleValue());
        } else if (value instanceof Boolean bool) {
            json.writeBoolean(bool);
        } else {
            json.writeString(value.toString());
        }
    }

    /** Output name of a field, or {@code null} when it is hidden (FLD-02, FLD-03). */
    private String nameOf(String field) {
        return hidden.contains(field) ? null : renames.getOrDefault(field, field);
    }

    private static String level(java.util.logging.Level level) {
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
