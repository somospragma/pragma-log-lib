package co.com.pragma.logging.quarkus.slf4j;

import java.util.List;
import org.jboss.logmanager.ExtLogRecord;
import org.slf4j.event.KeyValuePair;
import org.slf4j.helpers.MessageFormatter;

/**
 * A log record written through SLF4J that keeps the message template, the raw arguments and the key-values,
 * so the pragma-log formatter masks them before they become text (MSK-07). Other formatters see the usual
 * SLF4J text through {@link #getMessage()}, computed only when asked for.
 */
public final class Slf4jLogRecord extends ExtLogRecord {

    private static final long serialVersionUID = 1L;

    private final String template;
    private final transient Object[] arguments;
    private final transient List<KeyValuePair> keyValues;
    private transient String formatted;

    public Slf4jLogRecord(java.util.logging.Level level, String template, Object[] arguments, List<KeyValuePair> keyValues,
            String loggerClassName) {
        super(level, template, FormatStyle.NO_FORMAT, loggerClassName);
        this.template = template;
        this.arguments = arguments;
        this.keyValues = keyValues == null ? List.of() : keyValues;
    }

    public String template() {
        return template;
    }

    public Object[] arguments() {
        return arguments;
    }

    public List<KeyValuePair> keyValues() {
        return keyValues;
    }

    /** The message as SLF4J formats it, with key-values first like SLF4J does for backends without them. */
    @Override
    public String getMessage() {
        String text = formatted;
        if (text == null) {
            StringBuilder builder = new StringBuilder();
            for (KeyValuePair pair : keyValues) {
                builder.append(pair.key).append('=').append(pair.value).append(' ');
            }
            builder.append(arguments == null || arguments.length == 0
                    ? template
                    : MessageFormatter.basicArrayFormat(template, arguments));
            text = builder.toString();
            formatted = text;
        }
        return text;
    }
}
