package co.com.pragma.logging.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import co.com.pragma.logging.core.StandardFields;
import co.com.pragma.logging.core.masking.TextSanitizer;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import tools.jackson.core.JsonGenerator;

/**
 * Writes a searchable summary of the logged exception (ERR-02, ERR-04), present even when the
 * stack trace is disabled:
 *
 * <pre>{@code "error":{"type":"java.lang.IllegalStateException","message":"Gateway timeout",
 *          "rootCause":"java.net.SocketTimeoutException: Read timed out"}}</pre>
 */
final class ErrorJsonProvider extends AbstractFieldJsonProvider<ILoggingEvent> {

    private final TextSanitizer sanitizer;

    ErrorJsonProvider(TextSanitizer sanitizer) {
        this.sanitizer = sanitizer;
        setFieldName(StandardFields.ERROR);
    }

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) {
        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable == null) {
            return;
        }
        generator.writeName(getFieldName());
        generator.writeStartObject();
        generator.writeStringProperty("type", throwable.getClassName());
        if (throwable.getMessage() != null) {
            generator.writeStringProperty("message", sanitizer.maskOnly(throwable.getMessage()));
        }
        IThrowableProxy root = rootCause(throwable);
        if (root != throwable) {
            generator.writeStringProperty("rootCause", root.getMessage() == null
                    ? root.getClassName()
                    : root.getClassName() + ": " + sanitizer.maskOnly(root.getMessage()));
        }
        generator.writeEndObject();
    }

    private static IThrowableProxy rootCause(IThrowableProxy throwable) {
        IThrowableProxy current = throwable;
        // Bounded walk: cause chains can be cyclic.
        for (int i = 0; i < 64 && current.getCause() != null && current.getCause() != current; i++) {
            current = current.getCause();
        }
        return current;
    }
}
