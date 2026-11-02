package co.com.pragma.logging.spring;

import java.util.Map;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Turns pragma-log on as the Spring Boot structured logging format (JSON or masked plain text), before the
 * logging system starts.
 * Values are added with the lowest precedence, so anything the application sets wins.
 */
final class PragmaLogEnvironmentDefaults {

    static final String PROPERTY_SOURCE_NAME = "pragmaLogDefaults";

    private PragmaLogEnvironmentDefaults() {
    }

    static void apply(ConfigurableEnvironment environment) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        boolean enabled = environment.getProperty(PragmaLogProperties.PREFIX + ".enabled", Boolean.class, true);
        PragmaLogProperties.Format format = environment.getProperty(PragmaLogProperties.PREFIX + ".format",
                PragmaLogProperties.Format.class, PragmaLogProperties.Format.JSON);
        if (!enabled) {
            return;
        }
        boolean json = format == PragmaLogProperties.Format.JSON;
        String console = (json ? PragmaStructuredLogFormatter.class : PragmaPlainConsoleFormatter.class).getName();
        String file = (json ? PragmaStructuredLogFormatter.class : PragmaPlainFileFormatter.class).getName();
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of(
                "logging.structured.format.console", console,
                "logging.structured.format.file", file)));
    }
}
