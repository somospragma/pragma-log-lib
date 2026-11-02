package co.com.pragma.logging.logback;

import co.com.pragma.logging.core.alert.LogAlertListener;
import co.com.pragma.logging.core.alert.LogAlerts;
import java.util.ArrayList;
import java.util.List;

/** Alert properties shared by the JSON and text encoders (ALR-01..03), as set from {@code logback.xml}. */
final class AlertOptions {

    private long maxLogSize;
    private boolean console = true;
    private final List<String> listeners = new ArrayList<>();

    void setMaxLogSize(long maxLogSize) {
        this.maxLogSize = maxLogSize;
    }

    void setConsole(boolean console) {
        this.console = console;
    }

    void addListener(String className) {
        listeners.add(className);
    }

    /** @throws IllegalArgumentException when the configuration is invalid */
    LogAlerts build() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        List<LogAlertListener> loaded = new ArrayList<>();
        listeners.forEach(className -> loaded.add(LogAlerts.load(className, classLoader)));
        return new LogAlerts(maxLogSize, console, loaded);
    }
}
