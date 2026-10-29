package co.com.pragma.logging.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Spring Boot 3.x entry point; see {@link PragmaLogEnvironmentDefaults}. Remove when Boot 3 support ends.
 */
@SuppressWarnings("removal")
public class LegacyPragmaLogEnvironmentPostProcessor implements org.springframework.boot.env.EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        PragmaLogEnvironmentDefaults.apply(environment);
    }
}
