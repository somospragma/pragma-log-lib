package co.com.pragma.logging.examples.quarkus;

import co.com.pragma.logging.core.masking.MaskType;
import co.com.pragma.logging.core.masking.MaskedLogField;

/** Response body: the token is masked by annotation when the body is logged. */
public record Receipt(String status, @MaskedLogField(type = MaskType.SUFFIX, length = 4) String token) {
}
