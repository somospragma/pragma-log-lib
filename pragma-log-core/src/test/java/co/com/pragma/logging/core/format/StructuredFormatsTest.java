package co.com.pragma.logging.core.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class StructuredFormatsTest {

    /** Custom format loaded by class name. */
    public static class KeyValueFormatter implements LogEventFormatter {
        @Override
        public String format(Map<String, Object> event) {
            StringBuilder line = new StringBuilder();
            event.forEach((name, value) -> line.append(name).append('=').append(value).append(' '));
            return line.toString().trim() + "\n";
        }
    }

    private static Map<String, Object> event() {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", "java.lang.IllegalStateException");
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("timestamp", "2026-11-10T10:00:00Z");
        event.put("level", "INFO");
        event.put("message", "Pago \"aprobado\" <ok> & listo");
        event.put("amount", new BigDecimal("10.50"));
        event.put("count", 3);
        event.put("ratio", 0.5);
        event.put("ok", true);
        event.put("none", null);
        event.put("user", JsonMapper.shared().readTree("{\"name\":\"Ana\",\"tags\":[\"a\",\"b\"],\"x\":null}"));
        event.put("error", error);
        return event;
    }

    @Nested
    @DisplayName("JSON")
    class Json {

        @Test
        @DisplayName("compact JSON is one line with typed values (FMT-02)")
        void compact() {
            String line = new JsonEventFormatter(false).format(event());

            assertThat(line).endsWith("}\n").doesNotContain("\n  ");
            JsonNode json = JsonMapper.shared().readTree(line);
            assertThat(json.get("amount").decimalValue()).isEqualByComparingTo("10.50");
            assertThat(json.get("count").asInt()).isEqualTo(3);
            assertThat(json.get("ok").asBoolean()).isTrue();
            assertThat(json.get("none").isNull()).isTrue();
            assertThat(json.get("user").get("tags").get(1).asString()).isEqualTo("b");
            assertThat(json.get("error").get("type").asString()).isEqualTo("java.lang.IllegalStateException");
        }

        @Test
        @DisplayName("pretty JSON is indented over several lines (FMT-03)")
        void pretty() {
            JsonEventFormatter formatter = new JsonEventFormatter(true);
            String text = formatter.format(event());

            assertThat(formatter.pretty()).isTrue();
            assertThat(text.lines().count()).isGreaterThan(10);
            assertThat(text).contains("\n  \"level\" : \"INFO\"");
            assertThat(JsonMapper.shared().readTree(text).get("level").asString()).isEqualTo("INFO");
        }
    }

    @Nested
    @DisplayName("XML")
    class Xml {

        @Test
        @DisplayName("one <event> per line, escaped text, nested objects and arrays (FMT-04)")
        void xml() {
            String xml = new XmlEventFormatter().format(event());

            assertThat(xml).startsWith("<event><timestamp>2026-11-10T10:00:00Z</timestamp><level>INFO</level>")
                    .contains("<message>Pago &quot;aprobado&quot; &lt;ok&gt; &amp; listo</message>")
                    .contains("<amount>10.50</amount><count>3</count>")
                    .contains("<none/>")
                    .contains("<user><name>Ana</name><tags><item>a</item><item>b</item></tags><x/></user>")
                    .contains("<error><type>java.lang.IllegalStateException</type></error>")
                    .endsWith("</event>\n");
            assertThat(xml.lines().count()).isOne();
        }

        @Test
        @DisplayName("invalid characters in names become _ and control characters are dropped")
        void names() {
            assertThat(XmlEventFormatter.elementName("http.status")).isEqualTo("http.status");
            assertThat(XmlEventFormatter.elementName("1st field")).isEqualTo("_1st_field");
            assertThat(XmlEventFormatter.elementName("")).isEqualTo("_");
            assertThat(new XmlEventFormatter().format(Map.of("m", "a\nb\u0001c\td")))
                    .isEqualTo("<event><m>a&#10;bc&#9;d</m></event>\n");
        }
    }

    @Nested
    @DisplayName("CSV")
    class Csv {

        @Test
        @DisplayName("fixed columns, quoted when needed, objects as JSON, one line per event (FMT-04)")
        void csv() {
            Map<String, Object> event = event();
            event.put("stacktrace", "line 1\nline 2");
            CsvEventFormatter csv = new CsvEventFormatter(List.of("level", "message", "user.name", "user", "error.type",
                    "missing", "stacktrace", "ok"), ',', false);

            assertThat(csv.format(event)).isEqualTo("INFO,\"Pago \"\"aprobado\"\" <ok> & listo\",Ana,"
                    + "\"{\"\"name\"\":\"\"Ana\"\",\"\"tags\"\":[\"\"a\"\",\"\"b\"\"],\"\"x\"\":null}\","
                    + "java.lang.IllegalStateException,,line 1\\nline 2,true\n");
            assertThat(csv.header()).isNull();
        }

        @Test
        @DisplayName("default columns, another delimiter and the header line")
        void header() {
            CsvEventFormatter csv = new CsvEventFormatter(List.of(), ';', true);

            assertThat(csv.columns()).isEqualTo(CsvEventFormatter.DEFAULT_COLUMNS);
            assertThat(csv.header()).isEqualTo("timestamp;level;logger;thread;message\n");
            assertThat(csv.format(Map.of("level", "INFO", "message", "a;b"))).isEqualTo(";INFO;;;\"a;b\"\n");
        }

        @Test
        @DisplayName("a quote or a line break cannot be the delimiter")
        void invalidDelimiter() {
            assertThatThrownBy(() -> new CsvEventFormatter(List.of(), '"', false))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("format selection")
    class Selection {

        @Test
        @DisplayName("json, xml, csv and custom formats are created from the configuration")
        void create() {
            ClassLoader loader = getClass().getClassLoader();

            assertThat(StructuredFormat.create(StructuredFormat.type(" Json "), true, List.of(), ",", false, null,
                    loader)).isInstanceOf(JsonEventFormatter.class);
            assertThat(StructuredFormat.create(StructuredFormat.type("xml"), false, List.of(), ",", false, null,
                    loader)).isInstanceOf(XmlEventFormatter.class);
            assertThat(StructuredFormat.create(StructuredFormat.type("CSV"), false, List.of("level"), "|", true, null,
                    loader).header()).isEqualTo("level\n");
            LogEventFormatter custom = StructuredFormat.create(StructuredFormat.Type.CUSTOM, false, List.of(), ",",
                    false, KeyValueFormatter.class.getName(), loader);
            assertThat(custom.format(Map.of("level", "INFO"))).isEqualTo("level=INFO\n");
            assertThat(custom.header()).isNull();
        }

        @Test
        @DisplayName("invalid formats, delimiters and custom formatters are rejected with the property name")
        void invalid() {
            ClassLoader loader = getClass().getClassLoader();

            assertThatThrownBy(() -> StructuredFormat.type("yaml")).hasMessageContaining("pragma.log.format");
            assertThatThrownBy(() -> StructuredFormat.create(StructuredFormat.Type.CSV, false, List.of(), ";;", false,
                    null, loader)).hasMessageContaining("pragma.log.csv.delimiter");
            assertThatThrownBy(() -> StructuredFormat.create(StructuredFormat.Type.CUSTOM, false, List.of(), ",",
                    false, " ", loader)).hasMessageContaining("pragma.log.custom-formatter");
            assertThatThrownBy(() -> StructuredFormat.create(StructuredFormat.Type.CUSTOM, false, List.of(), ",",
                    false, String.class.getName(), loader)).hasMessageContaining("java.lang.String");
        }
    }
}
