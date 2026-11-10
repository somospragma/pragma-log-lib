package co.com.pragma.logging.core.format;

import java.util.Map;

/**
 * Writes one event, already masked and arranged, as text (FMT-04). pragma-log ships JSON, XML and CSV; an
 * application can plug in its own format by implementing this interface with a public no-argument constructor
 * and setting {@code pragma.log.format: custom} and {@code pragma.log.custom-formatter} to its class name.
 *
 * <pre>{@code
 * public class KeyValueFormatter implements LogEventFormatter {
 *     public String format(Map<String, Object> event) {
 *         StringBuilder line = new StringBuilder();
 *         event.forEach((name, value) -> line.append(name).append('=').append(value).append(' '));
 *         return line.append(System.lineSeparator()).toString();
 *     }
 * }
 * }</pre>
 *
 * <p>Implementations must be thread-safe and must not throw: a failure is reported and the event is written as JSON.
 */
@FunctionalInterface
public interface LogEventFormatter {

    /**
     * Returns the text of one event, ending with a line separator.
     *
     * @param event fields by output name, in output order. Values are {@code String}, {@code Number},
     *              {@code Boolean}, {@code null}, a Jackson {@code tools.jackson.databind.JsonNode} (logged
     *              objects) or a {@code Map<String, Object>} (nested fields, {@code error}, {@code extra})
     */
    String format(Map<String, Object> event);

    /** Returns the text written once at the start of a file, such as a CSV header, or {@code null} for none. */
    default String header() {
        return null;
    }
}
