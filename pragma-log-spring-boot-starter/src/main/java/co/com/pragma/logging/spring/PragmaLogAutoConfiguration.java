package co.com.pragma.logging.spring;

import co.com.pragma.logging.core.alert.LogAlertListener;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Hooks;

/**
 * Auto-configuration of pragma-log. The JSON log format itself is switched on earlier, by
 * {@link PragmaLogEnvironmentPostProcessor}, because logging starts before the application context.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = PragmaLogProperties.PREFIX, name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(PragmaLogProperties.class)
public class PragmaLogAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    PragmaLogAlertRegistrar pragmaLogAlertRegistrar(ObjectProvider<LogAlertListener> listeners) {
        return new PragmaLogAlertRegistrar(listeners.orderedStream().toList());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(OncePerRequestFilter.class)
    @ConditionalOnProperty(prefix = PragmaLogProperties.PREFIX + ".http", name = "enabled", matchIfMissing = true)
    static class ServletContextConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @Order(Ordered.HIGHEST_PRECEDENCE + 10)
        PragmaLogContextFilter pragmaLogContextFilter(PragmaLogProperties properties) {
            return new PragmaLogContextFilter(properties.getHttp());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass({WebFilter.class, Hooks.class, ThreadLocalAccessor.class})
    @ConditionalOnProperty(prefix = PragmaLogProperties.PREFIX + ".http", name = "enabled", matchIfMissing = true)
    static class ReactiveContextConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @Order(Ordered.HIGHEST_PRECEDENCE + 10)
        PragmaLogContextWebFilter pragmaLogContextWebFilter(PragmaLogProperties properties) {
            // Restores the Reactor Context into ThreadLocals (MDC) on every operator, like
            // spring.reactor.context-propagation=auto; required for the request context to reach the logs.
            Hooks.enableAutomaticContextPropagation();
            return new PragmaLogContextWebFilter(properties.getHttp());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({Hooks.class, ContextRegistry.class})
    static class ReactorContextKeysConfiguration {

        @Bean
        @ConditionalOnMissingBean
        PragmaContextKeysRegistrar pragmaContextKeysRegistrar(PragmaLogProperties properties) {
            return new PragmaContextKeysRegistrar(properties.getContextKeys());
        }
    }
}
