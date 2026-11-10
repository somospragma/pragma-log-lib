package co.com.pragma.logging.core.layout;

import co.com.pragma.logging.core.alert.LogAlerts;
import co.com.pragma.logging.core.format.JsonEventFormatter;
import co.com.pragma.logging.core.format.LogEventFormatter;

/**
 * Turns the fields of an event into text: arranges them with the {@link EventLayout}, writes them with the
 * configured format and reports missing required fields and large events. Shared by the Logback encoder and the
 * Quarkus formatter so every framework writes the same output. Thread-safe.
 */
public final class StructuredEventWriter {

    private static final LogEventFormatter FALLBACK = new JsonEventFormatter(false);

    private final EventLayout layout;
    private final LogEventFormatter formatter;
    private final LogAlerts alerts;

    public StructuredEventWriter(EventLayout layout, LogEventFormatter formatter, LogAlerts alerts) {
        this.layout = layout;
        this.formatter = formatter;
        this.alerts = alerts;
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
        String text;
        try {
            text = formatter.format(arranged.fields());
        } catch (RuntimeException | LinkageError e) {
            // A custom format must not lose events: they are written as JSON instead (ADR-15).
            alerts.maskingError(logger, e);
            text = FALLBACK.format(arranged.fields());
        }
        alerts.checkSize(logger, level, text);
        return text;
    }
}
