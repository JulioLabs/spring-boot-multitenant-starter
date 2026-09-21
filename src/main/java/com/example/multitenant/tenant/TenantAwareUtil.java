package com.example.multitenant.tenant;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class TenantAwareUtil {


  public Tenant getCurrentTenant() {
    Tenant tenant = TenantContextHolder.getTenant();
    if (tenant == null) {
      throw new EntityNotFoundException("No tenant set in current context");
    }

    return tenant;
  }

  public boolean hasTenantContext() {
    return TenantContextHolder.getTenant() != null;
  }
}
