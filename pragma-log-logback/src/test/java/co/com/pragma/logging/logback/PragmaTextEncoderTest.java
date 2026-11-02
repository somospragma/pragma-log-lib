package co.com.pragma.logging.logback;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import co.com.pragma.logging.core.masking.MaskedLogField;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;

class PragmaTextEncoderTest {

    private final LoggerContext context = new LoggerContext();
    private final Logger logger = context.getLogger("co.com.pragma.Sample");
    private PragmaTextEncoder encoder;

    static class Card {
        String holder = "Ana";
        @MaskedLogField
        String number = "4111111111111111";
    }

    @AfterEach
    void tearDown() {
        if (encoder != null) {
            encoder.stop();
        }
    }

    private void startEncoder(Consumer<PragmaTextEncoder> config) {
        encoder = new PragmaTextEncoder();
        encoder.setContext(context);
        encoder.setColor("NEVER");
        encoder.setTimeZone("UTC");
        config.accept(encoder);
        encoder.start();
    }

    @Test
    void writesReadableLineWithMaskedArgumentsMdcAndKeyValues() {
        startEncoder(e -> { });
        LoggingEvent event = eventWithMdc(Map.of("requestId", "req-1"), Level.INFO, "Paid with {}", new Card());
        event.setKeyValuePairs(List.of(new KeyValuePair("card", new Card()), new KeyValuePair("password", "s3cr3t")));

        String line = encode(event);

        assertThat(line).isEqualTo("2026-11-02 15:04:05.123  INFO [main] co.com.pragma.Sample : Paid with"
                + " {\"holder\":\"Ana\",\"number\":\"****************\"} requestId=req-1"
                + " card={\"holder\":\"Ana\",\"number\":\"****************\"} password=******" + System.lineSeparator());
    }

    @Test
    void appliesTimestampPatternZoneColorsAndHiddenFields() {
        startEncoder(e -> {
            e.setColor("ALWAYS");
            e.addLevelColor("INFO=#00aa88");
            e.setTimestampPattern("HH:mm:ss");
            e.setTimeZone("America/Bogota");
            e.setHideFields("requestId");
        });
        LoggingEvent event = eventWithMdc(Map.of("requestId", "req-1"), Level.INFO, "Hi");

        String line = encode(event);

        assertThat(line).isEqualTo("\u001b[2m10:04:05\u001b[0m \u001b[38;2;0;170;136m INFO\u001b[0m [main] "
                + "\u001b[36mco.com.pragma.Sample\u001b[0m : Hi" + System.lineSeparator());
    }

    @Test
    void writesBoundedStacktraceAfterTheLine() {
        startEncoder(e -> e.setStacktraceMaxDepth(2));
        LoggingEvent event = new LoggingEvent(Logger.FQCN, logger, Level.ERROR, "Payment failed",
                new IllegalStateException("gateway down"), null);
        event.setInstant(Instant.parse("2026-11-02T15:04:05.123Z"));
        event.setThreadName("main");
        event.setMDCPropertyMap(Map.of());

        String[] lines = encode(event).split("\\R");

        assertThat(lines[0]).isEqualTo("2026-11-02 15:04:05.123 ERROR [main] co.com.pragma.Sample : Payment failed");
        assertThat(lines[1]).isEqualTo("java.lang.IllegalStateException: gateway down");
        assertThat(lines.length).isLessThan(8);
    }

    @Test
    void masksPatternsAndTruncatesInText() {
        startEncoder(e -> {
            e.addMaskPattern("CARD");
            e.setMaxFieldLength(30);
        });
        LoggingEvent event = new LoggingEvent(Logger.FQCN, logger, Level.ERROR, "Card 4111111111111111 was rejected by the bank",
                new IllegalStateException("card 4111111111111111"), null);
        event.setInstant(Instant.parse("2026-11-02T15:04:05.123Z"));
        event.setThreadName("main");
        event.setMDCPropertyMap(Map.of());

        String text = encode(event);

        assertThat(text).doesNotContain("4111111111111111")
                .contains(" : Card 4111********1111 was reje...[truncated 16 chars]")
                .contains("java.lang.IllegalStateException: card 4111********1111");
    }

    @Test
    void reportsInvalidFormatConfiguration() {
        startEncoder(e -> {
            e.setTimeZone("Mars/Olympus");
            e.addLevelColor("INFO");
        });

        assertThat(encoder.getConfigurationErrors())
                .anyMatch(error -> error.contains("levelColor must look like name=value"))
                .anyMatch(error -> error.contains("pragma.log.timestamp.zone is not a valid zone id"));
        assertThat(encode(event(Level.INFO, "still logs"))).contains(" INFO [main] co.com.pragma.Sample : still logs");
    }

    private LoggingEvent event(Level level, String message, Object... args) {
        return eventWithMdc(Map.of(), level, message, args);
    }

    private LoggingEvent eventWithMdc(Map<String, String> mdc, Level level, String message, Object... args) {
        LoggingEvent event = new LoggingEvent(Logger.FQCN, logger, level, message, null,
                args.length == 0 ? null : args);
        event.setInstant(Instant.parse("2026-11-02T15:04:05.123Z"));
        event.setThreadName("main");
        event.setMDCPropertyMap(mdc);
        return event;
    }

    private String encode(LoggingEvent event) {
        return new String(encoder.encode(event), StandardCharsets.UTF_8);
    }
}
