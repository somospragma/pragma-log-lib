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
abstract class PragmaTextLogFormatter implements StructuredLogFormatter<ILoggingEvent> {

    private final PragmaTextEncoder encoder;

    PragmaTextLogFormatter(Environment environment, boolean console) {
        this.encoder = PragmaEncoderFactory.text(environment, PragmaStructuredLogFormatter.logbackContext(), console);
    }

    @Override
    public String format(ILoggingEvent event) {
        return new String(encoder.encode(event), StandardCharsets.UTF_8);
    }

    @Override
    public byte[] formatAsBytes(ILoggingEvent event, Charset charset) {
        return StandardCharsets.UTF_8.equals(charset) ? encoder.encode(event) : format(event).getBytes(charset);
    }
}
