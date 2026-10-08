package com.hypex.electriplan.tenancy;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class TenancyConfiguration implements WebMvcConfigurer {

    private final CompanyContextInterceptor interceptor;

    TenancyConfiguration(CompanyContextInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    /**
     * Replaces Spring Boot's JPA transaction manager with one that applies the
     * request's company to every transaction. The settings are Postgres
     * functions, so tests on another database turn them off.
     */
    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory factory,
            @Value("${electriplan.tenancy.session-settings:true}") boolean sessionSettings) {
        return new TenantTransactionManager(factory, sessionSettings);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**");
    }
}
