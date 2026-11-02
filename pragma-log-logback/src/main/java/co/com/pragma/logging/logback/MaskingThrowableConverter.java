package co.com.pragma.logging.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import co.com.pragma.logging.core.masking.TextSanitizer;
import net.logstash.logback.stacktrace.ShortenedThrowableConverter;

/** Bounded stack trace (ERR-03) with patterns masked (MSK-10): exception messages often carry data. */
final class MaskingThrowableConverter extends ShortenedThrowableConverter {

    private final TextSanitizer sanitizer;

    MaskingThrowableConverter(TextSanitizer sanitizer, int maxDepth, int maxLength, boolean rootCauseFirst) {
        this.sanitizer = sanitizer;
        setMaxDepthPerThrowable(maxDepth);
        setMaxLength(maxLength);
        setRootCauseFirst(rootCauseFirst);
        setOmitCommonFrames(true);
    }

    @Override
    public String convert(ILoggingEvent event) {
        return sanitizer.maskOnly(super.convert(event));
    }
}
