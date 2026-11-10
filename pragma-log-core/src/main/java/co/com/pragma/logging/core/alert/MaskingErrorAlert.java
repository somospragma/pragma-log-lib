package co.com.pragma.logging.core.alert;

/**
 * A log event whose arguments could not be masked; it was written without them (ALR-03).
 *
 * @param logger logger name of the event
 * @param error  what failed; its message may describe the logged object, so do not log it as is
 */
public record MaskingErrorAlert(String logger, Throwable error) {
}
