package co.com.pragma.logging.core.alert;

/**
 * A log event larger than the configured maximum size (ALR-01).
 *
 * @param logger    logger name of the event
 * @param level     level of the event
 * @param sizeBytes size of the encoded event, in UTF-8 bytes
 * @param maxBytes  configured maximum
 */
public record LargeLogAlert(String logger, String level, long sizeBytes, long maxBytes) {
}
