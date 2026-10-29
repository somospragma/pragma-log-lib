package co.com.pragma.logging.spring;

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

/**
 * Auto-configuration of pragma-log. The JSON log format itself is switched on earlier, by
 * {@link PragmaLogEnvironmentPostProcessor}, because logging starts before the application context.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = PragmaLogProperties.PREFIX, name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(PragmaLogProperties.class)
public class PragmaLogAutoConfiguration {

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
}
