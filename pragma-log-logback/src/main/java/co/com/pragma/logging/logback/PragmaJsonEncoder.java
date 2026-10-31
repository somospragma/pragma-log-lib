package co.com.pragma.logging.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import co.com.pragma.logging.core.HostName;
import co.com.pragma.logging.core.PragmaLogSettings;
import co.com.pragma.logging.core.StandardFields;
import co.com.pragma.logging.core.masking.LogMasker;
import co.com.pragma.logging.core.masking.MaskType;
import co.com.pragma.logging.core.masking.MaskingSettings;
import co.com.pragma.logging.core.masking.MaskingStrategy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.logstash.logback.encoder.LogstashEncoder;
import net.logstash.logback.stacktrace.ShortenedThrowableConverter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Logback encoder that writes one JSON object per event following the Pragma log schema.
 *
 * <p>Built on {@link LogstashEncoder} (ADR-01): MDC entries and SLF4J key-value pairs are
 * included as top-level fields. Message arguments, MDC and key-values are masked before writing
 * (ADR-07); if masking fails the event is still written, without its arguments (ADR-15).
 *
 * <pre>{@code
 * <encoder class="co.com.pragma.logging.logback.PragmaJsonEncoder">
 *   <appName>my-service</appName>
 *   <!-- optional masking settings -->
 *   <maskFields>password,cardNumber,documentId</maskFields>
 *   <maskCharacter>*</maskCharacter>
 *   <maskStrategy>EMAIL=com.acme.EmailMaskingStrategy</maskStrategy>
 *   <!-- optional field layout -->
 *   <field>env=prod</field>
 *   <hideFields>thread</hideFields>
 *   <renameField>level=severity</renameField>
 *   <!-- optional exception settings -->
 *   <stacktraceMaxDepth>30</stacktraceMaxDepth>
 * </encoder>
 * }</pre>
 */
public class PragmaJsonEncoder extends LogstashEncoder {

    static final int DEFAULT_STACKTRACE_MAX_DEPTH = 50;
    static final int DEFAULT_STACKTRACE_MAX_LENGTH = 16_384;

    private String appName = PragmaLogSettings.DEFAULT_APP_NAME;
    private String appVersion;
    private boolean includeHost = true;
    private boolean includePid;
    private boolean maskingEnabled = true;
    private char maskCharacter = MaskingSettings.DEFAULT_CHARACTER;
    private String maskDefaultType = MaskType.FULL.name();
    private int maskDefaultLength = MaskingSettings.DEFAULT_LENGTH;
    private Set<String> maskFields = MaskingSettings.DEFAULT_FIELDS;
    private int maskMaxDepth = MaskingSettings.DEFAULT_MAX_DEPTH;
    private final Map<String, String> maskStrategies = new LinkedHashMap<>();
    private final Map<String, String> fixedFields = new LinkedHashMap<>();
    private final FieldLayout layout = new FieldLayout();
    private boolean includeStacktrace = true;
    private int stacktraceMaxDepth = DEFAULT_STACKTRACE_MAX_DEPTH;
    private int stacktraceMaxLength = DEFAULT_STACKTRACE_MAX_LENGTH;
    private boolean stacktraceRootCauseFirst;
    private boolean includeContext;
    private boolean includeTags;

    private final List<String> configurationErrors = new ArrayList<>();
    private EventMasker eventMasker;
    private ErrorJsonProvider errorProvider;

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
        this.maskingEnabled = maskingEnabled;
    }

    public void setMaskCharacter(String maskCharacter) {
        if (maskCharacter == null || maskCharacter.length() != 1) {
            configurationError("pragma-log: maskCharacter must be exactly one character, got '" + maskCharacter + "'");
            return;
        }
        this.maskCharacter = maskCharacter.charAt(0);
    }

    public void setMaskDefaultType(String maskDefaultType) {
        this.maskDefaultType = maskDefaultType;
    }

    public void setMaskDefaultLength(int maskDefaultLength) {
        this.maskDefaultLength = maskDefaultLength;
    }

    /** Comma-separated field names masked by name; replaces the default list. */
    public void setMaskFields(String maskFields) {
        this.maskFields = Arrays.stream(maskFields.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void setMaskMaxDepth(int maskMaxDepth) {
        this.maskMaxDepth = maskMaxDepth;
    }

    /** Registers a custom strategy as {@code NAME=fully.qualified.ClassName}; may be repeated. */
    public void addMaskStrategy(String definition) {
        String[] pair = splitPair("maskStrategy", definition);
        if (pair != null) {
            maskStrategies.put(pair[0], pair[1]);
        }
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
        Arrays.stream(hideFields.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .forEach(layout::hide);
    }

    /** Renames a field as {@code current=new}, e.g. {@code level=severity}; may be repeated (FLD-03). */
    public void addRenameField(String definition) {
        String[] pair = splitPair("renameField", definition);
        if (pair != null) {
            layout.rename(pair[0], pair[1]);
        }
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

    /** Logback context properties (e.g. Spring Boot's PID and log patterns) are off by default: framework noise. */
    @Override
    public void setIncludeContext(boolean includeContext) {
        this.includeContext = includeContext;
    }

    /** Marker tags are off by default: frameworks add their own (e.g. Spring's COMMONS-LOGGING). */
    @Override
    public void setIncludeTags(boolean includeTags) {
        this.includeTags = includeTags;
    }

    @Override
    public void start() {
        super.setIncludeContext(includeContext);
        super.setIncludeTags(includeTags);
        PragmaLogSettings settings = new PragmaLogSettings(appName, buildMaskingSettings());
        LogMasker masker = new LogMasker(settings.masking());
        verifyMasking(masker);
        eventMasker = new EventMasker(masker);
        if (!includeStacktrace) {
            layout.hide(StandardFields.STACKTRACE);
        }
        layout.applyTo(this);
        setCustomFields(JsonMapper.shared().writeValueAsString(customFields(settings.appName())));
        setThrowableConverter(throwableConverter());
        if (errorProvider == null && !layout.isHidden(StandardFields.ERROR)) {
            errorProvider = new ErrorJsonProvider();
            errorProvider.setFieldName(layout.nameOf(StandardFields.ERROR));
            addProvider(errorProvider);
        }
        super.start();
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
            return super.encode(masked);
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            addError("pragma-log: could not encode event, writing it without arguments", e);
            return encodeFallback(event, e);
        }
    }

    /** Configuration problems found so far; each was also reported through Logback's status. */
    public List<String> getConfigurationErrors() {
        return List.copyOf(configurationErrors);
    }

    private void configurationError(String message) {
        configurationErrors.add(message);
        addError(message);
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
                    + " <ClassName>. Check that com.fasterxml.jackson.core:jackson-annotations matches the version"
                    + " required by Jackson 3 (with Spring Boot 3.x set jackson-bom.version to 2.22.x or newer). Cause: "
                    + e, e);
        }
    }

    private byte[] encodeFallback(ILoggingEvent event, Throwable error) {
        try {
            return super.encode(eventMasker.fallback(event, error));
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            addError("pragma-log: could not encode fallback event, dropping it", e);
            return new byte[0];
        }
    }

    /**
     * Invalid masking configuration is reported loudly through Logback's status and replaced by the
     * secure defaults: masking stays on (CFG-05).
     */
    private MaskingSettings buildMaskingSettings() {
        try {
            return new MaskingSettings(maskingEnabled, maskCharacter,
                    MaskType.valueOf(maskDefaultType.trim().toUpperCase(Locale.ROOT)), maskDefaultLength,
                    maskFields, maskMaxDepth, loadStrategies());
        } catch (RuntimeException e) {
            configurationError("pragma-log: invalid masking configuration, using secure defaults: " + e.getMessage(), e);
            return MaskingSettings.defaults();
        }
    }

    private Map<String, MaskingStrategy> loadStrategies() {
        Map<String, MaskingStrategy> strategies = new LinkedHashMap<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        maskStrategies.forEach((name, className) -> {
            try {
                Class<?> type = Class.forName(className, true, classLoader);
                strategies.put(name, (MaskingStrategy) type.getDeclaredConstructor().newInstance());
            } catch (ReflectiveOperationException | ClassCastException e) {
                throw new IllegalArgumentException("cannot load mask strategy " + name + "=" + className, e);
            }
        });
        return strategies;
    }

    private Map<String, Object> customFields(String app) {
        Map<String, Object> fields = new LinkedHashMap<>();
        putUnlessHidden(fields, StandardFields.APP, app);
        if (appVersion != null && !appVersion.isBlank()) {
            putUnlessHidden(fields, StandardFields.VERSION, appVersion.trim());
        }
        if (includeHost) {
            putUnlessHidden(fields, StandardFields.HOST, HostName.resolve());
        }
        if (includePid) {
            putUnlessHidden(fields, StandardFields.PID, ProcessHandle.current().pid());
        }
        fixedFields.forEach((name, value) -> putUnlessHidden(fields, name, value));
        return fields;
    }

    private void putUnlessHidden(Map<String, Object> fields, String name, Object value) {
        if (value != null && !layout.isHidden(name)) {
            fields.put(layout.nameOf(name), value);
        }
    }

    private ShortenedThrowableConverter throwableConverter() {
        ShortenedThrowableConverter converter = new ShortenedThrowableConverter();
        converter.setMaxDepthPerThrowable(stacktraceMaxDepth);
        converter.setMaxLength(stacktraceMaxLength);
        converter.setRootCauseFirst(stacktraceRootCauseFirst);
        converter.setOmitCommonFrames(true);
        return converter;
    }

    /** Parses {@code name=value}; reports and returns {@code null} when malformed. */
    private String[] splitPair(String property, String definition) {
        int separator = definition == null ? -1 : definition.indexOf('=');
        if (separator <= 0 || separator == definition.length() - 1) {
            configurationError("pragma-log: " + property + " must look like name=value, got '" + definition + "'");
            return null;
        }
        return new String[] {definition.substring(0, separator).trim(), definition.substring(separator + 1).trim()};
    }
}
