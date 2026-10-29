package co.com.pragma.logging.benchmarks;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import co.com.pragma.logging.core.masking.MaskType;
import co.com.pragma.logging.core.masking.MaskedLogField;
import co.com.pragma.logging.logback.PragmaJsonEncoder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.logstash.logback.encoder.LogstashEncoder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.slf4j.event.KeyValuePair;

/**
 * Cost of encoding one event with plain logstash-logback-encoder (baseline) versus
 * {@link PragmaJsonEncoder}, with and without objects to mask.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class EncoderBenchmark {

    public record Customer(
            String name,
            @MaskedLogField(type = MaskType.MIDDLE, length = 4) String cardNumber,
            String password,
            List<String> tags) {
    }

    private LogstashEncoder baseline;
    private PragmaJsonEncoder pragma;
    private LoggingEvent simpleEvent;
    private LoggingEvent objectArgumentEvent;
    private LoggingEvent keyValueObjectEvent;

    @Setup
    public void setUp() {
        LoggerContext context = new LoggerContext();
        Logger logger = context.getLogger("co.com.pragma.Benchmark");

        baseline = new LogstashEncoder();
        baseline.setContext(context);
        baseline.start();

        pragma = new PragmaJsonEncoder();
        pragma.setContext(context);
        pragma.setAppName("benchmark");
        pragma.start();

        Customer customer = new Customer("Ana", "4111111111111111", "s3cr3t", List.of("vip", "web"));
        simpleEvent = event(logger, "Order placed", null);
        objectArgumentEvent = event(logger, "Customer registered: {}", new Object[] {customer});
        keyValueObjectEvent = event(logger, "Order placed", null);
        keyValueObjectEvent.setKeyValuePairs(List.of(
                new KeyValuePair("orderId", "ORD-9"), new KeyValuePair("customer", customer)));
    }

    @TearDown
    public void tearDown() {
        baseline.stop();
        pragma.stop();
    }

    @Benchmark
    public byte[] baselineSimple() {
        return baseline.encode(simpleEvent);
    }

    @Benchmark
    public byte[] pragmaSimple() {
        return pragma.encode(simpleEvent);
    }

    @Benchmark
    public byte[] baselineObjectArgument() {
        return baseline.encode(objectArgumentEvent);
    }

    @Benchmark
    public byte[] pragmaMaskedObjectArgument() {
        return pragma.encode(objectArgumentEvent);
    }

    @Benchmark
    public byte[] pragmaMaskedKeyValueObject() {
        return pragma.encode(keyValueObjectEvent);
    }

    private static LoggingEvent event(Logger logger, String message, Object[] args) {
        LoggingEvent event = new LoggingEvent(Logger.FQCN, logger, Level.INFO, message, null, args);
        event.setMDCPropertyMap(Map.of("requestId", "req-123"));
        return event;
    }
}
