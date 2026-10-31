package co.com.pragma.logging.quarkus.slf4j;

import java.util.Arrays;
import java.util.List;
import org.jboss.logmanager.Logger;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;
import org.slf4j.event.Level;
import org.slf4j.event.LoggingEvent;
import org.slf4j.helpers.AbstractLogger;
import org.slf4j.spi.DefaultLoggingEventBuilder;
import org.slf4j.spi.LoggingEventAware;

/**
 * SLF4J logger on JBoss LogManager that hands the template, arguments and key-values to the log record
 * unformatted ({@link Slf4jLogRecord}), unlike slf4j-jboss-logmanager, which formats them with toString()
 * before any formatter can mask them.
 */
final class PragmaSlf4jLogger extends AbstractLogger implements LoggingEventAware {

    private static final long serialVersionUID = 1L;

    private final transient Logger logger;

    PragmaSlf4jLogger(Logger logger) {
        this.logger = logger;
        this.name = logger.getName();
    }

    @Override
    public boolean isTraceEnabled() {
        return logger.isLoggable(org.jboss.logmanager.Level.TRACE);
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return isTraceEnabled();
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isLoggable(org.jboss.logmanager.Level.DEBUG);
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return isDebugEnabled();
    }

    @Override
    public boolean isInfoEnabled() {
        return logger.isLoggable(org.jboss.logmanager.Level.INFO);
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return isInfoEnabled();
    }

    @Override
    public boolean isWarnEnabled() {
        return logger.isLoggable(org.jboss.logmanager.Level.WARN);
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return isWarnEnabled();
    }

    @Override
    public boolean isErrorEnabled() {
        return logger.isLoggable(org.jboss.logmanager.Level.ERROR);
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return isErrorEnabled();
    }

    @Override
    protected String getFullyQualifiedCallerName() {
        return AbstractLogger.class.getName();
    }

    @Override
    protected void handleNormalizedLoggingCall(Level level, Marker marker, String messagePattern, Object[] arguments,
            Throwable throwable) {
        write(level, messagePattern, arguments, null, throwable, AbstractLogger.class.getName());
    }

    /** Fluent API ({@code log.atInfo().addKeyValue(...)}): key-values stay structured. */
    @Override
    public void log(LoggingEvent event) {
        if (logger.isLoggable(toJBoss(event.getLevel()))) {
            String boundary = event.getCallerBoundary() == null
                    ? DefaultLoggingEventBuilder.class.getName()
                    : event.getCallerBoundary();
            write(event.getLevel(), event.getMessage(), event.getArgumentArray(), event.getKeyValuePairs(),
                    event.getThrowable(), boundary);
        }
    }

    private void write(Level level, String template, Object[] arguments, List<KeyValuePair> keyValues,
            Throwable throwable, String loggerClassName) {
        Object[] args = arguments;
        Throwable thrown = throwable;
        // SLF4J convention: a trailing Throwable argument is the exception of the event.
        if (thrown == null && args != null && args.length > 0 && args[args.length - 1] instanceof Throwable last) {
            thrown = last;
            args = Arrays.copyOf(args, args.length - 1);
        }
        Slf4jLogRecord record = new Slf4jLogRecord(toJBoss(level), template, args, keyValues, loggerClassName);
        record.setThrown(thrown);
        logger.logRaw(record);
    }

    private static java.util.logging.Level toJBoss(Level level) {
        return switch (level) {
            case ERROR -> org.jboss.logmanager.Level.ERROR;
            case WARN -> org.jboss.logmanager.Level.WARN;
            case INFO -> org.jboss.logmanager.Level.INFO;
            case DEBUG -> org.jboss.logmanager.Level.DEBUG;
            case TRACE -> org.jboss.logmanager.Level.TRACE;
        };
    }
}
