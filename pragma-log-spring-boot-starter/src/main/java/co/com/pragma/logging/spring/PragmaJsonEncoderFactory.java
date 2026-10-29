package co.com.pragma.logging.spring;

import ch.qos.logback.core.Context;
import co.com.pragma.logging.core.PragmaLogSettings;
import co.com.pragma.logging.logback.PragmaJsonEncoder;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/** Builds a {@link PragmaJsonEncoder} from Spring Boot configuration. */
final class PragmaJsonEncoderFactory {

    private PragmaJsonEncoderFactory() {
    }

    static PragmaLogProperties bind(Environment environment) {
        return Binder.get(environment)
                .bind(PragmaLogProperties.PREFIX, PragmaLogProperties.class)
                .orElseGet(PragmaLogProperties::new);
    }

    static PragmaJsonEncoder create(Environment environment, Context context) {
        PragmaLogProperties properties = bind(environment);
        PragmaJsonEncoder encoder = new PragmaJsonEncoder();
        encoder.setContext(context);
        encoder.setAppName(appName(properties, environment));

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

        PragmaLogProperties.Stacktrace stacktrace = properties.getStacktrace();
        encoder.setIncludeStacktrace(stacktrace.isInclude());
        encoder.setStacktraceMaxDepth(stacktrace.getMaxDepth());
        encoder.setStacktraceMaxLength(stacktrace.getMaxLength());
        encoder.setStacktraceRootCauseFirst(stacktrace.isRootCauseFirst());

        encoder.start();
        if (!encoder.getConfigurationErrors().isEmpty()) {
            // Fail fast (ADR-09): Spring Boot would otherwise start with a configuration nobody asked for.
            encoder.stop();
            throw new IllegalStateException("Invalid " + PragmaLogProperties.PREFIX + " configuration: "
                    + String.join("; ", encoder.getConfigurationErrors()));
        }
        return encoder;
    }

    private static String appName(PragmaLogProperties properties, Environment environment) {
        if (properties.getAppName() != null && !properties.getAppName().isBlank()) {
            return properties.getAppName();
        }
        return environment.getProperty("spring.application.name", PragmaLogSettings.DEFAULT_APP_NAME);
    }
}
