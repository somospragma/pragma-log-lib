package co.com.pragma.logging.examples.quarkus;

import co.com.pragma.logging.core.enrich.LogEnricher;
import jakarta.enterprise.context.ApplicationScoped;

/** A CDI bean: its field is added to every log. */
@ApplicationScoped
public class RegionEnricher implements LogEnricher {

    @Override
    public void enrich(EnrichedEvent event) {
        event.put("region", "co-bog");
    }
}
