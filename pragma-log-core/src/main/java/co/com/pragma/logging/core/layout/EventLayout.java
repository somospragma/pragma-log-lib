package co.com.pragma.logging.core.layout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which fields an event has, how they are named and in which order, shared by every output format:
 *
 * <ul>
 *   <li>Hidden fields are removed (FLD-02).</li>
 *   <li>Renamed fields get a new name (FLD-03); a name such as {@code $.http.request.id} nests the field inside
 *       objects (FLD-05b).</li>
 *   <li>Fields listed in the order come first, in that order; the rest keep their natural order (FLD-04).</li>
 *   <li>A missing required field is written with a placeholder and reported (FLD-07).</li>
 *   <li>With an extra field name, MDC and key-value fields go inside that object (FLD-06), except those the
 *       configuration names explicitly in the order, renames or required fields.</li>
 * </ul>
 *
 * <p>Immutable and thread-safe.
 */
public final class EventLayout {

    /** Value of a required field that the event does not have. */
    public static final String DEFAULT_PLACEHOLDER = "unknown";

    /** Prefix of output names that nest the field: {@code $.a.b} writes {@code {"a":{"b":value}}}. */
    public static final String PATH_PREFIX = "$.";

    private static final EventLayout DEFAULTS = new EventLayout(Set.of(), Map.of(), List.of(), List.of(), null, null);

    private final Set<String> hidden;
    private final Map<String, String> renames;
    private final List<String> order;
    private final List<String> required;
    private final String placeholder;
    private final String extraField;
    private final Set<String> pinned;

    /**
     * Fields arranged for one event, and the required fields it did not have.
     *
     * @param fields          output names in order; nested objects are {@code Map<String, Object>}
     * @param missingRequired required fields written with the placeholder
     */
    public record Arranged(Map<String, Object> fields, List<String> missingRequired) {
    }

    /**
     * Creates a layout.
     *
     * @param hidden      fields removed, by original name
     * @param renames     original name to output name; {@code $.a.b} nests the field
     * @param order       fields written first, by original or output name; may include the extra field name
     * @param required    fields every event must have, by original name
     * @param placeholder value of a missing required field; {@code null} for {@link #DEFAULT_PLACEHOLDER}
     * @param extraField  name of the object that groups MDC and key-value fields; {@code null} or blank to keep
     *                    them at the root
     * @throws IllegalArgumentException when a name is blank or a path is malformed
     */
    public EventLayout(Set<String> hidden, Map<String, String> renames, List<String> order, List<String> required,
            String placeholder, String extraField) {
        this.hidden = Set.copyOf(hidden);
        this.renames = Map.copyOf(renames);
        this.order = List.copyOf(order);
        this.required = List.copyOf(required);
        this.placeholder = placeholder == null ? DEFAULT_PLACEHOLDER : placeholder;
        this.extraField = extraField == null || extraField.isBlank() ? null : extraField.trim();
        this.renames.forEach((field, name) -> path(name));
        if (this.extraField != null) {
            path(this.extraField);
        }
        Set<String> names = new LinkedHashSet<>(this.order);
        names.addAll(this.renames.keySet());
        names.addAll(this.required);
        this.pinned = Set.copyOf(names);
    }

    /** Layout without hidden, renamed, ordered, required or extra fields. */
    public static EventLayout defaults() {
        return DEFAULTS;
    }

    public boolean isHidden(String field) {
        return hidden.contains(field);
    }

    /** Output name of a field: its rename, or the field name itself. */
    public String nameOf(String field) {
        return renames.getOrDefault(field, field);
    }

    public Set<String> hidden() {
        return hidden;
    }

    public Map<String, String> renames() {
        return renames;
    }

    public List<String> order() {
        return order;
    }

    public List<String> required() {
        return required;
    }

    public String placeholder() {
        return placeholder;
    }

    /** Name of the object that groups MDC and key-value fields, or {@code null}. */
    public String extraField() {
        return extraField;
    }

    /** Arranges the fields of one event. */
    public Arranged arrange(EventFields fields) {
        Map<String, EventFields.Entry> entries = new LinkedHashMap<>();
        fields.entries().forEach((name, entry) -> {
            if (!hidden.contains(name)) {
                entries.put(name, entry);
            }
        });

        List<String> missing = List.of();
        for (String name : required) {
            if (!entries.containsKey(name) || entries.get(name).value() == null) {
                if (missing.isEmpty()) {
                    missing = new ArrayList<>();
                }
                missing.add(name);
                entries.put(name, new EventFields.Entry(placeholder, EventFields.Kind.STATIC));
            }
        }

        // Natural order, with the extra object where its first member was.
        Map<String, Object> natural = new LinkedHashMap<>();
        Map<String, Object> extra = null;
        for (Map.Entry<String, EventFields.Entry> entry : entries.entrySet()) {
            String name = entry.getKey();
            if (extraField != null && entry.getValue().kind() == EventFields.Kind.DYNAMIC && !pinned.contains(name)) {
                if (extra == null) {
                    extra = new LinkedHashMap<>();
                    natural.put(extraField, extra);
                }
                extra.put(name, entry.getValue().value());
            } else {
                natural.put(name, entry.getValue().value());
            }
        }

        Map<String, Object> output = new LinkedHashMap<>();
        Set<String> written = new LinkedHashSet<>();
        for (String name : order) {
            String field = originalName(name, natural);
            if (field != null && written.add(field)) {
                write(output, field, natural.get(field));
            }
        }
        natural.forEach((field, value) -> {
            if (written.add(field)) {
                write(output, field, value);
            }
        });
        return new Arranged(output, missing);
    }

    /** The field an order entry refers to, by original or output name; {@code null} when absent. */
    private String originalName(String name, Map<String, Object> natural) {
        if (natural.containsKey(name)) {
            return name;
        }
        for (Map.Entry<String, String> rename : renames.entrySet()) {
            if (rename.getValue().equals(name) && natural.containsKey(rename.getKey())) {
                return rename.getKey();
            }
        }
        return null;
    }

    private void write(Map<String, Object> output, String field, Object value) {
        String name = field.equals(extraField) ? field : nameOf(field);
        List<String> path = path(name);
        if (path == null) {
            output.put(name, value);
            return;
        }
        Map<String, Object> parent = output;
        for (int i = 0; i < path.size() - 1; i++) {
            Object child = parent.computeIfAbsent(path.get(i), key -> new LinkedHashMap<String, Object>());
            if (!(child instanceof Map)) {
                // A field already uses that name: keep this one flat rather than lose either.
                output.put(String.join(".", path), value);
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> next = (Map<String, Object>) child;
            parent = next;
        }
        parent.put(path.get(path.size() - 1), value);
    }

    /**
     * Segments of a {@code $.a.b} name, or {@code null} for a plain name.
     *
     * @throws IllegalArgumentException when the path has empty segments
     */
    static List<String> path(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("field names must not be blank");
        }
        if (!name.startsWith(PATH_PREFIX)) {
            return null;
        }
        String[] segments = name.substring(PATH_PREFIX.length()).split("\\.", -1);
        for (String segment : segments) {
            if (segment.isBlank()) {
                throw new IllegalArgumentException("invalid field path '" + name + "': use $.parent.child");
            }
        }
        return List.of(segments);
    }
}
