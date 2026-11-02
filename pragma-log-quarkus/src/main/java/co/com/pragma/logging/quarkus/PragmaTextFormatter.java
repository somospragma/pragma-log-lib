package co.com.pragma.logging.quarkus;

import co.com.pragma.logging.core.Throwables;
import co.com.pragma.logging.core.format.PlainTextFormat;
import co.com.pragma.logging.core.masking.LogMasker;
import java.util.Map;
import java.util.Set;
import org.jboss.logmanager.ExtFormatter;
import org.jboss.logmanager.ExtLogRecord;

/**
 * Readable text formatter of the Quarkus extension for {@code pragma.log.format=plain} (FMT-01), with the same
 * masking as {@link PragmaJsonFormatter} and optional colors by level (FMT-05).
 */
public final class PragmaTextFormatter extends ExtFormatter {

    private final RecordMasking masking;
    private final PlainTextFormat format;
    private final Set<String> hidden;
    private final boolean includeStacktrace;
    private final int stacktraceMaxDepth;
    private final int stacktraceMaxLength;
    private final boolean stacktraceRootCauseFirst;

    PragmaTextFormatter(LogMasker masker, PlainTextFormat format, Set<String> hidden, boolean includeStacktrace,
            int stacktraceMaxDepth, int stacktraceMaxLength, boolean stacktraceRootCauseFirst) {
        this.masking = new RecordMasking(masker);
        this.format = format;
        this.hidden = Set.copyOf(hidden);
        this.includeStacktrace = includeStacktrace;
        this.stacktraceMaxDepth = stacktraceMaxDepth;
        this.stacktraceMaxLength = stacktraceMaxLength;
        this.stacktraceRootCauseFirst = stacktraceRootCauseFirst;
    }

    @Override
    public String format(ExtLogRecord record) {
        // A logging call must never break the application (ADR-15): without masking, the raw template is written.
        String message;
        Map<String, Object> fields;
        try {
            message = masking.message(record);
            fields = masking.fields(record);
            fields.keySet().removeAll(hidden);
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            message = record.getMessage();
            fields = Map.of(PragmaJsonFormatter.ERROR_FIELD, e.getClass().getName());
        }
        Throwable thrown = record.getThrown();
        String stacktrace = thrown != null && includeStacktrace
                ? Throwables.stackTrace(thrown, stacktraceMaxDepth, stacktraceMaxLength, stacktraceRootCauseFirst)
                : null;
        return format.format(record.getInstant(), RecordMasking.level(record.getLevel()), record.getThreadName(),
                record.getLoggerName(), message, fields, stacktrace);
    }
}
