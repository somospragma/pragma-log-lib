package co.com.pragma.logging.core.layout;

import co.com.pragma.logging.core.StandardFields;
import co.com.pragma.logging.core.alert.LogAlerts;
import co.com.pragma.logging.core.format.JsonEventFormatter;
import co.com.pragma.logging.core.format.LogEventFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Turns the fields of an event into text: arranges them with the {@link EventLayout}, writes them with the
 * configured format and reports missing required fields and large events. Optionally cuts an event bigger than
 * {@code max-log-size} until it fits (ALR-04). Shared by the Logback encoder and the Quarkus formatter so every
 * framework writes the same output. Thread-safe.
 */
public final class StructuredEventWriter {

    /** Field added to an event that was cut to fit {@code max-log-size}. */
    public static final String TRUNCATED_FIELD = "truncated";

    private static final LogEventFormatter FALLBACK = new JsonEventFormatter(false);
    /** Fields never cut: without them the event cannot be found. */
    private static final Set<String> KEPT = Set.of(StandardFields.TIMESTAMP, StandardFields.LEVEL,
            StandardFields.LOGGER, StandardFields.THREAD, StandardFields.APP, TRUNCATED_FIELD);
    private static final String TRUNCATED = "...[truncated ";
    private static final int MIN_TEXT = 64;
    private static final int MAX_ROUNDS = 16;

    private final EventLayout layout;
    private final LogEventFormatter formatter;
    private final LogAlerts alerts;
    private final boolean truncate;

    public StructuredEventWriter(EventLayout layout, LogEventFormatter formatter, LogAlerts alerts) {
        this(layout, formatter, alerts, false);
    }

    /** @param truncate whether an event bigger than {@code max-log-size} is cut until it fits (ALR-04) */
    public StructuredEventWriter(EventLayout layout, LogEventFormatter formatter, LogAlerts alerts, boolean truncate) {
        this.layout = layout;
        this.formatter = formatter;
        this.alerts = alerts;
        this.truncate = truncate;
    }

    public EventLayout layout() {
        return layout;
    }

    public LogEventFormatter formatter() {
        return formatter;
    }

    /** Text written once at the start of a file, or {@code null}. */
    public String header() {
        try {
            return formatter.header();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /**
     * Returns the text of one event.
     *
     * @param logger logger name, for alerts
     * @param level  level name, for alerts
     */
    public String write(EventFields fields, String logger, String level) {
        EventLayout.Arranged arranged = layout.arrange(fields);
        alerts.missingFields(logger, level, arranged.missingRequired());
        String text = format(arranged.fields(), logger);
        alerts.checkSize(logger, level, text);
        long max = alerts.maxLogSize();
        if (truncate && max > 0 && LogAlerts.utf8Length(text) > max) {
            text = shrink(arranged.fields(), max, logger);
        }
        return text;
    }

    private String format(Map<String, Object> fields, String logger) {
        try {
            return formatter.format(fields);
        } catch (RuntimeException | LinkageError e) {
            // A custom format must not lose events: they are written as JSON instead (ADR-15).
            alerts.maskingError(logger, e);
            return FALLBACK.format(fields);
        }
    }

    /**
     * Cuts the biggest fields, one at a time, until the event fits: long texts keep their start, objects are
     * replaced by a note. Identifying fields are never cut.
     */
    private String shrink(Map<String, Object> original, long max, String logger) {
        Map<String, Object> fields = new LinkedHashMap<>(original);
        fields.put(TRUNCATED_FIELD, true);
        String text = format(fields, logger);
        for (int round = 0; round < MAX_ROUNDS && LogAlerts.utf8Length(text) > max; round++) {
            String biggest = null;
            long biggestSize = 0;
            for (Map.Entry<String, Object> field : fields.entrySet()) {
                long size = size(field.getValue());
                if (!KEPT.contains(field.getKey()) && size > biggestSize) {
                    biggest = field.getKey();
                    biggestSize = size;
                }
            }
            if (biggest == null || biggestSize <= MIN_TEXT) {
                break;
            }
            long excess = LogAlerts.utf8Length(text) - max;
            Object value = fields.get(biggest);
            if (value instanceof CharSequence chars) {
                int keep = (int) Math.max(MIN_TEXT, chars.length() - excess - TRUNCATED.length() - 20);
                fields.put(biggest, chars.subSequence(0, Math.min(keep, chars.length())) + TRUNCATED
                        + (chars.length() - Math.min(keep, chars.length())) + " chars]");
            } else {
                fields.put(biggest, "[truncated " + biggestSize + " bytes]");
            }
            text = format(fields, logger);
        }
        return text;
    }

    /** Approximate bytes a value takes in the output. */
    private static long size(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof CharSequence chars) {
            return LogAlerts.utf8Length(chars);
        }
        if (value instanceof Map || value instanceof JsonNode) {
            return LogAlerts.utf8Length(FALLBACK.format(Map.of("v", value)));
        }
        return String.valueOf(value).length();
    }
}
