package co.com.pragma.logging.spring;

import co.com.pragma.logging.core.enrich.LogEnrichment;
import co.com.pragma.logging.core.masking.LogMasker;
import co.com.pragma.logging.core.masking.MaskType;
import co.com.pragma.logging.core.masking.MaskingSettings;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.aop.Advisor;
import org.springframework.aop.config.AopConfigUtils;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.annotation.Role;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.function.SingletonSupplier;

/**
 * {@code @LogEnrichment} on Spring beans (ENR-05): an infrastructure advisor, applied by Spring's auto-proxy
 * creator. Only spring-aop is needed, not AspectJ.
 */
@Configuration(proxyBeanMethods = false)
@Import(LogEnrichmentConfiguration.AutoProxy.class)
class LogEnrichmentConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor pragmaLogEnrichmentAdvisor(ObjectProvider<PragmaLogProperties> properties) {
        ComposablePointcut pointcut = new ComposablePointcut(new AnnotationMatchingPointcut(LogEnrichment.class, true))
                .union(new AnnotationMatchingPointcut(null, LogEnrichment.class, true));
        // Resolved on first use: advisors are created before the other beans.
        SingletonSupplier<LogMasker> masker = SingletonSupplier.of(() -> new LogMasker(masking(
                properties.getIfAvailable(PragmaLogProperties::new).getMasking())));
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut, new LogEnrichmentInterceptor(masker));
        advisor.setOrder(0);
        return advisor;
    }

    /** Object parameters are written as JSON masked by annotations and by the configured field names. */
    private static MaskingSettings masking(PragmaLogProperties.Masking masking) {
        try {
            return new MaskingSettings(masking.isEnabled(), masking.getCharacter().charAt(0),
                    MaskType.valueOf(masking.getDefaultType().trim().toUpperCase(Locale.ROOT)),
                    masking.getDefaultLength(),
                    masking.getFields() == null ? MaskingSettings.DEFAULT_FIELDS : Set.copyOf(masking.getFields()),
                    masking.getMaxDepth(), Map.of());
        } catch (RuntimeException e) {
            return MaskingSettings.defaults();
        }
    }

    /** Makes sure an auto-proxy creator exists, also without Spring Boot's AOP auto-configuration. */
    static class AutoProxy implements ImportBeanDefinitionRegistrar {

        @Override
        public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
            AopConfigUtils.registerAutoProxyCreatorIfNecessary(registry);
            AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
        }
    }
}
