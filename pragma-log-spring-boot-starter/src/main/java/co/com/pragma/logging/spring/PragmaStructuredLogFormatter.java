package co.com.pragma.logging.spring;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.ContextBase;
import co.com.pragma.logging.logback.PragmaStructuredEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import org.springframework.core.env.Environment;

/**
 * Spring Boot structured logging format backed by {@link PragmaStructuredEncoder}: JSON, XML, CSV or a custom
 * format.
 *
 * <p>Activated automatically by the starter through {@code logging.structured.format.console}
 * and {@code logging.structured.format.file}, so no {@code logback-spring.xml} is needed.
 */
public final class PragmaStructuredLogFormatter implements StructuredLogFormatter<ILoggingEvent>,
        PragmaLogReloader.Reloadable {

    private final Environment environment;
    private final AtomicReference<PragmaStructuredEncoder> encoder;

    public PragmaStructuredLogFormatter(Environment environment) {
        this.environment = environment;
        this.encoder = new AtomicReference<>(PragmaEncoderFactory.structured(environment, logbackContext()));
        PragmaLogReloader.register(this);
    }

    @Override
    public void reload() {
        encoder.getAndSet(PragmaEncoderFactory.structured(environment, logbackContext())).stop();
    }

    @Override
    public String format(ILoggingEvent event) {
        return new String(encoder.get().encode(event), StandardCharsets.UTF_8);
    }

    @Override
    public byte[] formatAsBytes(ILoggingEvent event, Charset charset) {
        byte[] bytes = encoder.get().encode(event);
        return StandardCharsets.UTF_8.equals(charset) ? bytes : new String(bytes, StandardCharsets.UTF_8).getBytes(charset);
    }

    static Context logbackContext() {
        return LoggerFactory.getILoggerFactory() instanceof Context context ? context : new ContextBase();
    }
}
