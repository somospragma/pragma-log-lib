package co.com.pragma.logging.examples.webflux;

import co.com.pragma.logging.core.masking.MaskType;
import co.com.pragma.logging.core.masking.MaskedLogField;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/** Spring Boot WebFlux app: the request context follows the request across Reactor threads. */
@SpringBootApplication
public class SpringWebFluxApp {

    public static void main(String[] args) {
        SpringApplication.run(SpringWebFluxApp.class, args);
    }

    record Order(String id, @MaskedLogField(type = MaskType.MIDDLE, length = 4) String cardNumber) {
    }

    @RestController
    static class OrderController {

        private static final Logger log = LoggerFactory.getLogger(OrderController.class);

        @GetMapping("/orders/{id}")
        Mono<Order> find(@PathVariable String id) {
            log.info("Looking up order {}", id);
            return Mono.just(new Order(id, "4111111111111111"))
                    .delayElement(Duration.ofMillis(50)) // simulates a non-blocking call on another thread
                    .doOnNext(order -> log.info("Order loaded: {}", order))
                    // Application values: listed in pragma.log.context-keys, they appear in every log above
                    .contextWrite(Context.of("customerId", "C-" + id));
        }
    }
}
