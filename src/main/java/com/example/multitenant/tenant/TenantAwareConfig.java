package com.example.multitenant.tenant;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Configuration class that enables aspect-oriented programming for tenant awareness.
 */
@Configuration
@EnableAspectJAutoProxy
public class TenantAwareConfig {
  // The configuration is handled by annotations
}
