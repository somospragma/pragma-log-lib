package co.com.pragma.logging.spring;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.ContextBase;
import co.com.pragma.logging.logback.PragmaJsonEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import org.springframework.core.env.Environment;

/**
 * Spring Boot structured logging format backed by {@link PragmaJsonEncoder}.
 *
 * <p>Activated automatically by the starter through {@code logging.structured.format.console}
 * and {@code logging.structured.format.file}, so no {@code logback-spring.xml} is needed.
 */
public class PragmaStructuredLogFormatter implements StructuredLogFormatter<ILoggingEvent> {

    private final PragmaJsonEncoder encoder;

    public PragmaStructuredLogFormatter(Environment environment) {
        this.encoder = PragmaEncoderFactory.json(environment, logbackContext());
    }

    @Override
    public String format(ILoggingEvent event) {
        return new String(encoder.encode(event), StandardCharsets.UTF_8);
    }

    @Override
    public byte[] formatAsBytes(ILoggingEvent event, Charset charset) {
        return StandardCharsets.UTF_8.equals(charset) ? encoder.encode(event) : format(event).getBytes(charset);
    }

    static Context logbackContext() {
        return LoggerFactory.getILoggerFactory() instanceof Context context ? context : new ContextBase();
    }
}
