package co.com.pragma.logging.quarkus;

import co.com.pragma.logging.core.StandardFields;
import co.com.pragma.logging.core.Throwables;
import co.com.pragma.logging.core.alert.LogAlerts;
import co.com.pragma.logging.core.layout.EventFields;
import co.com.pragma.logging.core.layout.StructuredEventWriter;
import co.com.pragma.logging.core.masking.LogMasker;
import co.com.pragma.logging.core.masking.TextSanitizer;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Handler;
import org.jboss.logmanager.ExtFormatter;
import org.jboss.logmanager.ExtLogRecord;

/**
 * Structured formatter of the Quarkus extension: one event per log with the Pragma schema in JSON, XML, CSV or a
 * custom format (FMT-02..04), masking of message arguments, MDC and key-values (MSK-07, MSK-09), the error summary
 * and a bounded stack trace (ERR-01..04). Fields are arranged like in Logback (FLD-02..07). A failure while masking
 * never loses the event: it is written without arguments and with {@code pragmaLogError} (ALR-05).
 */
public final class PragmaStructuredFormatter extends ExtFormatter {

    /** Field added to events that could not be masked; holds the error type. */
    static final String ERROR_FIELD = "pragmaLogError";

    private final RecordMasking masking;
    private final LogAlerts alerts;
    private final StructuredEventWriter writer;
    private final Map<String, Object> staticFields;
    private final boolean includeStacktrace;
    private final int stacktraceMaxDepth;
    private final int stacktraceMaxLength;
    private final boolean stacktraceRootCauseFirst;
    private final DateTimeFormatter timestamp;

    /**
     * Creates the formatter.
     *
     * @param writer       layout and output format
     * @param timestamp    formatter with a zone (FMT-06)
     * @param staticFields fields written in every event (app, version, host, pid, fixed fields), by schema name
     */
    PragmaStructuredFormatter(LogMasker masker, TextSanitizer sanitizer, LogAlerts alerts, StructuredEventWriter writer,
            DateTimeFormatter timestamp, Map<String, Object> staticFields, boolean includeStacktrace,
            int stacktraceMaxDepth, int stacktraceMaxLength, boolean stacktraceRootCauseFirst) {
        this.masking = new RecordMasking(masker, sanitizer);
        this.alerts = alerts;
        this.writer = writer;
        this.timestamp = timestamp;
        this.staticFields = new LinkedHashMap<>(staticFields);
        this.includeStacktrace = includeStacktrace;
        this.stacktraceMaxDepth = stacktraceMaxDepth;
        this.stacktraceMaxLength = stacktraceMaxLength;
        this.stacktraceRootCauseFirst = stacktraceRootCauseFirst;
    }

    /** The CSV header, written once at the start of a file. */
    @Override
    public String getHead(Handler handler) {
        String header = writer.header();
        return header == null ? "" : header;
    }

    @Override
    public String format(ExtLogRecord record) {
        // LinkageError covers incompatible dependency versions: a logging call must never break the app (ADR-15).
        try {
            return write(record, null);
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
        String level = RecordMasking.level(record.getLevel());
        EventFields fields = new EventFields()
                .standard(StandardFields.TIMESTAMP, timestamp.format(record.getInstant()))
                .standard(StandardFields.LEVEL, level)
                .standard(StandardFields.LOGGER, record.getLoggerName())
                .standard(StandardFields.THREAD, record.getThreadName())
                .standard(StandardFields.MESSAGE, failure == null
                        ? masking.message(record)
                        : masking.sanitizer().text(record.getMessage()));
        if (failure == null) {
            masking.fields(record).forEach(fields::dynamic);
        }
        staticFields.forEach(fields::fixed);
        Throwable thrown = record.getThrown();
        if (thrown != null) {
            fields.standard(StandardFields.ERROR, error(thrown));
            if (includeStacktrace) {
                fields.standard(StandardFields.STACKTRACE, masking.sanitizer().maskOnly(Throwables.stackTrace(thrown,
                        stacktraceMaxDepth, stacktraceMaxLength, stacktraceRootCauseFirst)));
            }
        }
        if (failure != null) {
            fields.standard(ERROR_FIELD, failure.getClass().getName());
        }
        return writer.write(fields, record.getLoggerName(), level);
    }

    @SuppressWarnings("ReferenceEquality") // the root cause is the same exception when it has no cause
    private Map<String, Object> error(Throwable thrown) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", thrown.getClass().getName());
        if (thrown.getMessage() != null) {
            error.put("message", masking.sanitizer().maskOnly(thrown.getMessage()));
        }
        Throwable root = Throwables.rootCause(thrown);
        if (root != thrown) {
            error.put("rootCause", masking.sanitizer().maskOnly(Throwables.describe(root)));
        }
        return error;
    }
}
