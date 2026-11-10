package co.com.pragma.logging.examples.quarkus;

import java.math.BigDecimal;

/** Request body: cardNumber is masked by name when the body is logged. */
public record Payment(BigDecimal amount, String cardNumber) {
}
