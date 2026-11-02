package co.com.pragma.logging.core.alert;

/**
 * @param logger logger name of the event
 * @param error  what failed; its message may describe the logged object, so do not log it as is
 */
public record MaskingErrorAlert(String logger, Throwable error) {
}
