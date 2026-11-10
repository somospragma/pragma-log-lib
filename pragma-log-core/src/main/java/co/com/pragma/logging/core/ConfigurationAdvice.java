package co.com.pragma.logging.core;

import co.com.pragma.logging.core.http.HttpLogSettings;
import co.com.pragma.logging.core.layout.EventLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Settings that are valid one by one but do not do what they seem together (CFG-09). They do not stop startup:
 * each one is reported once as a warning with what to change.
 */
public final class ConfigurationAdvice {

    private static final int SHORT_FIELD_LENGTH = 32;
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private ConfigurationAdvice() {
    }

    /**
     * The settings the advice looks at, by property meaning.
     *
     * @param maskingEnabled   {@code masking.enabled}
     * @param maskFields       {@code masking.fields}; {@code null} when the default list is used
     * @param maskPatterns     {@code masking.patterns}
     * @param maxFieldLength   {@code max-field-length}
     * @param format           {@code format}, e.g. json
     * @param pretty           {@code json.pretty}
     * @param csvSettings      whether any {@code csv.*} property is set
     * @param customFormatter  {@code custom-formatter}
     * @param layout           hidden, renamed, ordered and required fields and the extra field
     * @param maxLogSize       {@code alerts.max-log-size} in bytes, {@code 0} when not set
     * @param truncate         {@code alerts.truncate}
     * @param bodies           {@code http.body.enabled}
     * @param bodyMaxSize      {@code http.body.max-size} in bytes
     * @param http             headers, excluded headers and query parameters; {@code null} without HTTP context
     */
    public record Settings(boolean maskingEnabled, List<String> maskFields, List<String> maskPatterns, int maxFieldLength,
            String format, boolean pretty, boolean csvSettings, String customFormatter, EventLayout layout,
            long maxLogSize, boolean truncate, boolean bodies, long bodyMaxSize, HttpLogSettings http) {
    }

    /** Returns one message per inconsistency, each naming the properties involved; empty when there are none. */
    public static List<String> check(Settings settings) {
        List<String> warnings = new ArrayList<>();
        String format = settings.format() == null ? "json" : settings.format().trim().toLowerCase(Locale.ROOT);
        masking(settings, warnings);
        formats(settings, format, warnings);
        layout(settings.layout(), warnings);
        alerts(settings, warnings);
        http(settings, warnings);
        return warnings;
    }

    /**
     * Reports each warning once per JVM: console and file appenders build their configuration separately, and
     * frameworks may initialize logging more than once.
     */
    public static void reportOnce(List<String> warnings, Consumer<String> report) {
        for (String warning : warnings) {
            if (REPORTED.add(warning)) {
                report.accept(warning);
            }
        }
    }

    private static void masking(Settings settings, List<String> warnings) {
        if (!settings.maskingEnabled() && (!settings.maskPatterns().isEmpty() || settings.bodies())) {
            warnings.add("pragma.log.masking.enabled is false: masking.patterns and http.body are written without"
                    + " masking. Turn masking back on or remove them.");
        }
        if (settings.maskingEnabled() && settings.maskFields() != null && settings.maskFields().isEmpty()) {
            warnings.add("pragma.log.masking.fields is empty: no field is masked by name (password, token,"
                    + " cardNumber...), only by annotation. Remove the property to use the secure default list.");
        }
        if (settings.maxFieldLength() > 0 && settings.maxFieldLength() < SHORT_FIELD_LENGTH) {
            warnings.add("pragma.log.max-field-length is " + settings.maxFieldLength() + ": most messages will be cut."
                    + " Use a few hundred characters or more.");
        }
    }

    private static void formats(Settings settings, String format, List<String> warnings) {
        if (settings.pretty() && !format.equals("json")) {
            warnings.add("pragma.log.json.pretty only applies to format json, not " + format + ".");
        } else if (settings.pretty()) {
            warnings.add("pragma.log.json.pretty writes each log over several lines: log shippers expect one per"
                    + " line. Use it only locally, e.g. in a dev profile.");
        }
        if (settings.csvSettings() && !format.equals("csv")) {
            warnings.add("pragma.log.csv.* only applies to format csv, not " + format + ".");
        }
        if (settings.customFormatter() != null && !settings.customFormatter().isBlank() && !format.equals("custom")) {
            warnings.add("pragma.log.custom-formatter only applies to format custom, not " + format + ".");
        }
    }

    private static void layout(EventLayout layout, List<String> warnings) {
        if (layout == null) {
            return;
        }
        for (String field : layout.required()) {
            if (layout.isHidden(field)) {
                warnings.add("pragma.log.required-fields lists " + field + ", which hide-fields removes: it is"
                        + " always written with the placeholder.");
            }
        }
        for (String field : layout.order()) {
            if (layout.isHidden(field)) {
                warnings.add("pragma.log.field-order lists " + field + ", which hide-fields removes.");
            }
        }
        Map<String, String> outputs = new HashMap<>();
        layout.renames().forEach((field, name) -> {
            String other = outputs.put(name, field);
            if (other != null) {
                warnings.add("pragma.log.rename-fields gives " + other + " and " + field + " the same name " + name
                        + ": only one of them is written.");
            }
            if (StandardFields.ALL.contains(name) && !layout.isHidden(name) && !layout.renames().containsKey(name)) {
                warnings.add("pragma.log.rename-fields renames " + field + " to " + name
                        + ", a standard field: one of them is lost.");
            }
        });
        String extra = layout.extraField();
        if (extra != null && (StandardFields.ALL.contains(extra) || outputs.containsKey(extra))) {
            warnings.add("pragma.log.extra-field is " + extra + ", a name another field already uses.");
        }
    }

    private static void alerts(Settings settings, List<String> warnings) {
        if (settings.truncate() && settings.maxLogSize() <= 0) {
            warnings.add("pragma.log.alerts.truncate needs alerts.max-log-size: without a size, nothing is cut.");
        }
        if (settings.bodies() && settings.maxLogSize() > 0 && settings.bodyMaxSize() * 2 > settings.maxLogSize()) {
            warnings.add("pragma.log.http.body.max-size (" + settings.bodyMaxSize() + " bytes) for two bodies can"
                    + " exceed alerts.max-log-size (" + settings.maxLogSize() + " bytes): request logs will raise"
                    + " alerts" + (settings.truncate() ? " and be cut." : "."));
        }
    }

    private static void http(Settings settings, List<String> warnings) {
        HttpLogSettings http = settings.http();
        if (http == null) {
            return;
        }
        if (http.headers().contains(HttpLogSettings.ALL) && http.excludeHeaders().isEmpty()) {
            warnings.add("pragma.log.http.headers is \"*\" with an empty exclude-headers: credentials and cookies are"
                    + " copied to every log (masked by name). Keep the default exclude-headers.");
        }
    }
}
