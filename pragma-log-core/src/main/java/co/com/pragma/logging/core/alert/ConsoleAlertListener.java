package co.com.pragma.logging.core.alert;

import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Default listener: one line on standard error, at most once per minute for each logger and kind of alert, so a
 * loop that logs a huge object does not flood the console. The limit is shared by every encoder (console and
 * file appenders report the same event once). It writes to standard error directly, not through
 * the logging system, to avoid reentrance.
 */
final class ConsoleAlertListener implements LogAlertListener {

    private static final long INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);
    private static final int MAX_TRACKED = 1_000;

    private static final Map<String, Long> SHARED = new ConcurrentHashMap<>();

    private final PrintStream out;
    private final LongSupplier clock;
    private final Map<String, Long> lastWritten;

    ConsoleAlertListener() {
        this(System.err, System::nanoTime, SHARED);
    }

    ConsoleAlertListener(PrintStream out, LongSupplier clock, Map<String, Long> lastWritten) {
        this.out = out;
        this.clock = clock;
        this.lastWritten = lastWritten;
    }

    @Override
    public void onLargeLog(LargeLogAlert alert) {
        if (due("size:" + alert.logger())) {
            out.println("pragma-log WARN: a " + alert.level() + " log of " + alert.sizeBytes() + " bytes from "
                    + alert.logger() + " exceeds max-log-size (" + alert.maxBytes() + " bytes). It was written whole;"
                    + " check what that logger writes.");
        }
    }

    @Override
    public void onMaskingError(MaskingErrorAlert alert) {
        if (due("mask:" + alert.logger())) {
            out.println("pragma-log WARN: a log from " + alert.logger() + " could not be masked ("
                    + alert.error().getClass().getName() + "). It was written without arguments and with the"
                    + " pragmaLogError field.");
        }
    }

    private boolean due(String key) {
        long now = clock.getAsLong();
        if (lastWritten.size() > MAX_TRACKED) {
            lastWritten.clear();
        }
        Long previous = lastWritten.get(key);
        if (previous != null && now - previous < INTERVAL_NANOS) {
            return false;
        }
        lastWritten.put(key, now);
        return true;
    }
}
