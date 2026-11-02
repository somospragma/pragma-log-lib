package co.com.pragma.logging.spring;

import org.springframework.core.env.Environment;

/** {@code pragma.log.format: plain} on the console, with colors per {@code pragma.log.color}. */
public class PragmaPlainConsoleFormatter extends PragmaTextLogFormatter {

    public PragmaPlainConsoleFormatter(Environment environment) {
        super(environment, true);
    }
}
