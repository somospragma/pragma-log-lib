package co.com.pragma.logging.core.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class LogAlertsTest {

    static class Recorder implements LogAlertListener {
        final List<Object> alerts = new ArrayList<>();

        @Override
        public void onLargeLog(LargeLogAlert alert) {
            alerts.add(alert);
        }

        @Override
        public void onMaskingError(MaskingErrorAlert alert) {
            alerts.add(alert);
        }

        @Override
        public void onMissingFields(MissingFieldsAlert alert) {
            alerts.add(alert);
        }
    }

    @Test
    void missingRequiredFieldsAreReportedOnlyWhenThereAreSome() {
        Recorder recorder = new Recorder();
        LogAlerts alerts = new LogAlerts(0, false, List.of(recorder));

        alerts.missingFields("a.B", "INFO", List.of());
        alerts.missingFields("a.B", "WARN", List.of("requestId"));

        assertThat(recorder.alerts).containsExactly(new MissingFieldsAlert("a.B", "WARN", List.of("requestId")));
    }

    @Test
    void alertsOnlyAboveTheMaximumMeasuringUtf8Bytes() {
        Recorder recorder = new Recorder();
        LogAlerts alerts = new LogAlerts(10, false, List.of(recorder));

        alerts.checkSize("a.B", "INFO", "0123456789");
        alerts.checkSize("a.B", "INFO", "ñññññ"); // 10 bytes
        alerts.checkSize("a.B", "WARN", "ññññññ"); // 12 bytes

        assertThat(recorder.alerts).containsExactly(new LargeLogAlert("a.B", "WARN", 12, 10));
    }

    @Test
    void codeRegisteredListenersAndFailingListeners() {
        Recorder registered = new Recorder();
        LogAlertListener failing = new LogAlertListener() {
            @Override
            public void onMaskingError(MaskingErrorAlert alert) {
                throw new IllegalStateException("listener bug");
            }
        };
        LogAlerts.register(registered);
        try {
            IllegalStateException error = new IllegalStateException("boom");
            LogAlerts.none().maskingError("a.B", error);
            new LogAlerts(0, false, List.of(failing)).maskingError("a.B", error);

            assertThat(registered.alerts).containsExactly(new MaskingErrorAlert("a.B", error),
                    new MaskingErrorAlert("a.B", error));
        } finally {
            LogAlerts.unregister(registered);
        }
    }

    @Test
    void listenersThatLogDoNotTriggerNewAlerts() {
        List<LogAlerts> holder = new ArrayList<>();
        AtomicLong calls = new AtomicLong();
        LogAlertListener reentrant = new LogAlertListener() {
            @Override
            public void onLargeLog(LargeLogAlert alert) {
                calls.incrementAndGet();
                holder.get(0).checkSize("a.B", "INFO", 1_000); // as if the listener logged something big
            }
        };
        holder.add(new LogAlerts(10, false, List.of(reentrant)));

        holder.get(0).checkSize("a.B", "INFO", 1_000);

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void consoleListenerWritesAtMostOncePerMinutePerLogger() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AtomicLong now = new AtomicLong();
        ConsoleAlertListener console = new ConsoleAlertListener(new PrintStream(out, true, StandardCharsets.UTF_8),
                now::get, new java.util.HashMap<>());

        console.onLargeLog(new LargeLogAlert("a.B", "INFO", 99, 10));
        console.onLargeLog(new LargeLogAlert("a.B", "INFO", 99, 10));
        console.onLargeLog(new LargeLogAlert("c.D", "INFO", 99, 10));
        now.addAndGet(TimeUnit.MINUTES.toNanos(1));
        console.onLargeLog(new LargeLogAlert("a.B", "INFO", 99, 10));

        assertThat(out.toString(StandardCharsets.UTF_8).lines()).hasSize(3)
                .allMatch(line -> line.startsWith("pragma-log WARN: a INFO log of 99 bytes"));
    }

    @Test
    void consoleListenerReportsMissingRequiredFields() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleAlertListener console = new ConsoleAlertListener(new PrintStream(out, true, StandardCharsets.UTF_8),
                () -> 0L, new java.util.HashMap<>());

        console.onMissingFields(new MissingFieldsAlert("a.B", "WARN", List.of("requestId")));
        console.onMissingFields(new MissingFieldsAlert("a.B", "WARN", List.of("requestId")));

        assertThat(out.toString(StandardCharsets.UTF_8).lines()).singleElement().asString()
                .contains("WARN log from a.B does not have the required fields [requestId]");
    }

    @Test
    void loadsListenersByClassName() {
        assertThat(LogAlerts.load(Recorder.class.getName(), getClass().getClassLoader())).isInstanceOf(Recorder.class);
        assertThatThrownBy(() -> LogAlerts.load("java.lang.String", getClass().getClassLoader()))
                .hasMessageContaining("cannot load alert listener java.lang.String");
        assertThatThrownBy(() -> new LogAlerts(-1, false, List.of())).hasMessageContaining(">= 0");
    }
}
