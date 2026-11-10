package co.com.pragma.logging.core.alert;

import java.util.List;

/**
 * A log event without some of the required fields; they were written with the placeholder (FLD-07).
 *
 * @param logger logger name of the event
 * @param level  level of the event
 * @param fields required fields the event did not have
 */
public record MissingFieldsAlert(String logger, String level, List<String> fields) {

    public MissingFieldsAlert {
        fields = List.copyOf(fields);
    }
}
