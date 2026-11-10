package co.com.pragma.logging.core.enrich;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Adds a parameter of a {@link LogEnrichment} method to the logs written while it runs (ENR-05). Simple values are
 * written as they are; objects as their masked JSON. Fields with sensitive names are masked like any MDC entry.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface LogField {

    /** Field name. */
    String value();
}
