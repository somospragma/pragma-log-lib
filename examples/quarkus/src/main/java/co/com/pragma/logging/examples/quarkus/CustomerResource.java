package co.com.pragma.logging.examples.quarkus;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.time.Duration;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/customers")
public class CustomerResource {

    private static final Logger log = LoggerFactory.getLogger(CustomerResource.class);
    private static final org.jboss.logging.Logger jbossLog = org.jboss.logging.Logger.getLogger(CustomerResource.class);

    @GET
    @Path("/{id}")
    public Customer find(@PathParam("id") String id) {
        Customer customer = new Customer(id, "Ana", "4111111111111111", "s3cr3t");
        log.info("Customer found: {}", customer);
        log.atInfo().addKeyValue("customerId", id).addKeyValue("customer", customer).log("Lookup finished");
        jbossLog.infof("Audit %s (attempt %d)", customer, 1);
        // A card inside plain text: no annotation can see it, pragma.log.masking.patterns=CARD does.
        log.info("Gateway note: {}", "charged card " + customer.cardNumber());
        return customer;
    }

    /** Non-blocking: the second log runs on a worker thread, not the event loop that received the request. */
    @GET
    @Path("/{id}/async")
    public Uni<String> findAsync(@PathParam("id") String id) {
        log.info("Async lookup {}", id);
        return Uni.createFrom().item(id)
                .onItem().delayIt().by(Duration.ofMillis(20))
                .emitOn(Infrastructure.getDefaultWorkerPool())
                .invoke(value -> log.info("Async loaded {}", value));
    }

    /** Logs more than pragma.log.alerts.max-log-size: the log is written whole and the listeners are told. */
    @GET
    @Path("/{id}/history")
    public int history(@PathParam("id") String id) {
        var movements = Collections.nCopies(5_000, "purchase 10.00 USD");
        log.atInfo().addKeyValue("customerId", id).addKeyValue("movements", movements).log("History loaded");
        return movements.size();
    }

    @GET
    @Path("/fail")
    public String fail() {
        throw new IllegalStateException("Gateway timeout",
                new java.net.SocketTimeoutException("Read timed out"));
    }
}
