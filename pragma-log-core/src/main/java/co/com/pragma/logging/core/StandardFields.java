package co.com.pragma.logging.core;

import java.util.List;

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

    private StandardFields() {
    }
}
