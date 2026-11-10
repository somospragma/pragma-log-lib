package co.com.pragma.logging.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;
import co.com.pragma.logging.core.HostName;
import co.com.pragma.logging.core.PragmaLogSettings;
import co.com.pragma.logging.core.StandardFields;
import co.com.pragma.logging.core.alert.LogAlerts;
import co.com.pragma.logging.core.format.JsonEventFormatter;
import co.com.pragma.logging.core.format.LogEventFormatter;
import co.com.pragma.logging.core.format.StructuredFormat;
import co.com.pragma.logging.core.format.TimestampFormat;
import co.com.pragma.logging.core.layout.EventFields;
import co.com.pragma.logging.core.layout.EventLayout;
import co.com.pragma.logging.core.layout.StructuredEventWriter;
import co.com.pragma.logging.core.masking.LogMasker;
import co.com.pragma.logging.core.masking.MaskingSettings;
import co.com.pragma.logging.core.masking.TextSanitizer;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.event.KeyValuePair;

/**
 * Logback encoder that writes one structured event per log following the Pragma log schema, in JSON (compact or
 * pretty), XML, CSV or a custom {@link LogEventFormatter} (FMT-02..04).
 *
 * <p>MDC entries and SLF4J key-values become fields. Message arguments, MDC and key-values are masked before
 * writing (ADR-07); if masking fails the event is still written, without its arguments (ADR-15). Fields can be
 * hidden, renamed, nested, ordered, required or grouped in an {@code extra} object (FLD-02..07).
 *
 * <pre>{@code
 * <encoder class="co.com.pragma.logging.logback.PragmaStructuredEncoder">
 *   <appName>my-service</appName>
 *   <format>json</format>                      <!-- json, xml, csv or custom -->
 *   <pretty>false</pretty>
 *   <maskFields>password,cardNumber,documentId</maskFields>
 *   <field>env=prod</field>
 *   <hideFields>thread</hideFields>
 *   <renameField>requestId=$.http.request.id</renameField>
 *   <fieldOrder>timestamp,level,message</fieldOrder>
 *   <requiredFields>requestId</requiredFields>
 *   <extraField>extra</extraField>
 * </encoder>
 * }</pre>
 */
public class PragmaStructuredEncoder extends EncoderBase<ILoggingEvent> {

    static final int DEFAULT_STACKTRACE_MAX_DEPTH = 50;
    static final int DEFAULT_STACKTRACE_MAX_LENGTH = 16_384;

    private String appName = PragmaLogSettings.DEFAULT_APP_NAME;
    private String appVersion;
    private boolean includeHost = true;
    private boolean includePid;
    private final MaskingOptions masking = new MaskingOptions();
    private final AlertOptions alertOptions = new AlertOptions();
    private final Map<String, String> fixedFields = new LinkedHashMap<>();
    private final Set<String> hiddenFields = new LinkedHashSet<>();
    private final Map<String, String> renames = new LinkedHashMap<>();
    private final List<String> fieldOrder = new ArrayList<>();
    private final List<String> requiredFields = new ArrayList<>();
    private String requiredPlaceholder;
    private String extraField;
    private String format = StructuredFormat.Type.JSON.name();
    private boolean pretty;
    private final List<String> csvColumns = new ArrayList<>();
    private String csvDelimiter = ",";
    private boolean csvHeader;
    private String customFormatter;
    private String timestampPattern = TimestampFormat.JSON_DEFAULT;
    private String timeZone;
    private boolean includeStacktrace = true;
    private int stacktraceMaxDepth = DEFAULT_STACKTRACE_MAX_DEPTH;
    private int stacktraceMaxLength = DEFAULT_STACKTRACE_MAX_LENGTH;
    private boolean stacktraceRootCauseFirst;

    private final List<String> configurationErrors = new ArrayList<>();
    private EventMasker eventMasker;
    private StructuredEventWriter writer;
    private LogAlerts alerts = LogAlerts.none();
    private DateTimeFormatter timestamp;
    private Map<String, Object> staticFields = Map.of();
    private MaskingThrowableConverter throwableConverter;

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getAppName() {
        return appName;
    }

    /** Value of the "version" field; omitted when not set (FLD-12). */
    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    /** Whether the "host" field is added: HOSTNAME (the pod name in Kubernetes) or the local host name. */
    public void setIncludeHost(boolean includeHost) {
        this.includeHost = includeHost;
    }

    /** Whether the "pid" field is added. */
    public void setIncludePid(boolean includePid) {
        this.includePid = includePid;
    }

    public void setMaskingEnabled(boolean maskingEnabled) {
        masking.setEnabled(maskingEnabled);
    }

    public void setMaskCharacter(String maskCharacter) {
        try {
            masking.setCharacter(maskCharacter);
        } catch (IllegalArgumentException e) {
            configurationError(e.getMessage(), null);
        }
    }

    public void setMaskDefaultType(String maskDefaultType) {
        masking.setDefaultType(maskDefaultType);
    }

    public void setMaskDefaultLength(int maskDefaultLength) {
        masking.setDefaultLength(maskDefaultLength);
    }

    /** Comma-separated field names masked by name; replaces the default list. */
    public void setMaskFields(String maskFields) {
        masking.setFields(maskFields);
    }

    public void setMaskMaxDepth(int maskMaxDepth) {
        masking.setMaxDepth(maskMaxDepth);
    }

    /** Registers a custom strategy as {@code NAME=fully.qualified.ClassName}; may be repeated. */
    public void addMaskStrategy(String definition) {
        String[] pair = splitPair("maskStrategy", definition);
        if (pair != null) {
            masking.addStrategy(pair[0], pair[1]);
        }
    }

    /**
     * Pattern masked in messages, field values and stack traces: CARD, EMAIL, JWT or a regex; may be repeated
     * (MSK-10).
     */
    public void addMaskPattern(String maskPattern) {
        masking.addPattern(maskPattern);
    }

    /** Maximum characters of the message and of each text field; longer ones are truncated. 0, the default, for no limit (FLD-08). */
    public void setMaxFieldLength(int maxFieldLength) {
        masking.setMaxFieldLength(maxFieldLength);
    }

    /** UTF-8 bytes of an event above which alert listeners are notified; 0, the default, disables it (ALR-01). */
    public void setMaxLogSize(long maxLogSize) {
        alertOptions.setMaxLogSize(maxLogSize);
    }

    /** Class name of a {@code LogAlertListener}; may be repeated (ALR-02). */
    public void addAlertListener(String className) {
        alertOptions.addListener(className);
    }

    /** Whether alerts are also written to standard error, at most once per minute per logger. Default true. */
    public void setAlertConsole(boolean alertConsole) {
        alertOptions.setConsole(alertConsole);
    }

    /** Fixed field as {@code name=value}, e.g. {@code env=prod}; may be repeated (FLD-05). */
    public void addField(String definition) {
        String[] pair = splitPair("field", definition);
        if (pair != null) {
            fixedFields.put(pair[0], pair[1]);
        }
    }

    /** Comma-separated names of fields to remove: standard, fixed, MDC or key-value fields (FLD-02). */
    public void setHideFields(String hideFields) {
        hiddenFields.addAll(list(hideFields));
    }

    /**
     * Renames a field as {@code current=new}, e.g. {@code level=severity}; a new name such as
     * {@code $.http.request.id} nests it (FLD-03, FLD-05b). May be repeated.
     */
    public void addRenameField(String definition) {
        String[] pair = splitPair("renameField", definition);
        if (pair != null) {
            renames.put(pair[0], pair[1]);
        }
    }

    /** Comma-separated fields written first, in that order (FLD-04). */
    public void setFieldOrder(String fieldOrder) {
        this.fieldOrder.addAll(list(fieldOrder));
    }

    /** Comma-separated fields every event must have; missing ones get the placeholder and an alert (FLD-07). */
    public void setRequiredFields(String requiredFields) {
        this.requiredFields.addAll(list(requiredFields));
    }

    /** Value of a missing required field; {@code unknown} by default. */
    public void setRequiredPlaceholder(String requiredPlaceholder) {
        this.requiredPlaceholder = requiredPlaceholder;
    }

    /** Name of the object that groups MDC and key-value fields, e.g. {@code extra}; at the root when not set (FLD-06). */
    public void setExtraField(String extraField) {
        this.extraField = extraField;
    }

    /** {@code json} (default), {@code xml}, {@code csv} or {@code custom} (FMT-02..04). */
    public void setFormat(String format) {
        this.format = format;
    }

    /** JSON over several lines, for local reading (FMT-03). */
    public void setPretty(boolean pretty) {
        this.pretty = pretty;
    }

    /** Comma-separated CSV columns, by output name; nested fields with dots. */
    public void setCsvColumns(String csvColumns) {
        this.csvColumns.addAll(list(csvColumns));
    }

    public void setCsvDelimiter(String csvDelimiter) {
        this.csvDelimiter = csvDelimiter;
    }

    /** Whether a CSV file starts with the column names. */
    public void setCsvHeader(boolean csvHeader) {
        this.csvHeader = csvHeader;
    }

    /** Class name of a {@link LogEventFormatter}, for {@code format=custom}. */
    public void setCustomFormatter(String customFormatter) {
        this.customFormatter = customFormatter;
    }

    /** {@link java.time.format.DateTimeFormatter} pattern or a constant in brackets, e.g. {@code [ISO_INSTANT]} (FMT-06). */
    public void setTimestampPattern(String timestampPattern) {
        this.timestampPattern = timestampPattern;
    }

    /** Zone id such as {@code UTC} or {@code America/Bogota}; the system zone by default (FMT-06). */
    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    /** When {@code false}, only the {@code error} summary is written, without the stack trace (ERR-01). */
    public void setIncludeStacktrace(boolean includeStacktrace) {
        this.includeStacktrace = includeStacktrace;
    }

    /** Maximum frames written per exception in the chain (ERR-03). */
    public void setStacktraceMaxDepth(int stacktraceMaxDepth) {
        this.stacktraceMaxDepth = stacktraceMaxDepth;
    }

    /** Maximum characters of the whole stack trace (ERR-03). */
    public void setStacktraceMaxLength(int stacktraceMaxLength) {
        this.stacktraceMaxLength = stacktraceMaxLength;
    }

    public void setStacktraceRootCauseFirst(boolean stacktraceRootCauseFirst) {
        this.stacktraceRootCauseFirst = stacktraceRootCauseFirst;
    }

    @Override
    public void start() {
        PragmaLogSettings settings = new PragmaLogSettings(appName, buildMaskingSettings());
        LogMasker masker = new LogMasker(settings.masking());
        verifyMasking(masker);
        eventMasker = new EventMasker(masker, buildSanitizer());
        alerts = buildAlerts();
        writer = new StructuredEventWriter(buildLayout(), buildFormatter(), alerts);
        timestamp = buildTimestamp();
        staticFields = staticFields(settings.appName());
        throwableConverter = new MaskingThrowableConverter(eventMasker.sanitizer(), stacktraceMaxDepth,
                stacktraceMaxLength, stacktraceRootCauseFirst);
        throwableConverter.setContext(getContext());
        throwableConverter.start();
        super.start();
    }

    @Override
    public void stop() {
        if (throwableConverter != null) {
            throwableConverter.stop();
        }
        super.stop();
    }

    @Override
    public byte[] headerBytes() {
        String header = writer == null ? null : writer.header();
        return header == null ? null : header.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] footerBytes() {
        return null;
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        // LinkageError covers incompatible dependency versions on the application classpath:
        // a logging call must never break the application (ADR-15).
        ILoggingEvent masked;
        try {
            masked = eventMasker.mask(event);
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            addError("pragma-log: could not mask event, writing it without arguments", e);
            return encodeFallback(event, e);
        }
        try {
            return write(masked, null);
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            addError("pragma-log: could not encode event, writing it without arguments", e);
            return encodeFallback(event, e);
        }
    }

    /** Configuration problems found so far; each was also reported through Logback's status. */
    public List<String> getConfigurationErrors() {
        return List.copyOf(configurationErrors);
    }

    private byte[] write(ILoggingEvent event, Throwable failure) {
        EventFields fields = new EventFields()
                .standard(StandardFields.TIMESTAMP, timestamp.format(event.getInstant()))
                .standard(StandardFields.LEVEL, String.valueOf(event.getLevel()))
                .standard(StandardFields.LOGGER, event.getLoggerName())
                .standard(StandardFields.THREAD, event.getThreadName())
                .standard(StandardFields.MESSAGE, event.getFormattedMessage());
        if (failure == null) {
            Map<String, String> mdc = event.getMDCPropertyMap();
            if (mdc != null) {
                mdc.forEach(fields::dynamic);
            }
            List<KeyValuePair> pairs = event.getKeyValuePairs();
            if (pairs != null) {
                pairs.forEach(pair -> fields.dynamic(pair.key, pair.value));
            }
        }
        staticFields.forEach(fields::fixed);
        if (event.getThrowableProxy() != null) {
            fields.standard(StandardFields.ERROR, ErrorSummary.of(event.getThrowableProxy(), eventMasker.sanitizer()));
            if (includeStacktrace) {
                fields.standard(StandardFields.STACKTRACE, throwableConverter.convert(event));
            }
        }
        if (failure != null) {
            fields.standard(EventMasker.ERROR_FIELD, failure.getClass().getName());
        }
        String text = writer.write(fields, event.getLoggerName(), String.valueOf(event.getLevel()));
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] encodeFallback(ILoggingEvent event, Throwable error) {
        alerts.maskingError(event.getLoggerName(), error);
        try {
            return write(eventMasker.fallback(event, error), error);
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            addError("pragma-log: could not encode fallback event, dropping it", e);
            return new byte[0];
        }
    }

    private void configurationError(String message, Throwable cause) {
        configurationErrors.add(message);
        addError(message, cause);
    }

    /** A warning, not an error: frameworks such as Spring Boot abort startup on logging errors. */
    private void verifyMasking(LogMasker masker) {
        try {
            masker.verify();
        } catch (RuntimeException | LinkageError e) {
            addWarn("pragma-log: object masking does not work on this classpath, objects will be logged as"
                    + " <ClassName>. Check that com.fasterxml.jackson.core:jackson-annotations is 2.21 or newer. Cause: "
                    + e, e);
        }
    }

    /**
     * Invalid masking configuration is reported loudly through Logback's status and replaced by the
     * secure defaults: masking stays on (CFG-05).
     */
    private MaskingSettings buildMaskingSettings() {
        try {
            return masking.build();
        } catch (RuntimeException e) {
            configurationError("pragma-log: invalid masking configuration, using secure defaults: " + e.getMessage(), e);
            return MaskingSettings.defaults();
        }
    }

    /** Invalid patterns or length are reported; the event is then written without them. */
    private TextSanitizer buildSanitizer() {
        try {
            return masking.sanitizer();
        } catch (RuntimeException e) {
            configurationError("pragma-log: invalid maskPattern or maxFieldLength: " + e.getMessage(), e);
            return TextSanitizer.none();
        }
    }

    private LogAlerts buildAlerts() {
        try {
            return alertOptions.build();
        } catch (RuntimeException e) {
            configurationError("pragma-log: invalid alert configuration: " + e.getMessage(), e);
            return LogAlerts.none();
        }
    }

    /** An invalid layout is reported and replaced by the default one. */
    private EventLayout buildLayout() {
        Set<String> hidden = new LinkedHashSet<>(hiddenFields);
        if (!includeStacktrace) {
            hidden.add(StandardFields.STACKTRACE);
        }
        try {
            return new EventLayout(hidden, renames, fieldOrder, requiredFields, requiredPlaceholder, extraField);
        } catch (RuntimeException e) {
            configurationError("pragma-log: invalid field layout: " + e.getMessage(), e);
            return new EventLayout(hidden, Map.of(), List.of(), List.of(), null, null);
        }
    }

    /** An invalid format is reported and replaced by compact JSON. */
    private LogEventFormatter buildFormatter() {
        try {
            return StructuredFormat.create(StructuredFormat.type(format), pretty, csvColumns, csvDelimiter, csvHeader,
                    customFormatter, Thread.currentThread().getContextClassLoader());
        } catch (RuntimeException e) {
            configurationError("pragma-log: invalid format, using JSON: " + e.getMessage(), e);
            return new JsonEventFormatter(false);
        }
    }

    private DateTimeFormatter buildTimestamp() {
        try {
            return TimestampFormat.of(timestampPattern, timeZone);
        } catch (RuntimeException e) {
            configurationError("pragma-log: invalid timestamp settings, using ISO-8601: " + e.getMessage(), e);
            return TimestampFormat.of(TimestampFormat.JSON_DEFAULT, null);
        }
    }

    private Map<String, Object> staticFields(String app) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(StandardFields.APP, app);
        if (appVersion != null && !appVersion.isBlank()) {
            fields.put(StandardFields.VERSION, appVersion.trim());
        }
        if (includeHost) {
            fields.put(StandardFields.HOST, HostName.resolve());
        }
        if (includePid) {
            fields.put(StandardFields.PID, ProcessHandle.current().pid());
        }
        fields.putAll(fixedFields);
        fields.values().removeIf(value -> value == null);
        return Collections.unmodifiableMap(fields);
    }

    private static List<String> list(String commaSeparated) {
        if (commaSeparated == null) {
            return List.of();
        }
        return Arrays.stream(commaSeparated.split(",", -1))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toList();
    }

    /** Parses {@code name=value}; reports and returns {@code null} when malformed. */
    private String[] splitPair(String property, String definition) {
        int separator = definition == null ? -1 : definition.indexOf('=');
        if (separator <= 0 || separator == definition.length() - 1) {
            configurationError("pragma-log: " + property + " must look like name=value, got '" + definition + "'",
                    null);
            return null;
        }
        return new String[] {definition.substring(0, separator).trim(), definition.substring(separator + 1).trim()};
    }
}
