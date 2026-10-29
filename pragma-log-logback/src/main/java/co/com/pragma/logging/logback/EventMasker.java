package co.com.pragma.logging.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import co.com.pragma.logging.core.masking.LogMasker;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.event.KeyValuePair;
import org.slf4j.helpers.MessageFormatter;

/** Produces a masked view of a Logback event (MSK-07, MSK-09) or a safe fallback (ALR-05). */
final class EventMasker {

    /** Field added to events that could not be masked; holds the error type. */
    static final String ERROR_FIELD = "pragmaLogError";

    private final LogMasker masker;

    EventMasker(LogMasker masker) {
        this.masker = masker;
    }

    ILoggingEvent mask(ILoggingEvent event) {
        Object[] arguments = event.getArgumentArray();
        Object[] maskedArguments = maskArguments(arguments);
        String message = maskedArguments == null
                ? event.getFormattedMessage()
                : MessageFormatter.basicArrayFormat(event.getMessage(), maskedArguments);
        return new MaskedLoggingEvent(event, message, maskedArguments, maskMdc(event.getMDCPropertyMap()),
                maskKeyValuePairs(event.getKeyValuePairs()));
    }

    /** Event with the raw message template only: no arguments, MDC or key-values that could leak data. */
    ILoggingEvent fallback(ILoggingEvent event, Throwable error) {
        return new MaskedLoggingEvent(event, event.getMessage(), null,
                Map.of(ERROR_FIELD, error.getClass().getName()), null);
    }

    private Object[] maskArguments(Object[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return null;
        }
        Object[] masked = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            Object argument = arguments[i];
            masked[i] = argument instanceof Throwable ? argument.toString() : masker.toText(argument);
        }
        return masked;
    }

    private Map<String, String> maskMdc(Map<String, String> mdc) {
        if (mdc == null || mdc.isEmpty() || !hasSensitiveKey(mdc)) {
            return mdc;
        }
        Map<String, String> masked = new LinkedHashMap<>(mdc.size());
        mdc.forEach((key, value) -> {
            if (!masker.isSensitiveName(key) || value == null) {
                masked.put(key, value);
            } else if (!masker.hidesSensitiveNames()) {
                masked.put(key, masker.maskByName(value));
            }
        });
        return masked;
    }

    private boolean hasSensitiveKey(Map<String, String> mdc) {
        for (String key : mdc.keySet()) {
            if (masker.isSensitiveName(key)) {
                return true;
            }
        }
        return false;
    }

    private List<KeyValuePair> maskKeyValuePairs(List<KeyValuePair> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return pairs;
        }
        List<KeyValuePair> masked = new ArrayList<>(pairs.size());
        for (KeyValuePair pair : pairs) {
            if (!masker.isSensitiveName(pair.key) || pair.value == null) {
                masked.add(new KeyValuePair(pair.key, maskValue(pair.value)));
            } else if (!masker.hidesSensitiveNames()) {
                masked.add(new KeyValuePair(pair.key, masker.maskByName(pair.value)));
            }
        }
        return masked;
    }

    private Object maskValue(Object value) {
        if (value == null || value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return masker.toTree(value);
    }
}
