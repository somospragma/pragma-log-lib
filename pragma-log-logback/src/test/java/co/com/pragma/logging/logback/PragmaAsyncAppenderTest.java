package co.com.pragma.logging.logback;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import co.com.pragma.logging.core.enrich.LogEnricher;
import co.com.pragma.logging.core.enrich.LogEnrichers;
import co.com.pragma.logging.core.masking.MaskedLogField;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Asynchronous logging (PERF): nothing lost, everything masked, context taken on the calling thread. */
class PragmaAsyncAppenderTest {

    private static final ThreadLocal<String> TENANT = new ThreadLocal<>();

    record Card(String holder, @MaskedLogField String number) {
    }

    @Test
    @DisplayName("concurrent events are all written at shutdown, masked, with the caller's MDC and enricher fields")
    void concurrentEvents() throws Exception {
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(MDC.getMDCAdapter()); // the adapter MDC.put writes to
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PragmaStructuredEncoder encoder = new PragmaStructuredEncoder();
        encoder.setContext(context);
        encoder.setIncludeHost(false);
        encoder.start();
        OutputStreamAppender<ILoggingEvent> target = new OutputStreamAppender<>();
        target.setContext(context);
        target.setEncoder(encoder);
        target.setOutputStream(out);
        target.start();
        PragmaAsyncAppender async = new PragmaAsyncAppender();
        async.setContext(context);
        async.setQueueSize(64); // small: callers must wait instead of losing events
        async.addAppender(target);
        async.start();
        Logger logger = context.getLogger("async.Test");
        logger.addAppender(async);
        LogEnricher tenant = event -> event.put("tenant", TENANT.get());
        LogEnrichers.register(tenant);

        int threads = 8;
        int perThread = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> calls = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int id = t;
                calls.add(pool.submit(() -> {
                    start.await();
                    TENANT.set("tenant-" + id);
                    MDC.put("worker", "w-" + id);
                    try {
                        for (int i = 0; i < perThread; i++) {
                            logger.info("Paid {} {}", new Card("Ana", "4111111111111111"), i);
                        }
                    } finally {
                        MDC.clear();
                        TENANT.remove();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> call : calls) {
                call.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
            LogEnrichers.unregister(tenant);
        }
        context.stop(); // flushes the queue

        List<String> lines = out.toString(StandardCharsets.UTF_8).lines().toList();
        assertThat(lines).hasSize(threads * perThread);
        assertThat(String.join("\n", lines)).doesNotContain("4111111111111111");
        for (String line : lines) {
            JsonNode json = JsonMapper.shared().readTree(line);
            String worker = json.get("worker").asString();
            assertThat(json.get("tenant").asString()).isEqualTo("tenant-" + worker.substring(2));
            assertThat(json.get("message").asString()).contains("\"number\":\"****");
        }
    }

    @Test
    @DisplayName("defaults never discard events")
    void defaults() {
        PragmaAsyncAppender async = new PragmaAsyncAppender();

        assertThat(async.getQueueSize()).isEqualTo(PragmaAsyncAppender.DEFAULT_QUEUE_SIZE);
        assertThat(async.getDiscardingThreshold()).isZero();
        assertThat(async.isNeverBlock()).isFalse();
        assertThat(async.getMaxFlushTime()).isEqualTo(PragmaAsyncAppender.DEFAULT_MAX_FLUSH_TIME_MS);
    }
}
