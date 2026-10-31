package co.com.pragma.logging.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Exception rendering for adapters that do not get it from their logging backend (ERR-01..04): a bounded
 * stack trace and the root cause. Cause chains may be cyclic; every walk is bounded.
 */
public final class Throwables {

    private static final int MAX_CHAIN = 64;

    private Throwables() {
    }

    /** Last cause of the chain, or {@code throwable} itself. */
    public static Throwable rootCause(Throwable throwable) {
        List<Throwable> chain = chain(throwable);
        return chain.get(chain.size() - 1);
    }

    /** {@code type: message}, or just the type when there is no message. */
    public static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null ? throwable.getClass().getName() : throwable.getClass().getName() + ": " + message;
    }

    /**
     * Stack trace in the JDK layout ({@code Caused by:}), or root cause first ({@code Wrapped by:}).
     *
     * @param maxDepth        frames per exception of the chain; the rest is summarized as {@code ... n more}
     * @param maxLength       maximum characters of the whole text
     * @param rootCauseFirst  whether the root cause is written first
     */
    public static String stackTrace(Throwable throwable, int maxDepth, int maxLength, boolean rootCauseFirst) {
        List<Throwable> chain = chain(throwable);
        if (rootCauseFirst) {
            chain = new ArrayList<>(chain);
            Collections.reverse(chain);
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < chain.size(); i++) {
            Throwable current = chain.get(i);
            if (i > 0) {
                text.append(rootCauseFirst ? "Wrapped by: " : "Caused by: ");
            }
            text.append(describe(current)).append('\n');
            StackTraceElement[] frames = current.getStackTrace();
            int shown = Math.min(frames.length, Math.max(maxDepth, 0));
            for (int f = 0; f < shown; f++) {
                text.append("\tat ").append(frames[f]).append('\n');
            }
            if (frames.length > shown) {
                text.append("\t... ").append(frames.length - shown).append(" more\n");
            }
            if (text.length() > maxLength) {
                break;
            }
        }
        if (text.length() > maxLength) {
            text.setLength(Math.max(maxLength - 3, 0));
            text.append("...");
        }
        return text.toString();
    }

    private static List<Throwable> chain(Throwable throwable) {
        List<Throwable> chain = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = throwable; current != null && chain.size() < MAX_CHAIN && seen.add(current);
                current = current.getCause()) {
            chain.add(current);
        }
        return chain;
    }
}
