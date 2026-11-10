package co.com.pragma.logging.logback;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Hot reload in plain Java is Logback's own {@code scan="true"}: it reads {@code logback.xml} again and builds new
 * encoders (CFG-06). This checks that a reconfigured encoder writes with the new settings.
 */
class ReconfigurationTest {

    private static final String CONFIG = """
            <configuration>
              <appender name="OUT" class="ch.qos.logback.core.ConsoleAppender">
                <encoder class="co.com.pragma.logging.logback.PragmaStructuredEncoder">
                  <appName>orders</appName>
                  <field>env=ENV</field>
                </encoder>
              </appender>
              <root level="INFO"><appender-ref ref="OUT"/></root>
            </configuration>
            """;

    @Test
    @DisplayName("reconfiguring Logback applies the new encoder settings (CFG-06)")
    void reconfigure() throws Exception {
        LoggerContext context = new LoggerContext();

        String before = encodeWith(context, "before");
        String after = encodeWith(context, "after");

        assertThat(before).contains("\"env\":\"before\"");
        assertThat(after).contains("\"env\":\"after\"");
        context.stop();
    }

    private static String encodeWith(LoggerContext context, String env) throws Exception {
        context.reset();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(new ByteArrayInputStream(CONFIG.replace("ENV", env).getBytes(StandardCharsets.UTF_8)));
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        ConsoleAppender<ILoggingEvent> appender = (ConsoleAppender<ILoggingEvent>) root.getAppender("OUT");
        LoggingEvent event = new LoggingEvent(Logger.FQCN, root, Level.INFO, "hello", null, null);
        return new String(appender.getEncoder().encode(event), StandardCharsets.UTF_8);
    }
}
