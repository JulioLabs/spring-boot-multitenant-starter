package com.example.multitenant.tenant;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Thin injectable wrapper around {@link TenantContextHolder}. It lets Spring beans (like {@link TenantAwareAspect}) receive the tenant lookup by injection instead of reaching
 * into static state, which also means tests can mock it.
 */
@Component
public class TenantAwareUtil {


  /**
   * The current tenant, for callers that cannot continue without one.
   *
   * @throws EntityNotFoundException when no tenant is set, so a missing context fails loudly instead of producing an entity with no owner
   */
  public Tenant getCurrentTenant() {
    Tenant tenant = TenantContextHolder.getTenant();
    if (tenant == null) {
      throw new EntityNotFoundException("No tenant set in current context");
    }

    return tenant;
  }

  /**
   * Non-throwing counterpart of {@link #getCurrentTenant()}, for callers that treat "no tenant" as a valid case (public requests, system operations).
   */
  public boolean hasTenantContext() {
    return TenantContextHolder.getTenant() != null;
  }
}
