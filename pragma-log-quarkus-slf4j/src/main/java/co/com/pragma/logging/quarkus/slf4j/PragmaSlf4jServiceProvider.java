package co.com.pragma.logging.quarkus.slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logmanager.LogContext;
import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.Logger;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

/**
 * SLF4J binding of the Quarkus extension. It replaces slf4j-jboss-logmanager (excluded by the extension)
 * so SLF4J arguments and key-values reach the pragma-log formatter before they are turned into text.
 * Loaded parent-first, like JBoss LogManager, so the formatter sees the same {@link Slf4jLogRecord} class.
 */
public final class PragmaSlf4jServiceProvider implements SLF4JServiceProvider {

    private ILoggerFactory loggerFactory;
    private IMarkerFactory markerFactory;
    private MDCAdapter mdcAdapter;

    @Override
    public ILoggerFactory getLoggerFactory() {
        return loggerFactory;
    }

    @Override
    public IMarkerFactory getMarkerFactory() {
        return markerFactory;
    }

    @Override
    public MDCAdapter getMDCAdapter() {
        return mdcAdapter;
    }

    @Override
    public String getRequestedApiVersion() {
        return "2.0.99";
    }

    @Override
    public void initialize() {
        loggerFactory = new LoggerFactory();
        markerFactory = new BasicMarkerFactory();
        mdcAdapter = new JBossMdcAdapter();
    }

    private static final class LoggerFactory implements ILoggerFactory {

        private final Map<String, Logger> loggers = new ConcurrentHashMap<>();

        @Override
        public Logger getLogger(String name) {
            return loggers.computeIfAbsent(name,
                    key -> new PragmaSlf4jLogger(LogContext.getLogContext().getLogger(key)));
        }
    }
}
