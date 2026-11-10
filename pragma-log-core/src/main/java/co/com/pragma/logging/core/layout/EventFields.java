package co.com.pragma.logging.core.layout;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fields of one event before {@link EventLayout} arranges them, in natural order. Standard and static fields win
 * over MDC or key-value fields with the same name; among those, the last one wins.
 *
 * <p>Values are {@code String}, {@code Number}, {@code Boolean}, {@code null}, a Jackson {@code JsonNode} (masked
 * objects) or a {@code Map<String, Object>} (the error summary).
 */
public final class EventFields {

    /** Where a field comes from: decides precedence and which fields may go into the {@code extra} object. */
    enum Kind {
        STANDARD, DYNAMIC, STATIC
    }

    record Entry(Object value, Kind kind) {
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /** Timestamp, level, logger, thread, message, error, stack trace. */
    public EventFields standard(String name, Object value) {
        return put(name, value, Kind.STANDARD);
    }

    /** MDC entries and SLF4J key-values. */
    public EventFields dynamic(String name, Object value) {
        return put(name, value, Kind.DYNAMIC);
    }

    /** App, version, host, pid and fixed fields: the same in every event. */
    public EventFields fixed(String name, Object value) {
        return put(name, value, Kind.STATIC);
    }

    private EventFields put(String name, Object value, Kind kind) {
        Entry existing = entries.get(name);
        if (existing == null || kind != Kind.DYNAMIC || existing.kind() == Kind.DYNAMIC) {
            entries.put(name, new Entry(value, kind));
        }
        return this;
    }

    Map<String, Entry> entries() {
        return entries;
    }
}
