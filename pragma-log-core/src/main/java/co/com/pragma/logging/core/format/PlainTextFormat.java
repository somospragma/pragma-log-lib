package co.com.pragma.logging.core.format;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Readable one-line format for local development (FMT-01), shared by every adapter:
 *
 * <pre>
 * 2026-11-02 10:15:30.123  INFO [main] c.c.p.l.e.CustomerService : Customer created requestId=abc customerId=C-1
 * </pre>
 *
 * <p>Fields (MDC and key-values) follow the message as {@code name=value}; the stack trace, if any, follows on
 * the next lines. Callers pass values that are already masked. With colors, the level gets its color
 * (configurable per level), the timestamp is faint and the logger cyan.
 */
public final class PlainTextFormat {

    /** Default level colors; each one can be replaced by configuration. */
    public static final Map<String, String> DEFAULT_LEVEL_COLORS = Map.of(
            "ERROR", "red", "WARN", "yellow", "INFO", "green", "DEBUG", "cyan", "TRACE", "faint");

    static final int LOGGER_LENGTH = 40;

    private static final String FAINT = AnsiColors.code("faint");
    private static final String CYAN = AnsiColors.code("cyan");

    private final DateTimeFormatter timestamp;
    private final boolean colors;
    private final Map<String, String> levelCodes;

    /**
     * @param timestamp   formatter with a zone, see {@link TimestampFormat}
     * @param colors      whether ANSI colors are written
     * @param levelColors colors by level name (ERROR, WARN, INFO, DEBUG, TRACE) replacing the defaults
     * @throws IllegalArgumentException when a level or a color is unknown
     */
    public PlainTextFormat(DateTimeFormatter timestamp, boolean colors, Map<String, String> levelColors) {
        this.timestamp = timestamp;
        this.colors = colors;
        Map<String, String> merged = new LinkedHashMap<>(DEFAULT_LEVEL_COLORS);
        levelColors.forEach((level, color) -> {
            String name = level.trim().toUpperCase(Locale.ROOT);
            if (!DEFAULT_LEVEL_COLORS.containsKey(name)) {
                throw new IllegalArgumentException("pragma.log.color.levels: unknown level '" + level
                        + "', use ERROR, WARN, INFO, DEBUG or TRACE");
            }
            merged.put(name, color);
        });
        Map<String, String> codes = new LinkedHashMap<>();
        merged.forEach((level, color) -> {
            try {
                codes.put(level, AnsiColors.code(color));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("pragma.log.color.levels." + level + ": " + e.getMessage(), e);
            }
        });
        this.levelCodes = Map.copyOf(codes);
    }

    /**
     * @param fields     MDC and key-value fields, already masked; values are written with {@code toString()}
     * @param stacktrace stack trace text, or {@code null}
     * @return the line, ending with a line separator
     */
    public String format(Instant instant, String level, String thread, String logger, String message,
            Map<String, ?> fields, String stacktrace) {
        StringBuilder line = new StringBuilder(128);
        colored(line, FAINT, timestamp.format(instant));
        line.append(' ');
        String paddedLevel = level.length() < 5 ? " ".repeat(5 - level.length()) + level : level;
        colored(line, levelCodes.get(level), paddedLevel);
        line.append(" [").append(thread).append("] ");
        colored(line, CYAN, abbreviate(logger));
        line.append(" : ").append(message);
        if (fields != null) {
            fields.forEach((name, value) -> appendField(line, name, value));
        }
        line.append(System.lineSeparator());
        if (stacktrace != null && !stacktrace.isEmpty()) {
            line.append(stacktrace);
            if (!stacktrace.endsWith("\n")) {
                line.append(System.lineSeparator());
            }
        }
        return line.toString();
    }

    private void colored(StringBuilder line, String code, String text) {
        if (colors && code != null) {
            line.append(code).append(text).append(AnsiColors.RESET);
        } else {
            line.append(text);
        }
    }

    private static void appendField(StringBuilder line, String name, Object value) {
        String text = String.valueOf(value);
        line.append(' ').append(name).append('=');
        // Text with spaces is quoted so the field ends where it should; objects are already JSON.
        if (value instanceof CharSequence && (text.isEmpty() || text.chars().anyMatch(Character::isWhitespace))) {
            line.append('"').append(text.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        } else {
            line.append(text);
        }
    }

    /** Shortens package segments to their first letter, left to right, until the name fits: {@code c.c.p.l.Foo}. */
    static String abbreviate(String logger) {
        if (logger == null) {
            return "";
        }
        if (logger.length() <= LOGGER_LENGTH) {
            return logger;
        }
        String[] segments = logger.split("\\.");
        int length = logger.length();
        for (int i = 0; i < segments.length - 1 && length > LOGGER_LENGTH; i++) {
            if (segments[i].length() > 1) {
                length -= segments[i].length() - 1;
                segments[i] = segments[i].substring(0, 1);
            }
        }
        return String.join(".", segments);
    }
}
