package co.com.pragma.logging.core;

import java.util.List;
import java.util.function.Supplier;

/**
 * Names of the standard fields of the Pragma log schema (FLD-01).
 * Every adapter must emit these names so logs look the same across frameworks.
 */
public final class StandardFields {

    public static final String TIMESTAMP = "timestamp";
    public static final String LEVEL = "level";
    public static final String LOGGER = "logger";
    public static final String THREAD = "thread";
    public static final String MESSAGE = "message";
    public static final String APP = "app";
    /** Application version, when configured (FLD-12). */
    public static final String VERSION = "version";
    /** Host name; in Kubernetes, the pod name (FLD-12). */
    public static final String HOST = "host";
    /** Process id, off by default (FLD-12). */
    public static final String PID = "pid";
    public static final String STACKTRACE = "stacktrace";
    /** Structured summary of the logged exception: type, message and root cause (ERR-04). */
    public static final String ERROR = "error";

    /** All standard field names, in output order. */
    public static final List<String> ALL = List.of(TIMESTAMP, LEVEL, LOGGER, THREAD, MESSAGE, APP, VERSION, HOST, PID,
            ERROR, STACKTRACE);

    /** Message written for a log without text or exception (FLD-11). */
    public static final String EMPTY_MESSAGE = "(empty message)";

    private StandardFields() {
    }

    /**
     * Returns the message of a log, never empty (FLD-11): a blank one is replaced by the logged exception or by
     * {@link #EMPTY_MESSAGE}.
     *
     * @param exception summary of the logged exception, e.g. {@code java.lang.IllegalStateException: boom}; called
     *                  only for a blank message, and may return {@code null} when there is none
     */
    public static String message(String message, Supplier<String> exception) {
        if (message != null && !message.isBlank()) {
            return message;
        }
        String summary = exception.get();
        return summary == null || summary.isBlank() ? EMPTY_MESSAGE : summary;
    }
}
