package co.com.pragma.logging.examples.quarkus;

import co.com.pragma.logging.core.enrich.LogEnrichment;
import co.com.pragma.logging.core.enrich.LogField;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Every log written while charge() runs carries domain, operation and customerId. */
@ApplicationScoped
@LogEnrichment("domain=payments")
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    @LogEnrichment("operation=charge")
    public Receipt charge(@LogField("customerId") String customerId, BigDecimal amount) {
        log.info("Charging {}", amount);
        return new Receipt("approved", "rcpt-0001-2345");
    }
}
