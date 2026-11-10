package co.com.pragma.logging.logback;

import co.com.pragma.logging.core.alert.LogAlertListener;
import co.com.pragma.logging.core.alert.LogAlerts;
import co.com.pragma.logging.core.enrich.LogEnricher;
import co.com.pragma.logging.core.enrich.LogEnrichers;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Alert and enricher properties shared by the structured and text encoders (ALR-01..03, ENR-07), as set from {@code logback.xml}. */
final class AlertOptions {

    private long maxLogSize;
    private boolean console = true;
    private boolean truncate;
    private final List<String> listeners = new ArrayList<>();
    private final List<String> enrichers = new ArrayList<>();

    void setMaxLogSize(long maxLogSize) {
        this.maxLogSize = maxLogSize;
    }

    void setConsole(boolean console) {
        this.console = console;
    }

    void setTruncate(boolean truncate) {
        this.truncate = truncate;
    }

    boolean truncate() {
        return truncate;
    }

    long maxLogSize() {
        return maxLogSize;
    }

    void addListener(String className) {
        listeners.add(className);
    }

    void addEnricher(String className) {
        enrichers.add(className);
    }

    /** The enrichers that can be loaded; each one that cannot is reported and left out. */
    LogEnrichers buildEnrichers(Consumer<RuntimeException> onError) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        List<LogEnricher> loaded = new ArrayList<>();
        for (String className : enrichers) {
            try {
                loaded.add(LogEnrichers.load(className, classLoader));
            } catch (RuntimeException e) {
                onError.accept(e);
            }
        }
        return new LogEnrichers(loaded);
    }

    /** @throws IllegalArgumentException when the configuration is invalid */
    LogAlerts build() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        List<LogAlertListener> loaded = new ArrayList<>();
        listeners.forEach(className -> loaded.add(LogAlerts.load(className, classLoader)));
        return new LogAlerts(maxLogSize, console, loaded);
    }
}
