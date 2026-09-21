package com.example.multitenant.web;

import com.example.multitenant.tenant.TenantUserRepository;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TenantFilterConfig {

    @Bean
    public FilterRegistrationBean<TenantResolutionFilter> tenantResolutionFilterRegistration(TenantUserRepository tenantUserRepository) {
        FilterRegistrationBean<TenantResolutionFilter> registration = new FilterRegistrationBean<>(new TenantResolutionFilter(tenantUserRepository));
        registration.addUrlPatterns("/*");
        registration.setName("tenantResolutionFilter");
        // Spring Boot registers Spring Security's filter chain at order -100 (SecurityProperties.DEFAULT_FILTER_ORDER). An order of 1 therefore places this filter AFTER
        // security, which is what we need: it reads the Authentication from SecurityContextHolder, which is only populated once the security chain has run.
        // (Our original code carried the comment "keep before security", which is the opposite of what actually happens.)
        registration.setOrder(1);
        return registration;
    }
}
