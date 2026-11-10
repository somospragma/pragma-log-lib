package co.com.pragma.logging.core.format;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * CSV, one line per event with fixed columns (FMT-04). Values are quoted when needed (RFC 4180); objects are written
 * as JSON, and line breaks as {@code \n} so that each event stays on one line. A column the event does not have is
 * left empty. Nested fields are columns named with dots, e.g. {@code http.request.id} for {@code $.http.request.id}.
 */
public final class CsvEventFormatter implements LogEventFormatter {

    /** Columns used when none are configured. */
    public static final List<String> DEFAULT_COLUMNS = List.of("timestamp", "level", "logger", "thread", "message");

    private final List<String> columns;
    private final char delimiter;
    private final boolean header;

    /**
     * Creates a CSV formatter.
     *
     * @param columns   output field names; empty for {@link #DEFAULT_COLUMNS}
     * @param delimiter column separator, usually {@code ,} or {@code ;}
     * @param header    whether the column names are written once at the start of a file
     * @throws IllegalArgumentException when the delimiter is a quote or a line break
     */
    public CsvEventFormatter(List<String> columns, char delimiter, boolean header) {
        if (delimiter == '"' || delimiter == '\n' || delimiter == '\r') {
            throw new IllegalArgumentException("pragma.log.csv.delimiter cannot be a quote or a line break");
        }
        this.columns = columns == null || columns.isEmpty() ? DEFAULT_COLUMNS : List.copyOf(columns);
        this.delimiter = delimiter;
        this.header = header;
    }

    public List<String> columns() {
        return columns;
    }

    @Override
    public String format(Map<String, Object> event) {
        StringBuilder line = new StringBuilder(128);
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                line.append(delimiter);
            }
            cell(line, text(lookup(event, columns.get(i))));
        }
        return line.append('\n').toString();
    }

    @Override
    public String header() {
        if (!header) {
            return null;
        }
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                line.append(delimiter);
            }
            cell(line, columns.get(i));
        }
        return line.append('\n').toString();
    }

    /** A top-level field, or a nested one by its dotted path. */
    private static Object lookup(Map<String, Object> event, String column) {
        if (event.containsKey(column)) {
            return event.get(column);
        }
        Object current = event;
        for (String segment : column.split("\\.", -1)) {
            if (current instanceof Map<?, ?> map && map.containsKey(segment)) {
                current = map.get(segment);
            } else if (current instanceof JsonNode node && node.has(segment)) {
                current = node.get(segment);
            } else {
                return null;
            }
        }
        return current;
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof JsonNode node && node.isValueNode()) {
            return node.isNull() ? "" : node.asString();
        }
        if (value instanceof JsonNode || value instanceof Map) {
            StringWriter out = new StringWriter();
            try (JsonGenerator json = JsonMapper.shared().createGenerator(out)) {
                JsonEventFormatter.writeValue(json, value);
            }
            return out.toString();
        }
        return value.toString();
    }

    private void cell(StringBuilder line, String value) {
        String text = value.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
        boolean quote = text.indexOf(delimiter) >= 0 || text.indexOf('"') >= 0 || text.startsWith(" ")
                || text.endsWith(" ");
        if (quote) {
            line.append('"').append(text.replace("\"", "\"\"")).append('"');
        } else {
            line.append(text);
        }
    }
}
