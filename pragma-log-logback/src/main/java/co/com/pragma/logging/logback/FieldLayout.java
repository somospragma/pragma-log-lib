package co.com.pragma.logging.logback;

import co.com.pragma.logging.core.StandardFields;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.logstash.logback.encoder.LogstashEncoder;
import net.logstash.logback.fieldnames.LogstashFieldNames;

/**
 * Hidden and renamed fields (FLD-02, FLD-03). Applies to standard fields, fixed fields, MDC entries
 * and SLF4J key-values alike, so the configuration does not depend on where a field comes from.
 */
final class FieldLayout {

    /** logstash-logback-encoder marker that suppresses a field. */
    static final String IGNORE = "[ignore]";

    private final Set<String> hidden = new LinkedHashSet<>();
    private final Map<String, String> renames = new LinkedHashMap<>();

    void hide(String field) {
        hidden.add(field);
    }

    void rename(String field, String newName) {
        renames.put(field, newName);
    }

    boolean isHidden(String field) {
        return hidden.contains(field);
    }

    /** Output name of {@code field}, or {@link #IGNORE} when hidden. */
    String nameOf(String field) {
        return isHidden(field) ? IGNORE : renames.getOrDefault(field, field);
    }

    void applyTo(LogstashEncoder encoder) {
        LogstashFieldNames names = encoder.getFieldNames();
        names.setTimestamp(nameOf(StandardFields.TIMESTAMP));
        names.setLevel(nameOf(StandardFields.LEVEL));
        names.setLogger(nameOf(StandardFields.LOGGER));
        names.setThread(nameOf(StandardFields.THREAD));
        names.setMessage(nameOf(StandardFields.MESSAGE));
        names.setStackTrace(nameOf(StandardFields.STACKTRACE));
        names.setVersion(IGNORE);
        names.setLevelValue(IGNORE);

        // Any other name may come from MDC or key-values.
        for (String field : hidden) {
            if (!StandardFields.ALL.contains(field)) {
                encoder.addExcludeMdcKeyName(field);
                encoder.addExcludeKeyValueKeyName(field);
            }
        }
        renames.forEach((field, newName) -> {
            if (!StandardFields.ALL.contains(field) && !hidden.contains(field)) {
                encoder.addMdcKeyFieldName(field + "=" + newName);
                encoder.addKeyValueKeyFieldName(field + "=" + newName);
            }
        });
    }
}
