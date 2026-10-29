package co.com.pragma.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;

/**
 * Read-only view of a logging event whose message, arguments, MDC and key-value pairs were already masked.
 * Everything else is delegated to the original event, which is never modified.
 */
final class MaskedLoggingEvent implements ILoggingEvent {

    private final ILoggingEvent delegate;
    private final String formattedMessage;
    private final Object[] arguments;
    private final Map<String, String> mdc;
    private final List<KeyValuePair> keyValuePairs;

    MaskedLoggingEvent(ILoggingEvent delegate, String formattedMessage, Object[] arguments,
            Map<String, String> mdc, List<KeyValuePair> keyValuePairs) {
        this.delegate = delegate;
        this.formattedMessage = formattedMessage;
        this.arguments = arguments;
        this.mdc = mdc;
        this.keyValuePairs = keyValuePairs;
    }

    @Override
    public String getFormattedMessage() {
        return formattedMessage;
    }

    @Override
    public Object[] getArgumentArray() {
        return arguments;
    }

    @Override
    public Map<String, String> getMDCPropertyMap() {
        return mdc;
    }

    @Override
    @Deprecated
    public Map<String, String> getMdc() {
        return mdc;
    }

    @Override
    public List<KeyValuePair> getKeyValuePairs() {
        return keyValuePairs;
    }

    @Override
    public String getThreadName() {
        return delegate.getThreadName();
    }

    @Override
    public Level getLevel() {
        return delegate.getLevel();
    }

    @Override
    public String getMessage() {
        return delegate.getMessage();
    }

    @Override
    public String getLoggerName() {
        return delegate.getLoggerName();
    }

    @Override
    public LoggerContextVO getLoggerContextVO() {
        return delegate.getLoggerContextVO();
    }

    @Override
    public IThrowableProxy getThrowableProxy() {
        return delegate.getThrowableProxy();
    }

    @Override
    public StackTraceElement[] getCallerData() {
        return delegate.getCallerData();
    }

    @Override
    public boolean hasCallerData() {
        return delegate.hasCallerData();
    }

    @Override
    public List<Marker> getMarkerList() {
        return delegate.getMarkerList();
    }

    @Override
    public long getTimeStamp() {
        return delegate.getTimeStamp();
    }

    @Override
    public int getNanoseconds() {
        return delegate.getNanoseconds();
    }

    @Override
    public Instant getInstant() {
        return delegate.getInstant();
    }

    @Override
    public long getSequenceNumber() {
        return delegate.getSequenceNumber();
    }

    @Override
    public void prepareForDeferredProcessing() {
        delegate.prepareForDeferredProcessing();
    }
}
