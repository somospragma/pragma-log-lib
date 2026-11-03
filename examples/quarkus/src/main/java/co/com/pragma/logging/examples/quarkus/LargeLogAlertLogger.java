package co.com.pragma.logging.examples.quarkus;

import co.com.pragma.logging.core.alert.LargeLogAlert;
import co.com.pragma.logging.core.alert.LogAlertListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Alert listener set by class name in {@code pragma.log.alerts.listeners}. A real one would count a metric; logging
 * from a listener is safe, pragma-log does not alert on the listener's own logs.
 */
public class LargeLogAlertLogger implements LogAlertListener {

    private static final Logger log = LoggerFactory.getLogger(LargeLogAlertLogger.class);

    @Override
    public void onLargeLog(LargeLogAlert alert) {
        log.warn("Large log from {}: {} bytes", alert.logger(), alert.sizeBytes());
    }
}
