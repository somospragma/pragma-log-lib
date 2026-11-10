package co.com.pragma.logging.quarkus;

import co.com.pragma.logging.core.HostName;
import co.com.pragma.logging.core.PragmaLogSettings;
import co.com.pragma.logging.core.StandardFields;
import co.com.pragma.logging.core.alert.LogAlertListener;
import co.com.pragma.logging.core.alert.LogAlerts;
import co.com.pragma.logging.core.format.LogEventFormatter;
import co.com.pragma.logging.core.format.PlainTextFormat;
import co.com.pragma.logging.core.format.StructuredFormat;
import co.com.pragma.logging.core.format.TimestampFormat;
import co.com.pragma.logging.core.layout.EventLayout;
import co.com.pragma.logging.core.layout.StructuredEventWriter;
import co.com.pragma.logging.core.masking.LogMasker;
import co.com.pragma.logging.core.masking.MaskType;
import co.com.pragma.logging.core.masking.MaskingSettings;
import co.com.pragma.logging.core.masking.MaskingStrategy;
import co.com.pragma.logging.core.masking.PatternMasker;
import co.com.pragma.logging.core.masking.TextSanitizer;
import co.com.pragma.logging.quarkus.slf4j.PragmaSlf4jServiceProvider;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.configuration.MemorySize;
import java.lang.reflect.Field;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Formatter;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

/** Creates the {@link PragmaStructuredFormatter} or {@link PragmaTextFormatter} at startup from {@link PragmaLogConfig}. */
@Recorder
public class PragmaLogRecorder {

    private static final String SLF4J_PROVIDER = "slf4j.provider";

    private final RuntimeValue<PragmaLogConfig> config;

    public PragmaLogRecorder(RuntimeValue<PragmaLogConfig> config) {
        this.config = config;
    }

    /**
     * Makes SLF4J use pragma-log's binding. Quarkus removes slf4j-jboss-logmanager from the application, but in
     * dev mode and tests it can still be on the JVM classpath, where SLF4J picks whichever binding it finds
     * first. If SLF4J is not initialized yet, the standard {@code slf4j.provider} property decides; if it already
     * bound to another provider (dev mode initializes it during the build), the provider is replaced, so the
     * application's loggers, created after this point, are pragma-log's. Loggers created earlier keep the old
     * binding; their message arguments are still masked by {@link PragmaStructuredFormatter}.
     */
    public void preferPragmaSlf4j() {
        if (System.getProperty(SLF4J_PROVIDER) == null) {
            System.setProperty(SLF4J_PROVIDER, PragmaSlf4jServiceProvider.class.getName());
        }
        if (isPragmaSlf4j()) {
            return;
        }
        try {
            Field provider = org.slf4j.LoggerFactory.class.getDeclaredField("PROVIDER");
            provider.setAccessible(true);
            PragmaSlf4jServiceProvider pragma = new PragmaSlf4jServiceProvider();
            pragma.initialize();
            provider.set(null, pragma);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Reported by warnIfSlf4jIsNotOurs() once logging is configured.
        }
    }

    private static boolean isPragmaSlf4j() {
        return org.slf4j.LoggerFactory.getILoggerFactory().getClass().getName()
                .startsWith(PragmaSlf4jServiceProvider.class.getPackageName());
    }

    /**
     * The formatter for console or file logs, or empty to keep the Quarkus one.
     *
     * @param console whether it is for the console; files never get colors
     * @throws IllegalStateException when the configuration is invalid: startup stops with a clear message
     */
    public RuntimeValue<Optional<Formatter>> formatter(boolean console) {
        PragmaLogConfig settings = config.getValue();
        if (!settings.enabled()) {
            return new RuntimeValue<>(Optional.empty());
        }
        LogMasker masker = new LogMasker(masking(settings.masking()));
        try {
            masker.verify();
        } catch (RuntimeException | LinkageError e) {
            throw new IllegalStateException("pragma-log: object masking does not work on this classpath. Check that"
                    + " com.fasterxml.jackson.core:jackson-annotations is 2.21 or newer. Cause: " + e, e);
        }
        Set<String> hidden = new HashSet<>(settings.hideFields().orElse(List.of()));
        warnIfSlf4jIsNotOurs();
        PragmaLogConfig.Stacktrace stacktrace = settings.stacktrace();
        TextSanitizer sanitizer = sanitizer(settings);
        LogAlerts alerts = alerts(settings.alerts());
        boolean plain = settings.format() == PragmaLogConfig.Format.PLAIN;
        DateTimeFormatter timestamp = timestamp(settings.timestamp(),
                plain ? TimestampFormat.PLAIN_DEFAULT : TimestampFormat.JSON_DEFAULT);
        if (plain) {
            return new RuntimeValue<>(Optional.of(new PragmaTextFormatter(masker, sanitizer, alerts,
                    textFormat(timestamp, console && settings.color().mode().enabled(), settings.color().levels()),
                    hidden, stacktrace.include(), stacktrace.maxDepth(), stacktrace.maxLength(),
                    stacktrace.rootCauseFirst())));
        }
        if (!stacktrace.include()) {
            hidden.add(StandardFields.STACKTRACE);
        }
        StructuredEventWriter writer = new StructuredEventWriter(layout(settings, hidden), eventFormatter(settings),
                alerts);
        return new RuntimeValue<>(Optional.of(new PragmaStructuredFormatter(masker, sanitizer, alerts, writer,
                timestamp, staticFields(settings), stacktrace.include(), stacktrace.maxDepth(), stacktrace.maxLength(),
                stacktrace.rootCauseFirst())));
    }

    private static EventLayout layout(PragmaLogConfig settings, Set<String> hidden) {
        try {
            return new EventLayout(hidden, settings.renameFields(), settings.fieldOrder().orElse(List.of()),
                    settings.requiredFields().orElse(List.of()), settings.requiredPlaceholder(),
                    settings.extraField().orElse(null));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid pragma.log configuration: " + e.getMessage(), e);
        }
    }

    private static LogEventFormatter eventFormatter(PragmaLogConfig settings) {
        try {
            PragmaLogConfig.Csv csv = settings.csv();
            return StructuredFormat.create(StructuredFormat.type(settings.format().name()), settings.json().pretty(),
                    csv.columns().orElse(List.of()), csv.delimiter(), csv.header(),
                    settings.customFormatter().orElse(null), Thread.currentThread().getContextClassLoader());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid pragma.log configuration: " + e.getMessage(), e);
        }
    }

    private static TextSanitizer sanitizer(PragmaLogConfig settings) {
        try {
            PragmaLogConfig.Masking masking = settings.masking();
            PatternMasker patterns = masking.enabled()
                    ? PatternMasker.of(masking.patterns().orElse(List.of()), masking.character().charAt(0))
                    : PatternMasker.none();
            return new TextSanitizer(patterns, settings.maxFieldLength());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid pragma.log configuration: " + e.getMessage(), e);
        }
    }

    private static LogAlerts alerts(PragmaLogConfig.Alerts alerts) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            List<LogAlertListener> listeners = alerts.listeners().orElse(List.of()).stream()
                    .map(className -> LogAlerts.load(className, classLoader))
                    .toList();
            return new LogAlerts(alerts.maxLogSize().map(MemorySize::asLongValue).orElse(0L), alerts.console(),
                    listeners);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid pragma.log configuration: " + e.getMessage(), e);
        }
    }

    private static DateTimeFormatter timestamp(PragmaLogConfig.Timestamp timestamp, String defaultPattern) {
        try {
            return TimestampFormat.of(timestamp.pattern().orElse(defaultPattern), timestamp.zone().orElse(null));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid pragma.log configuration: " + e.getMessage(), e);
        }
    }

    private static PlainTextFormat textFormat(DateTimeFormatter timestamp, boolean colors, Map<String, String> levels) {
        try {
            return new PlainTextFormat(timestamp, colors, levels);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid pragma.log configuration: " + e.getMessage(), e);
        }
    }

    /** slf4j-jboss-logmanager can win on classpaths Quarkus does not build, such as a test JVM. */
    private static void warnIfSlf4jIsNotOurs() {
        if (!isPragmaSlf4j()) {
            Logger.getLogger(PragmaLogRecorder.class).warn("pragma-log: SLF4J is bound to "
                    + org.slf4j.LoggerFactory.getILoggerFactory().getClass().getName() + " instead of pragma-log, so"
                    + " SLF4J key-value objects are not masked. Remove org.jboss.slf4j:slf4j-jboss-logmanager from"
                    + " this classpath (in tests: exclude it from testRuntimeClasspath).");
        }
    }

    private static Map<String, Object> staticFields(PragmaLogConfig settings) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(StandardFields.APP, settings.appName()
                .or(() -> property("quarkus.application.name"))
                .orElse(PragmaLogSettings.DEFAULT_APP_NAME));
        settings.version().or(() -> property("quarkus.application.version"))
                .filter(version -> !version.isBlank())
                .ifPresent(version -> fields.put(StandardFields.VERSION, version));
        if (settings.includeHost()) {
            String host = HostName.resolve();
            if (host != null) {
                fields.put(StandardFields.HOST, host);
            }
        }
        if (settings.includePid()) {
            fields.put(StandardFields.PID, ProcessHandle.current().pid());
        }
        fields.putAll(settings.fields());
        return fields;
    }

    private static MaskingSettings masking(PragmaLogConfig.Masking masking) {
        try {
            if (masking.character().length() != 1) {
                throw new IllegalArgumentException("pragma.log.masking.character must be exactly one character, got '"
                        + masking.character() + "'");
            }
            return new MaskingSettings(masking.enabled(), masking.character().charAt(0),
                    MaskType.valueOf(masking.defaultType().trim().toUpperCase(Locale.ROOT)), masking.defaultLength(),
                    masking.fields().map(Set::copyOf).orElse(MaskingSettings.DEFAULT_FIELDS), masking.maxDepth(),
                    strategies(masking.strategies()));
        } catch (RuntimeException e) {
            // Fail fast (ADR-09): starting with a masking configuration nobody asked for could leak data.
            throw new IllegalStateException("Invalid pragma.log configuration: " + e.getMessage(), e);
        }
    }

    private static Map<String, MaskingStrategy> strategies(Map<String, String> classNames) {
        Map<String, MaskingStrategy> strategies = new LinkedHashMap<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        classNames.forEach((name, className) -> {
            try {
                Class<?> type = Class.forName(className, true, classLoader);
                strategies.put(name, (MaskingStrategy) type.getDeclaredConstructor().newInstance());
            } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
                throw new IllegalArgumentException("cannot load mask strategy " + name + "=" + className, e);
            }
        });
        return strategies;
    }

    private static Optional<String> property(String name) {
        return ConfigProvider.getConfig().getOptionalValue(name, String.class);
    }
}
