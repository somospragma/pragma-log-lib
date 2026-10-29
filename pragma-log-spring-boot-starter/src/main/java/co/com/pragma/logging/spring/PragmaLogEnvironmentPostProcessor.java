package co.com.pragma.logging.spring;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

/** Spring Boot 4+ entry point; see {@link PragmaLogEnvironmentDefaults}. */
public class PragmaLogEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        PragmaLogEnvironmentDefaults.apply(environment);
    }
}
