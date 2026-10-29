package co.com.pragma.logging.core.masking;

/** Built-in masking algorithms. All of them keep the original length of the text. */
final class BuiltInMasks {

    private BuiltInMasks() {
    }

    static String full(String value, char maskChar) {
        return String.valueOf(maskChar).repeat(value.length());
    }

    static String prefix(String value, char maskChar, int length) {
        if (length >= value.length()) {
            return full(value, maskChar);
        }
        return String.valueOf(maskChar).repeat(Math.max(length, 0)) + value.substring(Math.max(length, 0));
    }

    static String suffix(String value, char maskChar, int length) {
        if (length >= value.length()) {
            return full(value, maskChar);
        }
        int visible = value.length() - Math.max(length, 0);
        return value.substring(0, visible) + String.valueOf(maskChar).repeat(Math.max(length, 0));
    }

    static String middle(String value, char maskChar, int visibleAtEachEnd) {
        int visible = Math.max(visibleAtEachEnd, 0);
        if (visible * 2 >= value.length()) {
            return full(value, maskChar);
        }
        int masked = value.length() - visible * 2;
        return value.substring(0, visible)
                + String.valueOf(maskChar).repeat(masked)
                + value.substring(value.length() - visible);
    }
}
