package co.com.pragma.logging.core.alert;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Checks events against {@code max-log-size} and dispatches alerts to the configured listeners and to those
 * registered in code (ALR-01..03). Thread-safe.
 */
public final class LogAlerts {

    private static final List<LogAlertListener> REGISTERED = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<Boolean> DISPATCHING = new ThreadLocal<>();
    private static final LogAlerts NONE = new LogAlerts(0, false, List.of());

    private final long maxLogSize;
    private final List<LogAlertListener> listeners;

    /**
     * @param maxLogSize maximum UTF-8 bytes of an encoded event before {@code onLargeLog}; {@code 0} disables it
     * @param console    whether alerts are also written to standard error
     * @param listeners  listeners from configuration
     * @throws IllegalArgumentException when {@code maxLogSize} is negative
     */
    public LogAlerts(long maxLogSize, boolean console, List<LogAlertListener> listeners) {
        if (maxLogSize < 0) {
            throw new IllegalArgumentException("pragma.log.alerts.max-log-size must be >= 0");
        }
        this.maxLogSize = maxLogSize;
        List<LogAlertListener> all = new ArrayList<>();
        if (console) {
            all.add(new ConsoleAlertListener());
        }
        all.addAll(listeners);
        this.listeners = List.copyOf(all);
    }

    /** No size limit and only listeners registered in code. */
    public static LogAlerts none() {
        return NONE;
    }

    /** Adds a listener for every pragma-log encoder and formatter of this class loader. */
    public static void register(LogAlertListener listener) {
        REGISTERED.add(listener);
    }

    public static void unregister(LogAlertListener listener) {
        REGISTERED.remove(listener);
    }

    /**
     * Creates a listener from its class name.
     *
     * @throws IllegalArgumentException when it cannot be loaded or is not a {@link LogAlertListener}
     */
    public static LogAlertListener load(String className, ClassLoader classLoader) {
        try {
            Class<?> type = Class.forName(className.trim(), true, classLoader);
            return (LogAlertListener) type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
            throw new IllegalArgumentException("cannot load alert listener " + className
                    + " (it must implement LogAlertListener and have a public no-arg constructor)", e);
        }
    }

    public long maxLogSize() {
        return maxLogSize;
    }

    /** Alerts when an encoded event exceeds the maximum; {@code sizeBytes} are UTF-8 bytes. */
    public void checkSize(String logger, String level, long sizeBytes) {
        if (maxLogSize > 0 && sizeBytes > maxLogSize) {
            LargeLogAlert alert = new LargeLogAlert(logger, level, sizeBytes, maxLogSize);
            dispatch(listener -> listener.onLargeLog(alert));
        }
    }

    /** Same as {@link #checkSize(String, String, long)} for a text that is not encoded yet. */
    public void checkSize(String logger, String level, CharSequence text) {
        // A char takes 1 to 3 UTF-8 bytes (surrogate pairs take 4 for 2 chars): measure only when it can exceed.
        if (maxLogSize > 0 && (long) text.length() * 3 > maxLogSize) {
            checkSize(logger, level, utf8Length(text));
        }
    }

    public void maskingError(String logger, Throwable error) {
        MaskingErrorAlert alert = new MaskingErrorAlert(logger, error);
        dispatch(listener -> listener.onMaskingError(alert));
    }

    /** Reports required fields an event did not have; does nothing when the list is empty. */
    public void missingFields(String logger, String level, List<String> fields) {
        if (!fields.isEmpty()) {
            MissingFieldsAlert alert = new MissingFieldsAlert(logger, level, fields);
            dispatch(listener -> listener.onMissingFields(alert));
        }
    }

    private void dispatch(Consumer<LogAlertListener> call) {
        if (DISPATCHING.get() != null) {
            return; // a listener logged something: no alerts about its own logs
        }
        DISPATCHING.set(Boolean.TRUE);
        try {
            listeners.forEach(listener -> safely(call, listener));
            REGISTERED.forEach(listener -> safely(call, listener));
        } finally {
            DISPATCHING.remove();
        }
    }

    private static void safely(Consumer<LogAlertListener> call, LogAlertListener listener) {
        try {
            call.accept(listener);
        } catch (RuntimeException | LinkageError e) {
            // A listener must never break logging (ADR-15).
        }
    }

    static long utf8Length(CharSequence text) {
        long bytes = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x80) {
                bytes++;
            } else if (c < 0x800) {
                bytes += 2;
            } else if (Character.isHighSurrogate(c) && i + 1 < text.length()
                    && Character.isLowSurrogate(text.charAt(i + 1))) {
                bytes += 4;
                i++;
            } else {
                bytes += 3;
            }
        }
        return bytes;
    }
}
