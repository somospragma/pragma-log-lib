package co.com.pragma.logging.core.enrich;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Adds fields to every log written while an annotated method runs (ENR-05), in Spring and Quarkus beans. Fixed
 * fields come from {@link #value()}; method parameters annotated with {@link LogField} add their values. On a class,
 * it applies to all its methods; method fields are added after class fields.
 *
 * <pre>{@code
 * @LogEnrichment("domain=payments")
 * class PaymentService {
 *     @LogEnrichment("operation=refund")
 *     Mono<Refund> refund(@LogField("paymentId") String paymentId) {
 *         log.info("Refund started");   // carries domain, operation and paymentId
 *     }
 * }
 * }</pre>
 *
 * <p>The fields are put in the MDC, so they also reach logs of other classes called from the method; previous MDC
 * values are restored when it returns. Reactive results (Reactor {@code Mono}/{@code Flux}, Mutiny {@code Uni}) carry
 * them in their context until they complete.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface LogEnrichment {

    /** Fixed fields as {@code name=value}. */
    String[] value() default {};
}
