package co.com.pragma.logging.core;

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
    public static final String STACKTRACE = "stacktrace";

    private StandardFields() {
    }
}
