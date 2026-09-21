package com.example.multitenant.web;

import com.example.multitenant.tenant.TenantUserRepository;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link TenantResolutionFilter} with the servlet container.
 *
 * <p>A {@link FilterRegistrationBean} is used instead of just exposing the filter as a {@code @Bean} because it lets us pin the filter's position in the chain, which is the part
 * that matters here.
 */
@Configuration
public class TenantFilterConfig {

    @Bean
    public FilterRegistrationBean<TenantResolutionFilter> tenantResolutionFilterRegistration(TenantUserRepository tenantUserRepository) {
        FilterRegistrationBean<TenantResolutionFilter> registration = new FilterRegistrationBean<>(new TenantResolutionFilter(tenantUserRepository));
        // Every path goes through the filter; TenantResolutionFilter itself decides what to skip.
        registration.addUrlPatterns("/*");
        registration.setName("tenantResolutionFilter");
        // Spring Boot registers Spring Security's filter chain at order -100 (SecurityProperties.DEFAULT_FILTER_ORDER). An order of 1 therefore places this filter AFTER
        // security, which is what we need: it reads the Authentication from SecurityContextHolder, which is only populated once the security chain has run.
        // (Our original code carried the comment "keep before security", which is the opposite of what actually happens.)
        registration.setOrder(1);
        return registration;
    }
}
