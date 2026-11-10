package co.com.pragma.logging.spring;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AsyncAppenderBase;
import co.com.pragma.logging.logback.PragmaAsyncAppender;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.GenericApplicationListener;
import org.springframework.core.ResolvableType;

/**
 * {@code pragma.log.async.enabled}: right after Spring Boot sets up logging, its console and file appenders are
 * wrapped in a {@link PragmaAsyncAppender}, so logging calls do not wait for the output. Pending events are written
 * when the application stops. Registered in {@code META-INF/spring.factories}.
 */
public class PragmaAsyncLogging implements GenericApplicationListener {

    static final String NAME_PREFIX = "PRAGMA_ASYNC_";

    @Override
    public boolean supportsEventType(ResolvableType eventType) {
        return ApplicationEnvironmentPreparedEvent.class.isAssignableFrom(eventType.toClass());
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (!(event instanceof ApplicationEnvironmentPreparedEvent prepared)) {
            return;
        }
        Binder binder = Binder.get(prepared.getEnvironment());
        boolean enabled = !Boolean.FALSE.equals(binder.bind(PragmaLogProperties.PREFIX + ".enabled", Boolean.class)
                .orElse(null));
        PragmaLogProperties.Async async = binder.bind(PragmaLogProperties.PREFIX + ".async",
                PragmaLogProperties.Async.class).orElseGet(PragmaLogProperties.Async::new);
        if (enabled && async.isEnabled() && LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            wrap(context, async);
        }
    }

    /** Runs after Spring Boot's logging listener has configured the appenders. */
    @Override
    public int getOrder() {
        return LoggingApplicationListener.DEFAULT_ORDER + 1;
    }

    static void wrap(LoggerContext context, PragmaLogProperties.Async settings) {
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        List<Appender<ILoggingEvent>> appenders = new ArrayList<>();
        for (Iterator<Appender<ILoggingEvent>> it = root.iteratorForAppenders(); it.hasNext(); ) {
            Appender<ILoggingEvent> appender = it.next();
            if (!(appender instanceof AsyncAppenderBase)) {
                appenders.add(appender);
            }
        }
        for (Appender<ILoggingEvent> appender : appenders) {
            PragmaAsyncAppender async = new PragmaAsyncAppender();
            async.setContext(context);
            async.setName(NAME_PREFIX + appender.getName());
            async.setQueueSize(settings.getQueueSize());
            async.setNeverBlock(settings.isNeverBlock());
            async.setMaxFlushTime(Math.toIntExact(settings.getMaxFlushTime().toMillis()));
            async.addAppender(appender);
            async.start();
            root.detachAppender(appender);
            root.addAppender(async);
        }
    }
}
