package co.com.pragma.logging.core.format;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Timestamp pattern and time zone of the logs (FMT-06). The pattern is either a {@link DateTimeFormatter}
 * pattern ({@code yyyy-MM-dd HH:mm:ss.SSS}) or the name of one of its constants in brackets
 * ({@code [ISO_OFFSET_DATE_TIME]}), as in logstash-logback-encoder.
 */
public final class TimestampFormat {

    /** Default of the JSON format: ISO-8601 with offset. */
    public static final String JSON_DEFAULT = "[ISO_OFFSET_DATE_TIME]";

    /** Default of the plain text format. */
    public static final String PLAIN_DEFAULT = "yyyy-MM-dd HH:mm:ss.SSS";

    private TimestampFormat() {
    }

    /**
     * @param pattern pattern or {@code [CONSTANT]}
     * @param zone    zone id such as {@code UTC} or {@code America/Bogota}; {@code null} or blank for the
     *                system zone
     * @throws IllegalArgumentException when the pattern or the zone is invalid
     */
    public static DateTimeFormatter of(String pattern, String zone) {
        return formatter(pattern).withZone(zone(zone));
    }

    /** @throws IllegalArgumentException when the zone is invalid */
    public static ZoneId zone(String zone) {
        if (zone == null || zone.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(zone.trim());
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("pragma.log.timestamp.zone is not a valid zone id: '" + zone + "'", e);
        }
    }

    private static DateTimeFormatter formatter(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("pragma.log.timestamp.pattern must not be blank");
        }
        String trimmed = pattern.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return constant(trimmed.substring(1, trimmed.length() - 1));
        }
        try {
            return DateTimeFormatter.ofPattern(trimmed);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("pragma.log.timestamp.pattern is not valid: '" + pattern + "'", e);
        }
    }

    private static DateTimeFormatter constant(String name) {
        try {
            Field field = DateTimeFormatter.class.getField(name);
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == DateTimeFormatter.class) {
                return (DateTimeFormatter) field.get(null);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // reported below
        }
        throw new IllegalArgumentException("pragma.log.timestamp.pattern: [" + name
                + "] is not a DateTimeFormatter constant such as [ISO_OFFSET_DATE_TIME]");
    }
}
