package co.com.pragma.logging.quarkus;

import co.com.pragma.logging.core.enrich.LogEnrichment;
import co.com.pragma.logging.core.enrich.LogEnrichmentFields;
import co.com.pragma.logging.core.masking.LogMasker;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jboss.logmanager.MDC;

/**
 * Puts the fields of a {@link LogEnrichment} method in the MDC while it runs and restores the previous values
 * afterwards (ENR-05). A Mutiny {@code Uni} result keeps them until it completes. {@link LogEnrichment} is not a CDI
 * annotation: the extension registers it as an interceptor binding.
 */
@LogEnrichment
@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE)
public class LogEnrichmentInterceptor {

    private static final boolean MUTINY = present("io.smallrye.mutiny.Uni");

    private final PragmaLogConfig config;
    private final AtomicReference<LogMasker> masker = new AtomicReference<>();

    @Inject
    LogEnrichmentInterceptor(PragmaLogConfig config) {
        this.config = config;
    }

    @AroundInvoke
    Object enrich(InvocationContext invocation) throws Exception {
        Object target = invocation.getTarget();
        Map<String, String> fields = LogEnrichmentFields.of(target == null ? null : target.getClass(),
                invocation.getMethod(), invocation.getParameters(), masker());
        if (fields.isEmpty()) {
            return invocation.proceed();
        }
        Map<String, String> previous = put(fields);
        try {
            Object result = invocation.proceed();
            return MUTINY ? Reactive.withFields(result, fields) : result;
        } finally {
            restore(previous);
        }
    }

    static Map<String, String> put(Map<String, String> fields) {
        Map<String, String> previous = new LinkedHashMap<>();
        fields.forEach((key, value) -> previous.put(key, MDC.put(key, value)));
        return previous;
    }

    static void restore(Map<String, String> previous) {
        previous.forEach((key, value) -> {
            if (value == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, value);
            }
        });
    }

    private LogMasker masker() {
        LogMasker current = masker.get();
        if (current == null) {
            masker.compareAndSet(null, new LogMasker(PragmaLogRecorder.masking(config.masking())));
            current = masker.get();
        }
        return current;
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, LogEnrichmentInterceptor.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** Loaded only when Mutiny is on the classpath. */
    private static final class Reactive {

        static Object withFields(Object result, Map<String, String> fields) {
            if (result instanceof io.smallrye.mutiny.Uni<?> uni) {
                return uni.onSubscription().invoke(() -> put(fields))
                        .onTermination().invoke(() -> fields.keySet().forEach(MDC::remove));
            }
            return result;
        }
    }
}
