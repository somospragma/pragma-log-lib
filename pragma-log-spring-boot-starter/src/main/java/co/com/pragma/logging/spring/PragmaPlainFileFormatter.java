package co.com.pragma.logging.spring;

import org.springframework.core.env.Environment;

/** {@code pragma.log.format: plain} in the log file, never with colors. */
public class PragmaPlainFileFormatter extends PragmaTextLogFormatter {

    public PragmaPlainFileFormatter(Environment environment) {
        super(environment, false);
    }
}
