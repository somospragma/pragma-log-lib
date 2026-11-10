package co.com.pragma.logging.core.alert;

/**
 * Receives alerts about the logs themselves (ALR-02): an event bigger than {@code max-log-size} (ALR-01), an
 * event that could not be masked or encoded (ALR-03, ALR-05), or an event without required fields (FLD-07). Typical uses are a metric or a notification.
 *
 * <p>Register it with {@code pragma.log.alerts.listeners} (class name, public no-arg constructor), with
 * {@link LogAlerts#register(LogAlertListener)}, or, in Spring Boot, as a bean.
 *
 * <p>Calls happen on the logging thread, inside the logging call: keep them fast. An exception thrown here is
 * ignored. Logs written from a listener do not trigger new alerts.
 */
public interface LogAlertListener {

    /** An event is bigger than the configured maximum. It is still written whole. */
    default void onLargeLog(LargeLogAlert alert) {
    }

    /**
     * An event could not be masked or encoded. It was written without arguments and with the
     * {@code pragmaLogError} field, never with unmasked data.
     */
    default void onMaskingError(MaskingErrorAlert alert) {
    }

    /** An event did not have some of the required fields. It was written with the placeholder in them. */
    default void onMissingFields(MissingFieldsAlert alert) {
    }
}
