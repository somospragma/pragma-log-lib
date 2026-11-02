package co.com.pragma.logging.core.masking;

import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Last pass over the texts of an event: pattern masking (MSK-10), then truncation of long values (FLD-08).
 * Masking goes first so a cut never leaves part of a secret unmasked. Thread-safe.
 */
public final class TextSanitizer {

    /** Appended to truncated values, followed by the number of characters removed and {@code "]"}. */
    public static final String TRUNCATED = "...[truncated ";

    private static final TextSanitizer NONE = new TextSanitizer(PatternMasker.none(), 0);

    private final PatternMasker patterns;
    private final int maxFieldLength;

    /**
     * @param maxFieldLength maximum characters of the message and of each text value; {@code 0} for no limit
     * @throws IllegalArgumentException when {@code maxFieldLength} is negative
     */
    public TextSanitizer(PatternMasker patterns, int maxFieldLength) {
        if (maxFieldLength < 0) {
            throw new IllegalArgumentException("pragma.log.max-field-length must be >= 0");
        }
        this.patterns = patterns;
        this.maxFieldLength = maxFieldLength;
    }

    public static TextSanitizer none() {
        return NONE;
    }

    /** Whether this sanitizer changes nothing, so callers can skip it. */
    public boolean isEmpty() {
        return patterns.isEmpty() && maxFieldLength == 0;
    }

    /** Message or text field value: masked and truncated. */
    public String text(String value) {
        return truncate(patterns.mask(value));
    }

    /** Stack traces and error messages: masked only, they have their own length limit. */
    public String maskOnly(String value) {
        return patterns.mask(value);
    }

    /**
     * A field value: text is masked and truncated, JSON trees are copied with their text nodes sanitized, other
     * values are returned as they are.
     */
    public Object value(Object value) {
        if (isEmpty()) {
            return value;
        }
        if (value instanceof String text) {
            return text(text);
        }
        if (value instanceof JsonNode node) {
            return tree(node);
        }
        return value;
    }

    private JsonNode tree(JsonNode node) {
        if (node.isString()) {
            String text = node.asString();
            String sanitized = text(text);
            return sanitized.equals(text) ? node : JsonNodeFactory.instance.stringNode(sanitized);
        }
        if (node.isObject()) {
            ObjectNode copy = JsonNodeFactory.instance.objectNode();
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                copy.set(field.getKey(), tree(field.getValue()));
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = JsonNodeFactory.instance.arrayNode(node.size());
            for (JsonNode element : node) {
                copy.add(tree(element));
            }
            return copy;
        }
        return node;
    }

    private String truncate(String value) {
        if (value == null || maxFieldLength == 0 || value.length() <= maxFieldLength) {
            return value;
        }
        int keep = maxFieldLength;
        if (Character.isHighSurrogate(value.charAt(keep - 1))) {
            keep--;
        }
        return value.substring(0, keep) + TRUNCATED + (value.length() - keep) + " chars]";
    }
}
