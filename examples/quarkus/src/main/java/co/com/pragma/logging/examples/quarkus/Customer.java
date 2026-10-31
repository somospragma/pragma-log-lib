package co.com.pragma.logging.examples.quarkus;

import co.com.pragma.logging.core.masking.MaskType;
import co.com.pragma.logging.core.masking.MaskedLogField;

/** Logged as JSON: cardNumber is masked by annotation, password by name. */
public record Customer(String id, String name, @MaskedLogField(type = MaskType.MIDDLE, length = 4) String cardNumber,
        String password) {
}
