package co.com.pragma.logging.core.format;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** ANSI escape codes for the plain text format (FMT-05): color names or hex values such as {@code #ff8800}. */
public final class AnsiColors {

    public static final String RESET = "\u001b[0m";

    private static final Pattern HEX = Pattern.compile("#?([0-9a-fA-F]{6}|[0-9a-fA-F]{3})");

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("black", "30"), Map.entry("red", "31"), Map.entry("green", "32"), Map.entry("yellow", "33"),
            Map.entry("blue", "34"), Map.entry("magenta", "35"), Map.entry("cyan", "36"), Map.entry("white", "37"),
            Map.entry("bright_black", "90"), Map.entry("bright_red", "91"), Map.entry("bright_green", "92"),
            Map.entry("bright_yellow", "93"), Map.entry("bright_blue", "94"), Map.entry("bright_magenta", "95"),
            Map.entry("bright_cyan", "96"), Map.entry("bright_white", "97"), Map.entry("faint", "2"),
            Map.entry("bold", "1"), Map.entry("default", "39"));

    private AnsiColors() {
    }

    /**
     * Escape code of a color: a name ({@code red}, {@code bright_blue}, {@code faint}, …, case and
     * {@code -}/{@code _} ignored) or a hex value ({@code #ff8800}, {@code #f80}) written as 24-bit color.
     *
     * @throws IllegalArgumentException when the color is unknown
     */
    public static String code(String color) {
        if (color == null || color.isBlank()) {
            throw new IllegalArgumentException("color must not be blank");
        }
        String name = color.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        String sgr = NAMES.get(name);
        if (sgr != null) {
            return "\u001b[" + sgr + "m";
        }
        if (name.startsWith("#") && HEX.matcher(name).matches()) {
            String hex = name.substring(1);
            if (hex.length() == 3) {
                hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
            }
            int rgb = Integer.parseInt(hex, 16);
            return "\u001b[38;2;" + (rgb >> 16) + ";" + ((rgb >> 8) & 0xff) + ";" + (rgb & 0xff) + "m";
        }
        throw new IllegalArgumentException("unknown color '" + color + "': use a name such as red, bright_blue or"
                + " faint, or a hex value such as #ff8800");
    }
}
