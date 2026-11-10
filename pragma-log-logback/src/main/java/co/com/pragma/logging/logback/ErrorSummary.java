package co.com.pragma.logging.logback;

import ch.qos.logback.classic.spi.IThrowableProxy;
import co.com.pragma.logging.core.masking.TextSanitizer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Searchable summary of the logged exception (ERR-02, ERR-04), present even when the stack trace is disabled:
 *
 * <pre>{@code "error":{"type":"java.lang.IllegalStateException","message":"Gateway timeout",
 *          "rootCause":"java.net.SocketTimeoutException: Read timed out"}}</pre>
 */
final class ErrorSummary {

    private ErrorSummary() {
    }

    @SuppressWarnings("ReferenceEquality") // exceptions in a cause chain are compared by identity
    static Map<String, Object> of(IThrowableProxy throwable, TextSanitizer sanitizer) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", throwable.getClassName());
        if (throwable.getMessage() != null) {
            error.put("message", sanitizer.maskOnly(throwable.getMessage()));
        }
        IThrowableProxy root = rootCause(throwable);
        if (root != throwable) {
            error.put("rootCause", root.getMessage() == null
                    ? root.getClassName()
                    : root.getClassName() + ": " + sanitizer.maskOnly(root.getMessage()));
        }
        return error;
    }

    @SuppressWarnings("ReferenceEquality")
    private static IThrowableProxy rootCause(IThrowableProxy throwable) {
        IThrowableProxy current = throwable;
        // Bounded walk: cause chains can be cyclic.
        for (int i = 0; i < 64 && current.getCause() != null && current.getCause() != current; i++) {
            current = current.getCause();
        }
        return current;
    }
}
