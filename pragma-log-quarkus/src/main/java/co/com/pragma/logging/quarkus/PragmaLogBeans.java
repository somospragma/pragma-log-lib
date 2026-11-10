package co.com.pragma.logging.quarkus;

import co.com.pragma.logging.core.alert.LogAlertListener;
import co.com.pragma.logging.core.alert.LogAlerts;
import co.com.pragma.logging.core.enrich.LogEnricher;
import co.com.pragma.logging.core.enrich.LogEnrichers;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds the application's {@link LogEnricher} and {@link LogAlertListener} CDI beans to pragma-log (ENR-07, ALR-02).
 * The formatters exist before the beans, so they are registered globally at startup and removed at shutdown.
 */
@Singleton
public class PragmaLogBeans {

    private final List<LogEnricher> enrichers = new ArrayList<>();
    private final List<LogAlertListener> listeners = new ArrayList<>();

    void start(@Observes StartupEvent event, @Any Instance<LogEnricher> enricherBeans,
            @Any Instance<LogAlertListener> listenerBeans) {
        enricherBeans.forEach(enrichers::add);
        listenerBeans.forEach(listeners::add);
        enrichers.forEach(LogEnrichers::register);
        listeners.forEach(LogAlerts::register);
    }

    void stop(@Observes ShutdownEvent event) {
        enrichers.forEach(LogEnrichers::unregister);
        listeners.forEach(LogAlerts::unregister);
        enrichers.clear();
        listeners.clear();
    }
}
