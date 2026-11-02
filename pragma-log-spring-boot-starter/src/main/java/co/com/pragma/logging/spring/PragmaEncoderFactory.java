package co.com.pragma.logging.spring;

import ch.qos.logback.core.Context;
import co.com.pragma.logging.core.PragmaLogSettings;
import co.com.pragma.logging.core.format.ColorMode;
import co.com.pragma.logging.core.format.TimestampFormat;
import co.com.pragma.logging.logback.PragmaJsonEncoder;
import co.com.pragma.logging.logback.PragmaTextEncoder;
import java.util.List;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Builds the {@link PragmaJsonEncoder} or {@link PragmaTextEncoder} from Spring Boot configuration. Invalid
 * configuration stops startup (ADR-09): Spring Boot would otherwise start with a configuration nobody asked for.
 */
final class PragmaEncoderFactory {

    private PragmaEncoderFactory() {
    }

    static PragmaLogProperties bind(Environment environment) {
        return Binder.get(environment)
                .bind(PragmaLogProperties.PREFIX, PragmaLogProperties.class)
                .orElseGet(PragmaLogProperties::new);
    }

    static PragmaJsonEncoder json(Environment environment, Context context) {
        PragmaLogProperties properties = bind(environment);
        PragmaLogProperties.Timestamp timestamp = properties.getTimestamp();
        validateTimestamp(timestamp, TimestampFormat.JSON_DEFAULT);
        PragmaJsonEncoder encoder = new PragmaJsonEncoder();
        encoder.setContext(context);
        encoder.setAppName(appName(properties, environment));
        encoder.setAppVersion(properties.getVersion() != null
                ? properties.getVersion()
                : environment.getProperty("spring.application.version"));
        encoder.setIncludeHost(properties.isIncludeHost());
        encoder.setIncludePid(properties.isIncludePid());
        if (timestamp.getPattern() != null) {
            encoder.setTimestampPattern(timestamp.getPattern());
        }
        if (timestamp.getZone() != null && !timestamp.getZone().isBlank()) {
            encoder.setTimeZone(timestamp.getZone().trim());
        }

        properties.getFields().forEach((name, value) -> encoder.addField(name + "=" + value));
        encoder.setHideFields(String.join(",", properties.getHideFields()));
        properties.getRenameFields().forEach((name, newName) -> encoder.addRenameField(name + "=" + newName));

        PragmaLogProperties.Masking masking = properties.getMasking();
        encoder.setMaskingEnabled(masking.isEnabled());
        encoder.setMaskCharacter(masking.getCharacter());
        encoder.setMaskDefaultType(masking.getDefaultType());
        encoder.setMaskDefaultLength(masking.getDefaultLength());
        if (masking.getFields() != null) {
            encoder.setMaskFields(String.join(",", masking.getFields()));
        }
        encoder.setMaskMaxDepth(masking.getMaxDepth());
        masking.getStrategies().forEach((name, className) -> encoder.addMaskStrategy(name + "=" + className));
        masking.getPatterns().forEach(encoder::addMaskPattern);
        encoder.setMaxFieldLength(properties.getMaxFieldLength());
        PragmaLogProperties.Alerts alerts = properties.getAlerts();
        encoder.setMaxLogSize(alerts.getMaxLogSize() == null ? 0 : alerts.getMaxLogSize().toBytes());
        encoder.setAlertConsole(alerts.isConsole());
        alerts.getListeners().forEach(encoder::addAlertListener);

        PragmaLogProperties.Stacktrace stacktrace = properties.getStacktrace();
        encoder.setIncludeStacktrace(stacktrace.isInclude());
        encoder.setStacktraceMaxDepth(stacktrace.getMaxDepth());
        encoder.setStacktraceMaxLength(stacktrace.getMaxLength());
        encoder.setStacktraceRootCauseFirst(stacktrace.isRootCauseFirst());

        encoder.start();
        failOnErrors(encoder.getConfigurationErrors(), encoder::stop);
        return encoder;
    }

    /** @param console whether the encoder writes to the console; files never get colors */
    static PragmaTextEncoder text(Environment environment, Context context, boolean console) {
        PragmaLogProperties properties = bind(environment);
        PragmaTextEncoder encoder = new PragmaTextEncoder();
        encoder.setContext(context);
        PragmaLogProperties.Color color = properties.getColor();
        encoder.setColor((console ? color.getMode() : ColorMode.NEVER).name());
        color.getLevels().forEach((level, value) -> encoder.addLevelColor(level + "=" + value));
        PragmaLogProperties.Timestamp timestamp = properties.getTimestamp();
        if (timestamp.getPattern() != null) {
            encoder.setTimestampPattern(timestamp.getPattern());
        }
        encoder.setTimeZone(timestamp.getZone());
        encoder.setHideFields(String.join(",", properties.getHideFields()));

        PragmaLogProperties.Masking masking = properties.getMasking();
        encoder.setMaskingEnabled(masking.isEnabled());
        encoder.setMaskCharacter(masking.getCharacter());
        encoder.setMaskDefaultType(masking.getDefaultType());
        encoder.setMaskDefaultLength(masking.getDefaultLength());
        if (masking.getFields() != null) {
            encoder.setMaskFields(String.join(",", masking.getFields()));
        }
        encoder.setMaskMaxDepth(masking.getMaxDepth());
        masking.getStrategies().forEach((name, className) -> encoder.addMaskStrategy(name + "=" + className));
        masking.getPatterns().forEach(encoder::addMaskPattern);
        encoder.setMaxFieldLength(properties.getMaxFieldLength());
        PragmaLogProperties.Alerts alerts = properties.getAlerts();
        encoder.setMaxLogSize(alerts.getMaxLogSize() == null ? 0 : alerts.getMaxLogSize().toBytes());
        encoder.setAlertConsole(alerts.isConsole());
        alerts.getListeners().forEach(encoder::addAlertListener);

        PragmaLogProperties.Stacktrace stacktrace = properties.getStacktrace();
        encoder.setIncludeStacktrace(stacktrace.isInclude());
        encoder.setStacktraceMaxDepth(stacktrace.getMaxDepth());
        encoder.setStacktraceMaxLength(stacktrace.getMaxLength());
        encoder.setStacktraceRootCauseFirst(stacktrace.isRootCauseFirst());

        encoder.start();
        failOnErrors(encoder.getConfigurationErrors(), encoder::stop);
        return encoder;
    }

    private static void validateTimestamp(PragmaLogProperties.Timestamp timestamp, String defaultPattern) {
        try {
            TimestampFormat.of(timestamp.getPattern() != null ? timestamp.getPattern() : defaultPattern,
                    timestamp.getZone());
        } catch (IllegalArgumentException e) {
            failOnErrors(List.of(e.getMessage()), () -> { });
        }
    }

    private static void failOnErrors(List<String> errors, Runnable cleanup) {
        if (!errors.isEmpty()) {
            cleanup.run();
            throw new IllegalStateException("Invalid " + PragmaLogProperties.PREFIX + " configuration: "
                    + String.join("; ", errors));
        }
    }

    private static String appName(PragmaLogProperties properties, Environment environment) {
        if (properties.getAppName() != null && !properties.getAppName().isBlank()) {
            return properties.getAppName();
        }
        return environment.getProperty("spring.application.name", PragmaLogSettings.DEFAULT_APP_NAME);
    }
}
