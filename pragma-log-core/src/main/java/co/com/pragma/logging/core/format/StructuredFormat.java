package co.com.pragma.logging.core.format;

import java.util.List;
import java.util.Locale;

/** Structured output formats and how they are created from the configuration (FMT-02..04). */
public final class StructuredFormat {

    /** A structured output format. */
    public enum Type {
        JSON, XML, CSV, CUSTOM
    }

    private StructuredFormat() {
    }

    /**
     * Parses a format name, ignoring case.
     *
     * @throws IllegalArgumentException when it is not json, xml, csv or custom
     */
    public static Type type(String name) {
        try {
            return Type.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("pragma.log.format must be json, xml, csv or custom, got '" + name + "'",
                    e);
        }
    }

    /**
     * Creates the formatter of a format.
     *
     * @param pretty          JSON over several lines; ignored by the other formats
     * @param csvColumns      CSV columns; empty for the defaults
     * @param csvDelimiter    CSV separator, one character
     * @param csvHeader       whether a CSV file starts with the column names
     * @param customFormatter class name of a {@link LogEventFormatter}, for {@link Type#CUSTOM}
     * @param classLoader     loads the custom formatter
     * @throws IllegalArgumentException when the settings are invalid or the custom formatter cannot be created
     */
    public static LogEventFormatter create(Type type, boolean pretty, List<String> csvColumns, String csvDelimiter,
            boolean csvHeader, String customFormatter, ClassLoader classLoader) {
        return switch (type) {
            case JSON -> new JsonEventFormatter(pretty);
            case XML -> new XmlEventFormatter();
            case CSV -> new CsvEventFormatter(csvColumns, delimiter(csvDelimiter), csvHeader);
            case CUSTOM -> load(customFormatter, classLoader);
        };
    }

    private static char delimiter(String delimiter) {
        if (delimiter == null || delimiter.length() != 1) {
            throw new IllegalArgumentException("pragma.log.csv.delimiter must be exactly one character, got '"
                    + delimiter + "'");
        }
        return delimiter.charAt(0);
    }

    private static LogEventFormatter load(String className, ClassLoader classLoader) {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("pragma.log.format=custom needs pragma.log.custom-formatter, the class"
                    + " name of a LogEventFormatter");
        }
        try {
            Class<?> type = Class.forName(className.trim(), true, classLoader);
            return (LogEventFormatter) type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
            throw new IllegalArgumentException("pragma.log.custom-formatter: cannot create " + className
                    + " (it must implement LogEventFormatter and have a public no-arg constructor)", e);
        }
    }
}
