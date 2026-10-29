package co.com.pragma.logging.examples.mvc;

import co.com.pragma.logging.core.masking.MaskType;
import co.com.pragma.logging.core.masking.MaskedLogField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Spring Boot MVC app: adding the starter is enough, no logback-spring.xml. */
@SpringBootApplication
public class SpringMvcApp {

    public static void main(String[] args) {
        SpringApplication.run(SpringMvcApp.class, args);
    }

    record Customer(
            String id,
            String name,
            @MaskedLogField(type = MaskType.MIDDLE, length = 4) String cardNumber,
            String password) {
    }

    @RestController
    static class CustomerController {

        private static final Logger log = LoggerFactory.getLogger(CustomerController.class);

        @GetMapping("/customers/{id}")
        Customer find(@PathVariable String id) {
            Customer customer = new Customer(id, "Ana", "4111111111111111", "s3cr3t");
            log.info("Customer found: {}", customer);
            log.atInfo().addKeyValue("customerId", id).log("Lookup finished");
            return customer;
        }
    }
}
