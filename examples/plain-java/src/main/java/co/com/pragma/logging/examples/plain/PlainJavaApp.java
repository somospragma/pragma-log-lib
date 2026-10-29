package co.com.pragma.logging.examples.plain;

import co.com.pragma.logging.core.masking.MaskType;
import co.com.pragma.logging.core.masking.MaskedLogField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** Minimal plain-Java app: standard SLF4J calls, JSON output configured only in logback.xml. */
public final class PlainJavaApp {

    private static final Logger log = LoggerFactory.getLogger(PlainJavaApp.class);

    record Customer(
            String name,
            @MaskedLogField(type = MaskType.MIDDLE, length = 4) String cardNumber,
            String password) {
    }

    private PlainJavaApp() {
    }

    public static void main(String[] args) {
        Customer customer = new Customer("Ana", "4111111111111111", "s3cr3t");
        MDC.put("requestId", "req-123");
        try {
            log.info("Application started");
            log.info("Customer registered: {}", customer);
            log.atInfo().addKeyValue("orderId", "ORD-9").addKeyValue("customer", customer).log("Order placed");
            log.error("Payment failed", new IllegalStateException("Gateway timeout"));
        } finally {
            MDC.remove("requestId");
        }
    }
}
