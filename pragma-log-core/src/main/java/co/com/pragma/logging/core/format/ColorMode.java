package co.com.pragma.logging.core.format;

import java.io.Console;
import java.lang.reflect.Method;

/** When the plain text console format uses colors (FMT-05). Files never get colors. */
public enum ColorMode {

    /** Colors only on an interactive terminal, and never when the {@code NO_COLOR} variable is set. */
    AUTO,
    /** Always colors, e.g. in an IDE console, which is not detected as a terminal. */
    ALWAYS,
    NEVER;

    public boolean enabled() {
        return switch (this) {
            case ALWAYS -> true;
            case NEVER -> false;
            case AUTO -> isTerminal();
        };
    }

    @SuppressWarnings("SystemConsoleNull") // null up to Java 21; isTerminal() below covers 22+
    private static boolean isTerminal() {
        String noColor = System.getenv("NO_COLOR");
        if ((noColor != null && !noColor.isEmpty()) || "dumb".equals(System.getenv("TERM"))) {
            return false;
        }
        Console console = System.console();
        if (console == null) {
            return false;
        }
        try {
            // Java 22+ returns a console even when output is redirected; isTerminal() tells them apart.
            Method isTerminal = Console.class.getMethod("isTerminal");
            return (Boolean) isTerminal.invoke(console);
        } catch (ReflectiveOperationException | LinkageError e) {
            // Before Java 22, or a native image without the method registered.
            return true;
        }
    }
}
