package co.com.pragma.logging.core.format;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON, one line per event (FMT-02), or indented over several lines (FMT-03). Pretty JSON is meant for local
 * reading: log shippers expect one event per line.
 */
public final class JsonEventFormatter implements LogEventFormatter {

    private static final String LINE_SEPARATOR = "\n";

    private final boolean pretty;
    private final ObjectWriter writer;

    public JsonEventFormatter(boolean pretty) {
        this.pretty = pretty;
        this.writer = pretty ? JsonMapper.shared().writerWithDefaultPrettyPrinter() : JsonMapper.shared().writer();
    }

    public boolean pretty() {
        return pretty;
    }

    @Override
    public String format(Map<String, Object> event) {
        StringWriter out = new StringWriter(256);
        try (JsonGenerator json = writer.createGenerator(out)) {
            writeObject(json, event);
        }
        return out.append(LINE_SEPARATOR).toString();
    }

    static void writeObject(JsonGenerator json, Map<?, ?> fields) {
        json.writeStartObject();
        for (Map.Entry<?, ?> field : fields.entrySet()) {
            json.writeName(String.valueOf(field.getKey()));
            writeValue(json, field.getValue());
        }
        json.writeEndObject();
    }

    static void writeValue(JsonGenerator json, Object value) {
        if (value == null) {
            json.writeNull();
        } else if (value instanceof JsonNode node) {
            json.writeTree(node);
        } else if (value instanceof Map<?, ?> map) {
            writeObject(json, map);
        } else if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            json.writeNumber(((Number) value).longValue());
        } else if (value instanceof BigDecimal decimal) {
            json.writeNumber(decimal);
        } else if (value instanceof BigInteger integer) {
            json.writeNumber(integer);
        } else if (value instanceof Number number) {
            json.writeNumber(number.doubleValue());
        } else if (value instanceof Boolean bool) {
            json.writeBoolean(bool);
        } else {
            json.writeString(value.toString());
        }
    }
}
