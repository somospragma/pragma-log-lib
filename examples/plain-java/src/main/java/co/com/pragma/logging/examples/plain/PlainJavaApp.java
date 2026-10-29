package co.com.pragma.logging.examples.plain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** Minimal plain-Java app: standard SLF4J calls, JSON output configured only in logback.xml. */
public final class PlainJavaApp {

    private static final Logger log = LoggerFactory.getLogger(PlainJavaApp.class);

    private PlainJavaApp() {
    }

    public static void main(String[] args) {
        MDC.put("requestId", "req-123");
        try {
            log.info("Application started");
            log.atInfo().addKeyValue("orderId", "ORD-9").log("Order placed");
            log.error("Payment failed", new IllegalStateException("Gateway timeout"));
        } finally {
            MDC.remove("requestId");
        }
    }
}
