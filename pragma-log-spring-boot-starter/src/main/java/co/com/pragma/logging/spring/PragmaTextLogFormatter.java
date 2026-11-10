package co.com.pragma.logging.spring;

import ch.qos.logback.classic.spi.ILoggingEvent;
import co.com.pragma.logging.logback.PragmaTextEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import org.springframework.core.env.Environment;

/**
 * Readable text with masking for {@code pragma.log.format: plain} (FMT-01), plugged in as a Spring Boot
 * structured logging format like the JSON one. Console and file use separate subclasses because only the
 * console gets colors.
 */
abstract class PragmaTextLogFormatter implements StructuredLogFormatter<ILoggingEvent>,
        PragmaLogReloader.Reloadable {

    private final Environment environment;
    private final boolean console;
    private volatile PragmaTextEncoder encoder;

    PragmaTextLogFormatter(Environment environment, boolean console) {
        this.environment = environment;
        this.console = console;
        this.encoder = PragmaEncoderFactory.text(environment, PragmaStructuredLogFormatter.logbackContext(), console);
    }

    @Override
    public void reload() {
        PragmaTextEncoder previous = encoder;
        encoder = PragmaEncoderFactory.text(environment, PragmaStructuredLogFormatter.logbackContext(), console);
        previous.stop();
    }

    @Override
    public String format(ILoggingEvent event) {
        return new String(encoder.encode(event), StandardCharsets.UTF_8);
    }

    @Override
    public byte[] formatAsBytes(ILoggingEvent event, Charset charset) {
        byte[] bytes = encoder.encode(event);
        return StandardCharsets.UTF_8.equals(charset) ? bytes : new String(bytes, StandardCharsets.UTF_8).getBytes(charset);
    }
}
