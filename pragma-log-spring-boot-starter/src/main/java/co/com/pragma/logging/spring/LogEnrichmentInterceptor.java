package co.com.pragma.logging.spring;

import co.com.pragma.logging.core.enrich.LogEnrichmentFields;
import co.com.pragma.logging.core.masking.LogMasker;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.ClassUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/**
 * Puts the fields of a {@code @LogEnrichment} method in the MDC while it runs and restores the previous values
 * afterwards (ENR-05). A Reactor {@code Mono} or {@code Flux} result carries them in its context instead, so they
 * reach the logs of operators that run later, on any thread.
 */
final class LogEnrichmentInterceptor implements MethodInterceptor {

    private static final boolean REACTOR = ClassUtils.isPresent("reactor.core.publisher.Mono",
            LogEnrichmentInterceptor.class.getClassLoader());

    private final Supplier<LogMasker> masker;

    LogEnrichmentInterceptor(Supplier<LogMasker> masker) {
        this.masker = masker;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object target = invocation.getThis();
        Class<?> targetClass = target == null ? null : AopUtils.getTargetClass(target);
        Method method = invocation.getMethod();
        Map<String, String> fields = LogEnrichmentFields.of(targetClass, method, invocation.getArguments(),
                masker.get());
        if (fields.isEmpty()) {
            return invocation.proceed();
        }
        Map<String, String> previous = RequestLogContext.putInMdc(fields);
        try {
            Object result = invocation.proceed();
            return REACTOR ? Reactive.withFields(result, fields) : result;
        } finally {
            RequestLogContext.restoreMdc(previous);
        }
    }

    /** Loaded only when Reactor is on the classpath. */
    private static final class Reactive {

        static Object withFields(Object result, Map<String, String> fields) {
            if (result instanceof Mono<?> mono) {
                return mono.contextWrite(context -> context.put(PragmaEnrichmentAccessor.KEY, merge(context, fields)));
            }
            if (result instanceof Flux<?> flux) {
                return flux.contextWrite(context -> context.put(PragmaEnrichmentAccessor.KEY, merge(context, fields)));
            }
            return result;
        }

        /** Fields of an enclosing {@code @LogEnrichment} call, overridden by this one. */
        private static Map<String, String> merge(ContextView context, Map<String, String> inner) {
            Map<String, String> merged = new HashMap<>(context.<Map<String, String>>getOrEmpty(
                    PragmaEnrichmentAccessor.KEY).orElse(Map.of()));
            merged.putAll(inner);
            return Map.copyOf(merged);
        }
    }
}
