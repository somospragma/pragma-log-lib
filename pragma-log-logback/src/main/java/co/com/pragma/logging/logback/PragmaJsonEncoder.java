package co.com.pragma.logging.logback;

import co.com.pragma.logging.core.PragmaLogSettings;
import co.com.pragma.logging.core.StandardFields;
import java.util.Map;
import net.logstash.logback.encoder.LogstashEncoder;
import net.logstash.logback.fieldnames.LogstashFieldNames;
import tools.jackson.databind.json.JsonMapper;

/**
 * Logback encoder that writes one JSON object per event following the Pragma log schema.
 *
 * <p>Built on {@link LogstashEncoder} (ADR-01): MDC entries and SLF4J key-value pairs are
 * included as top-level fields. Usage in {@code logback.xml}:
 *
 * <pre>{@code
 * <encoder class="co.com.pragma.logging.logback.PragmaJsonEncoder">
 *   <appName>my-service</appName>
 * </encoder>
 * }</pre>
 */
public class PragmaJsonEncoder extends LogstashEncoder {

    private static final String IGNORE = "[ignore]";

    private String appName = PragmaLogSettings.DEFAULT_APP_NAME;

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getAppName() {
        return appName;
    }

    @Override
    public void start() {
        PragmaLogSettings settings = new PragmaLogSettings(appName);
        applySchemaFieldNames(getFieldNames());
        setCustomFields(JsonMapper.shared().writeValueAsString(Map.of(StandardFields.APP, settings.appName())));
        super.start();
    }

    private static void applySchemaFieldNames(LogstashFieldNames names) {
        names.setTimestamp(StandardFields.TIMESTAMP);
        names.setLevel(StandardFields.LEVEL);
        names.setLogger(StandardFields.LOGGER);
        names.setThread(StandardFields.THREAD);
        names.setMessage(StandardFields.MESSAGE);
        names.setStackTrace(StandardFields.STACKTRACE);
        names.setVersion(IGNORE);
        names.setLevelValue(IGNORE);
    }
}
