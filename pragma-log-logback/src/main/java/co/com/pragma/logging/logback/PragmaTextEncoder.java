package co.com.pragma.logging.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;
import co.com.pragma.logging.core.format.ColorMode;
import co.com.pragma.logging.core.format.PlainTextFormat;
import co.com.pragma.logging.core.format.TimestampFormat;
import co.com.pragma.logging.core.alert.LogAlerts;
import co.com.pragma.logging.core.masking.LogMasker;
import co.com.pragma.logging.core.masking.MaskingSettings;
import co.com.pragma.logging.core.masking.TextSanitizer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.event.KeyValuePair;

/**
 * Logback encoder that writes a readable line per event for local development (FMT-01), with the same
 * masking as {@link PragmaJsonEncoder} (ADR-07) and optional colors by level (FMT-05).
 *
 * <pre>{@code
 * <encoder class="co.com.pragma.logging.logback.PragmaTextEncoder">
 *   <color>AUTO</color>                       <!-- AUTO, ALWAYS or NEVER; use NEVER for files -->
 *   <levelColor>INFO=#00aa88</levelColor>     <!-- name or hex; may be repeated -->
 *   <timestampPattern>HH:mm:ss.SSS</timestampPattern>
 *   <timeZone>UTC</timeZone>
 *   <maskFields>password,cardNumber</maskFields>
 * </encoder>
 * }</pre>
 */
public class PragmaTextEncoder extends EncoderBase<ILoggingEvent> {

    private final MaskingOptions masking = new MaskingOptions();
    private final AlertOptions alertOptions = new AlertOptions();
    private final Map<String, String> levelColors = new LinkedHashMap<>();
    private final Set<String> hiddenFields = new HashSet<>();
    private String color = ColorMode.AUTO.name();
    private String timestampPattern = TimestampFormat.PLAIN_DEFAULT;
    private String timeZone;
    private boolean includeStacktrace = true;
    private int stacktraceMaxDepth = PragmaJsonEncoder.DEFAULT_STACKTRACE_MAX_DEPTH;
    private int stacktraceMaxLength = PragmaJsonEncoder.DEFAULT_STACKTRACE_MAX_LENGTH;
    private boolean stacktraceRootCauseFirst;

    private final List<String> configurationErrors = new ArrayList<>();
    private EventMasker eventMasker;
    private LogAlerts alerts = LogAlerts.none();
    private PlainTextFormat format;
    private MaskingThrowableConverter throwableConverter;

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

    /** AUTO (default: only on a terminal, never with NO_COLOR), ALWAYS or NEVER. */
    public void setColor(String color) {
        this.color = color;
    }

    /** Color of a level as {@code LEVEL=color}, e.g. {@code INFO=blue} or {@code WARN=#ff8800}; may be repeated. */
    public void addLevelColor(String definition) {
        String[] pair = splitPair("levelColor", definition);
        if (pair != null) {
            levelColors.put(pair[0], pair[1]);
        }
    }

    /** {@link java.time.format.DateTimeFormatter} pattern or a constant in brackets, e.g. {@code [ISO_INSTANT]}. */
    public void setTimestampPattern(String timestampPattern) {
        this.timestampPattern = timestampPattern;
    }

    /** Zone id such as {@code UTC} or {@code America/Bogota}; the system zone by default. */
    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    /** Comma-separated names of MDC or key-value fields to leave out. */
    public void setHideFields(String hideFields) {
        Arrays.stream(hideFields.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .forEach(hiddenFields::add);
    }

    public void setIncludeStacktrace(boolean includeStacktrace) {
        this.includeStacktrace = includeStacktrace;
    }

    public void setStacktraceMaxDepth(int stacktraceMaxDepth) {
        this.stacktraceMaxDepth = stacktraceMaxDepth;
    }

    public void setStacktraceMaxLength(int stacktraceMaxLength) {
        this.stacktraceMaxLength = stacktraceMaxLength;
    }

    public void setStacktraceRootCauseFirst(boolean stacktraceRootCauseFirst) {
        this.stacktraceRootCauseFirst = stacktraceRootCauseFirst;
    }

    @Override
    public void start() {
        LogMasker masker = new LogMasker(buildMaskingSettings());
        try {
            masker.verify();
        } catch (RuntimeException | LinkageError e) {
            addWarn("pragma-log: object masking does not work on this classpath, objects will be logged as"
                    + " <ClassName>. Check that com.fasterxml.jackson.core:jackson-annotations is 2.21 or newer."
                    + " Cause: " + e, e);
        }
        eventMasker = new EventMasker(masker, buildSanitizer());
        alerts = buildAlerts();
        format = buildFormat();
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
        return null;
    }

    @Override
    public byte[] footerBytes() {
        return null;
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        // A logging call must never break the application (ADR-15).
        try {
            byte[] line = write(eventMasker.mask(event));
            alerts.checkSize(event.getLoggerName(), String.valueOf(event.getLevel()), line.length);
            return line;
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            addError("pragma-log: could not mask event, writing it without arguments", e);
            alerts.maskingError(event.getLoggerName(), e);
            try {
                return write(eventMasker.fallback(event, e));
            } catch (RuntimeException | LinkageError | StackOverflowError again) {
                addError("pragma-log: could not encode fallback event, dropping it", again);
                return new byte[0];
            }
        }
    }

    /** Configuration problems found so far; each was also reported through Logback's status. */
    public List<String> getConfigurationErrors() {
        return List.copyOf(configurationErrors);
    }

    private byte[] write(ILoggingEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        Map<String, String> mdc = event.getMDCPropertyMap();
        if (mdc != null) {
            mdc.forEach((name, value) -> putUnlessHidden(fields, name, value));
        }
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        if (pairs != null) {
            pairs.forEach(pair -> putUnlessHidden(fields, pair.key, pair.value));
        }
        String stacktrace = includeStacktrace && event.getThrowableProxy() != null
                ? throwableConverter.convert(event)
                : null;
        String line = format.format(event.getInstant(), event.getLevel().toString(), event.getThreadName(),
                event.getLoggerName(), event.getFormattedMessage(), fields, stacktrace);
        return line.getBytes(StandardCharsets.UTF_8);
    }

    private void putUnlessHidden(Map<String, Object> fields, String name, Object value) {
        if (!hiddenFields.contains(name)) {
            fields.put(name, value);
        }
    }

    /** Invalid masking is replaced by the secure defaults and reported (CFG-05). */
    private MaskingSettings buildMaskingSettings() {
        try {
            return masking.build();
        } catch (RuntimeException e) {
            configurationError("pragma-log: invalid masking configuration, using secure defaults: " + e.getMessage(), e);
            return MaskingSettings.defaults();
        }
    }

    /** Invalid format settings fall back to the defaults, without colors, and are reported. */
    private PlainTextFormat buildFormat() {
        try {
            boolean colors = ColorMode.valueOf(color.trim().toUpperCase(Locale.ROOT)).enabled();
            return new PlainTextFormat(TimestampFormat.of(timestampPattern, timeZone), colors, levelColors);
        } catch (RuntimeException e) {
            configurationError("pragma-log: invalid text format configuration, using defaults: " + e.getMessage(), e);
            return new PlainTextFormat(TimestampFormat.of(TimestampFormat.PLAIN_DEFAULT, null), false, Map.of());
        }
    }

    private void configurationError(String message, Throwable cause) {
        configurationErrors.add(message);
        addError(message, cause);
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
