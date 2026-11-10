package co.com.pragma.logging.spring;

import java.util.Map;
import java.util.WeakHashMap;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

/**
 * Applies changed {@code pragma.log.*} properties without restarting (CFG-06): masking, fields, layout, alerts,
 * stack trace, timestamp and the HTTP context. Changing {@code pragma.log.format} between plain and structured
 * still needs a restart, because Spring Boot picks the formatter class when logging starts.
 *
 * <p>Reloads by itself on Spring Cloud's {@code EnvironmentChangeEvent} (refresh endpoint, Kubernetes ConfigMaps,
 * Config Server). Call {@link #reload()} after changing the {@code Environment} in any other way. If the new
 * configuration is invalid, the previous one stays and the problem is written to standard error.
 */
public class PragmaLogReloader implements ApplicationListener<ApplicationEvent> {

    private static final String ENVIRONMENT_CHANGE_EVENT =
            "org.springframework.cloud.context.environment.EnvironmentChangeEvent";

    /** Formatters created by Spring Boot's logging system; weak, so a stopped logging system is not kept. */
    private static final Map<Reloadable, Boolean> FORMATTERS = new WeakHashMap<>();

    /** A formatter that can rebuild its encoder from the current configuration. */
    interface Reloadable {

        /**
         * Rebuilds the encoder from the current configuration.
         *
         * @throws RuntimeException when the current configuration is invalid; the old encoder stays
         */
        void reload();
    }

    static void register(Reloadable formatter) {
        synchronized (FORMATTERS) {
            FORMATTERS.put(formatter, Boolean.TRUE);
        }
    }

    private final Environment environment;
    private final PragmaLogProperties properties;

    PragmaLogReloader(Environment environment, PragmaLogProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    /**
     * Rebinds the {@code pragma.log.*} properties used by the HTTP filters and rebuilds every pragma-log formatter
     * from the current {@code Environment}.
     */
    public void reload() {
        try {
            Binder.get(environment).bind(PragmaLogProperties.PREFIX, Bindable.ofInstance(properties));
        } catch (RuntimeException e) {
            System.err.println("pragma-log WARN: the new pragma.log configuration was not applied: " + e.getMessage());
            return;
        }
        Reloadable[] formatters;
        synchronized (FORMATTERS) {
            formatters = FORMATTERS.keySet().toArray(Reloadable[]::new);
        }
        for (Reloadable formatter : formatters) {
            try {
                formatter.reload();
            } catch (RuntimeException e) {
                System.err.println("pragma-log WARN: the new pragma.log configuration was not applied, the previous"
                        + " one stays: " + e.getMessage());
            }
        }
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (ENVIRONMENT_CHANGE_EVENT.equals(event.getClass().getName())) {
            reload();
        }
    }
}
