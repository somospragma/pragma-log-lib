package co.com.pragma.logging.logback;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import co.com.pragma.logging.core.enrich.LogEnrichers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.event.KeyValuePair;

/**
 * Asynchronous appender with safe defaults for pragma-log: the logging call returns right away and another thread
 * masks, encodes and writes the event.
 *
 * <ul>
 *   <li>No level is discarded when the queue fills up ({@code discardingThreshold} 0) and the caller waits instead of
 *       losing events ({@code neverBlock} false). Set {@code neverBlock} to true to drop events rather than wait.</li>
 *   <li>The MDC and the fields of enrichers registered in code or as beans are taken on the calling thread, so they
 *       describe the right request.</li>
 *   <li>Pending events are written when the logging system stops, up to {@code maxFlushTime}.</li>
 * </ul>
 *
 * <p>Logged objects are masked on the writing thread: log objects that are not modified afterwards.
 *
 * <pre>{@code
 * <appender name="ASYNC" class="co.com.pragma.logging.logback.PragmaAsyncAppender">
 *   <queueSize>8192</queueSize>
 *   <appender-ref ref="CONSOLE"/>
 * </appender>
 * }</pre>
 */
public final class PragmaAsyncAppender extends AsyncAppender {

    public static final int DEFAULT_QUEUE_SIZE = 8192;
    public static final int DEFAULT_MAX_FLUSH_TIME_MS = 5000;

    public PragmaAsyncAppender() {
        setQueueSize(DEFAULT_QUEUE_SIZE);
        setDiscardingThreshold(0);
        setNeverBlock(false);
        setMaxFlushTime(DEFAULT_MAX_FLUSH_TIME_MS);
        setIncludeCallerData(false);
    }

    @Override
    protected void append(ILoggingEvent event) {
        super.append(withCallerFields(event));
    }

    /** The event with the enricher fields of the calling thread as key-values; the event itself when there are none. */
    static ILoggingEvent withCallerFields(ILoggingEvent event) {
        Map<String, Object> enriched = LogEnrichers.none().fields(event.getLoggerName(), String.valueOf(event.getLevel()));
        if (enriched.isEmpty()) {
            return event;
        }
        List<KeyValuePair> pairs = new ArrayList<>();
        enriched.forEach((name, value) -> pairs.add(new KeyValuePair(name, value)));
        if (event.getKeyValuePairs() != null) {
            pairs.addAll(event.getKeyValuePairs());
        }
        return new MaskedLoggingEvent(event, event.getMessage(), event.getArgumentArray(), event.getMDCPropertyMap(),
                pairs);
    }
}
