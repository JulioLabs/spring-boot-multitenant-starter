package com.example.multitenant.tenant;

import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.LocalDateTime;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Stamps the current tenant on tenant-aware entities just before they are saved through a Spring Data repository, and refuses to save an entity that belongs to a different tenant.
 */
@Aspect
@Component
public class TenantAwareAspect {

  private static final Logger LOGGER = LoggerFactory.getLogger(TenantAwareAspect.class);

  private final TenantAwareUtil tenantAwareUtil;

  @Autowired
  public TenantAwareAspect(TenantAwareUtil tenantAwareUtil) {
    this.tenantAwareUtil = tenantAwareUtil;
  }

  /**
   * Sets the tenant on all TenantAwareEntity objects before they are saved.
   *
   * @param joinPoint the join point for the intercepted method
   */
  @Before("execution(* org.springframework.data.repository.CrudRepository+.save(..)) || " + "execution(* org.springframework.data.repository.CrudRepository+.saveAll(..))")
  public void beforeSave(JoinPoint joinPoint) {
    // Skip if no tenant context is available (e.g., system operations)
    if (!tenantAwareUtil.hasTenantContext()) {
      return;
    }

    // Get the entity being saved
    Object[] args = joinPoint.getArgs();
    if (args.length == 0) {
      return;
    }

    Object entity = args[0];

    if (entity instanceof TenantAwareEntity tenantAwareEntity) {
      try {
        Tenant currentTenant = tenantAwareUtil.getCurrentTenant();

        // Only set the tenant if it's not already set
        if (tenantAwareEntity.getTenant() == null) {

          tenantAwareEntity.setTenant(currentTenant);
          tenantAwareEntity.setTenantIdFilter("" + currentTenant.getId());
          tenantAwareEntity.setCreatedAt(LocalDateTime.now(Clock.systemUTC()));
          tenantAwareEntity.setUpdatedAt(LocalDateTime.now(Clock.systemUTC()));

          LOGGER.debug("Automatically set tenant {} on entity {}", currentTenant.getId(), entity
                .getClass()
                .getSimpleName());
        } else if (!tenantAwareEntity.belongsToTenant(currentTenant.getId())) {
          throw new SecurityException("Attempt to save entity with mismatched tenant");
        } else {
          tenantAwareEntity.setTenantIdFilter("" + tenantAwareEntity
                .getTenant()
                .getId());
        }
      } catch (EntityNotFoundException e) {
        LOGGER.warn("Could not set tenant on entity: {}", e.getMessage());
      }
    }
  }
}
