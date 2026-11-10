package co.com.pragma.logging.core.enrich;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.pragma.logging.core.masking.LogMasker;
import co.com.pragma.logging.core.masking.MaskType;
import co.com.pragma.logging.core.masking.MaskedLogField;
import co.com.pragma.logging.core.masking.MaskingSettings;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LogEnrichersTest {

    /** Loaded by class name. */
    public static class RegionEnricher implements LogEnricher {
        @Override
        public void enrich(EnrichedEvent event) {
            event.put("region", "co-bog");
            event.put("source", event.logger() + "/" + event.level());
            event.put("ignored", null);
        }
    }

    record Card(String holder, @MaskedLogField(type = MaskType.MIDDLE, length = 4) String number) {
    }

    @LogEnrichment({"domain=payments", "malformed"})
    static class PaymentService {
        @LogEnrichment("operation=charge")
        public void charge(@LogField("customerId") String customerId, @LogField("card") Card card,
                @LogField("date") LocalDate date, @LogField(" ") String blank, String other, @LogField("none") String none) {
        }

        public void plain() {
        }
    }

    @Test
    @DisplayName("configured and registered enrichers add fields; failures and their own logs are ignored (ENR-07)")
    void enrichers() {
        LogEnricher failing = event -> {
            throw new IllegalStateException("boom");
        };
        LogEnricher registered = event -> event.put("tenant", "t-1");
        LogEnrichers enrichers = new LogEnrichers(List.of(LogEnrichers.load(RegionEnricher.class.getName(),
                getClass().getClassLoader()), failing));
        LogEnrichers.register(registered);
        try {
            assertThat(enrichers.fields("a.B", "INFO")).containsExactly(Map.entry("region", "co-bog"),
                    Map.entry("source", "a.B/INFO"), Map.entry("tenant", "t-1"));
        } finally {
            LogEnrichers.unregister(registered);
        }
        assertThat(LogEnrichers.none().fields("a.B", "INFO")).isEmpty();
        assertThatThrownBy(() -> LogEnrichers.load("java.lang.String", getClass().getClassLoader()))
                .hasMessageContaining("pragma.log.enrichers");
    }

    @Test
    @DisplayName("an enricher that logs does not enrich its own logs")
    void reentrance() {
        AtomicReference<LogEnrichers> holder = new AtomicReference<>();
        AtomicReference<Map<String, Object>> inner = new AtomicReference<>();
        holder.set(new LogEnrichers(List.of(event -> {
            inner.set(holder.get().fields("inner", "DEBUG"));
            event.put("outer", true);
        })));

        assertThat(holder.get().fields("a.B", "INFO")).containsEntry("outer", true);
        assertThat(inner.get()).isEmpty();
    }

    @Test
    @DisplayName("@LogEnrichment class and method fields, then @LogField parameters, objects masked (ENR-05)")
    void enrichmentFields() throws Exception {
        Method charge = PaymentService.class.getMethod("charge", String.class, Card.class, LocalDate.class,
                String.class, String.class, String.class);
        LogMasker masker = new LogMasker(MaskingSettings.defaults());

        Map<String, String> fields = LogEnrichmentFields.of(PaymentService.class, charge,
                new Object[] {"c-1", new Card("Ana", "4111111111111111"), LocalDate.of(2026, 11, 10), "x", "y", null},
                masker);

        assertThat(fields).containsExactly(Map.entry("domain", "payments"), Map.entry("operation", "charge"),
                Map.entry("customerId", "c-1"), Map.entry("card", "{\"holder\":\"Ana\",\"number\":\"4111********1111\"}"),
                Map.entry("date", "2026-11-10"));
        assertThat(LogEnrichmentFields.of(null, PaymentService.class.getMethod("plain"), new Object[0], masker))
                .containsExactly(Map.entry("domain", "payments"));
    }
}
