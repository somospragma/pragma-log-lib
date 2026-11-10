package co.com.pragma.logging.quarkus;

import co.com.pragma.logging.core.StandardFields;
import co.com.pragma.logging.core.Throwables;
import co.com.pragma.logging.core.alert.LogAlerts;
import co.com.pragma.logging.core.masking.LogMasker;
import co.com.pragma.logging.core.masking.TextSanitizer;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jboss.logmanager.ExtFormatter;
import org.jboss.logmanager.ExtLogRecord;
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


    private final RecordMasking masking;
    private final LogAlerts alerts;
    private final Set<String> hidden;
    private final Map<String, String> renames;
    private final Map<String, Object> staticFields;
    private final boolean includeStacktrace;
    private final int stacktraceMaxDepth;
    private final int stacktraceMaxLength;
    private final boolean stacktraceRootCauseFirst;
    private final DateTimeFormatter timestamp;

    /**
     * @param timestamp    formatter with a zone (FMT-06)
     * @param staticFields fields written in every event (app, version, host, pid, fixed fields), by schema name
     */
    PragmaJsonFormatter(LogMasker masker, TextSanitizer sanitizer, LogAlerts alerts, DateTimeFormatter timestamp,
            Set<String> hidden, Map<String, String> renames,
            Map<String, Object> staticFields, boolean includeStacktrace, int stacktraceMaxDepth,
            int stacktraceMaxLength, boolean stacktraceRootCauseFirst) {
        this.masking = new RecordMasking(masker, sanitizer);
        this.alerts = alerts;
        this.timestamp = timestamp;
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
            String line = write(record, null);
            alerts.checkSize(record.getLoggerName(), RecordMasking.level(record.getLevel()), line);
            return line;
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            alerts.maskingError(record.getLoggerName(), e);
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
            string(json, StandardFields.TIMESTAMP, timestamp.format(record.getInstant()));
            string(json, StandardFields.MESSAGE, failure == null
                    ? masking.message(record)
                    : masking.sanitizer().text(record.getMessage()));
            string(json, StandardFields.LOGGER, record.getLoggerName());
            string(json, StandardFields.THREAD, record.getThreadName());
            string(json, StandardFields.LEVEL, RecordMasking.level(record.getLevel()));
            Throwable thrown = record.getThrown();
            if (thrown != null && includeStacktrace) {
                string(json, StandardFields.STACKTRACE,
                        masking.sanitizer().maskOnly(Throwables.stackTrace(thrown, stacktraceMaxDepth,
                                stacktraceMaxLength, stacktraceRootCauseFirst)));
            }
            if (failure == null) {
                for (Map.Entry<String, Object> field : masking.fields(record).entrySet()) {
                    value(json, field.getKey(), field.getValue());
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

    @SuppressWarnings("ReferenceEquality") // the root cause is the same exception when it has no cause
    private void writeError(JsonGenerator json, Throwable thrown) {
        String name = nameOf(StandardFields.ERROR);
        if (name == null) {
            return;
        }
        json.writeName(name);
        json.writeStartObject();
        json.writeStringProperty("type", thrown.getClass().getName());
        if (thrown.getMessage() != null) {
            json.writeStringProperty("message", masking.sanitizer().maskOnly(thrown.getMessage()));
        }
        Throwable root = Throwables.rootCause(thrown);
        if (root != thrown) {
            json.writeStringProperty("rootCause", masking.sanitizer().maskOnly(Throwables.describe(root)));
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
}
