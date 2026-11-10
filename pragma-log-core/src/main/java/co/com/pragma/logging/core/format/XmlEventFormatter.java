package co.com.pragma.logging.core.format;

import java.util.Iterator;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * XML, one {@code <event>} element per line (FMT-04). Field names become element names; characters that XML
 * does not allow in names become {@code _}. Objects become nested elements and arrays repeat an {@code <item>}
 * element.
 *
 * <pre>{@code <event><timestamp>2026-11-10T10:15:30Z</timestamp><level>INFO</level>...</event>}</pre>
 */
public final class XmlEventFormatter implements LogEventFormatter {

    private static final String ROOT = "event";
    private static final String ITEM = "item";

    @Override
    public String format(Map<String, Object> event) {
        StringBuilder xml = new StringBuilder(256);
        xml.append('<').append(ROOT).append('>');
        event.forEach((name, value) -> element(xml, name, value));
        xml.append("</").append(ROOT).append(">\n");
        return xml.toString();
    }

    private static void element(StringBuilder xml, String name, Object value) {
        String tag = elementName(name);
        if (value == null) {
            xml.append('<').append(tag).append("/>");
            return;
        }
        xml.append('<').append(tag).append('>');
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, child) -> element(xml, String.valueOf(key), child));
        } else if (value instanceof JsonNode node) {
            node(xml, node);
        } else {
            escape(xml, value.toString());
        }
        xml.append("</").append(tag).append('>');
    }

    private static void node(StringBuilder xml, JsonNode node) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> property : node.properties()) {
                nodeElement(xml, property.getKey(), property.getValue());
            }
        } else if (node.isArray()) {
            for (Iterator<JsonNode> items = node.values().iterator(); items.hasNext(); ) {
                nodeElement(xml, ITEM, items.next());
            }
        } else if (!node.isNull()) {
            escape(xml, node.asString());
        }
    }

    private static void nodeElement(StringBuilder xml, String name, JsonNode node) {
        String tag = elementName(name);
        if (node.isNull()) {
            xml.append('<').append(tag).append("/>");
            return;
        }
        xml.append('<').append(tag).append('>');
        node(xml, node);
        xml.append("</").append(tag).append('>');
    }

    /** A valid XML element name: letters, digits, {@code . - _}, not starting with a digit, dot or hyphen. */
    static String elementName(String name) {
        if (name == null || name.isEmpty()) {
            return "_";
        }
        StringBuilder tag = new StringBuilder(name.length() + 1);
        char first = name.charAt(0);
        if (!Character.isLetter(first) && first != '_') {
            tag.append('_');
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            tag.append(Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' ? c : '_');
        }
        return tag.toString();
    }

    private static void escape(StringBuilder xml, String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '<' -> xml.append("&lt;");
                case '>' -> xml.append("&gt;");
                case '&' -> xml.append("&amp;");
                case '"' -> xml.append("&quot;");
                case '\'' -> xml.append("&apos;");
                case '\n' -> xml.append("&#10;");
                case '\r' -> xml.append("&#13;");
                case '\t' -> xml.append("&#9;");
                default -> {
                    // Control characters are not allowed in XML 1.0, even escaped.
                    if (c >= 0x20 || Character.isSurrogate(c)) {
                        xml.append(c);
                    }
                }
            }
        }
    }
}
