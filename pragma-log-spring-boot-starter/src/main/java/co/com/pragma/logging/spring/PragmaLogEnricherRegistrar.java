package co.com.pragma.logging.spring;

import co.com.pragma.logging.core.enrich.LogEnricher;
import co.com.pragma.logging.core.enrich.LogEnrichers;
import java.util.List;
import org.springframework.beans.factory.DisposableBean;

/**
 * Adds the application's {@link LogEnricher} beans to every pragma-log encoder (ENR-07). The encoders exist before
 * the application context, so beans are registered globally and removed when the context closes.
 */
final class PragmaLogEnricherRegistrar implements DisposableBean {

    private final List<LogEnricher> enrichers;

    PragmaLogEnricherRegistrar(List<LogEnricher> enrichers) {
        this.enrichers = List.copyOf(enrichers);
        this.enrichers.forEach(LogEnrichers::register);
    }

    @Override
    public void destroy() {
        enrichers.forEach(LogEnrichers::unregister);
    }
}
