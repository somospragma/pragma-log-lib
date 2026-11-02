package co.com.pragma.logging.spring;

import co.com.pragma.logging.core.alert.LogAlertListener;
import co.com.pragma.logging.core.alert.LogAlerts;
import java.util.List;
import org.springframework.beans.factory.DisposableBean;

/**
 * Adds the application's {@link LogAlertListener} beans to pragma-log's alerts (ALR-02). The encoders exist
 * before the application context, so beans are registered globally and removed when the context closes.
 */
final class PragmaLogAlertRegistrar implements DisposableBean {

    private final List<LogAlertListener> listeners;

    PragmaLogAlertRegistrar(List<LogAlertListener> listeners) {
        this.listeners = List.copyOf(listeners);
        this.listeners.forEach(LogAlerts::register);
    }

    @Override
    public void destroy() {
        listeners.forEach(LogAlerts::unregister);
    }
}
