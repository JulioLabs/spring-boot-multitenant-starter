package com.example.multitenant.tenant;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Configuration class that enables aspect-oriented programming for tenant awareness.
 *
 * <p>{@link TenantAwareAspect} only takes effect if Spring wraps the repository beans in AOP proxies. Spring Boot's AOP auto-configuration normally does this already; declaring it
 * explicitly keeps that requirement visible in the tenancy code itself.
 */
@Configuration
@EnableAspectJAutoProxy
public class TenantAwareConfig {
  // The configuration is handled by annotations
}
