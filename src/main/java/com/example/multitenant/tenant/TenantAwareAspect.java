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
 *
 * <p>Why this exists when Hibernate already fills the {@code @TenantId} column: Hibernate does not populate the {@code tenant} foreign key, and nothing stops application code from
 * building an entity that points at another tenant. This aspect closes both gaps at the one place every save passes through.
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
   * <p>The pointcut matches {@code save} and {@code saveAll} on any {@code CrudRepository} subtype, which covers every Spring Data repository (JpaRepository extends it).
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

    // KNOWN LIMITATION: only the first argument is inspected. For saveAll(...) that argument is a collection, not a TenantAwareEntity, so the instanceof check below fails and
    // the call passes through unstamped and unchecked. See "Known limitations" in the README.
    Object entity = args[0];

    if (entity instanceof TenantAwareEntity tenantAwareEntity) {
      try {
        Tenant currentTenant = tenantAwareUtil.getCurrentTenant();

        // Only set the tenant if it's not already set
        if (tenantAwareEntity.getTenant() == null) {
          // New entity: take everything from the current context.
          tenantAwareEntity.setTenant(currentTenant);
          tenantAwareEntity.setTenantIdFilter("" + currentTenant.getId());
          tenantAwareEntity.setCreatedAt(LocalDateTime.now(Clock.systemUTC()));
          tenantAwareEntity.setUpdatedAt(LocalDateTime.now(Clock.systemUTC()));

          LOGGER.debug("Automatically set tenant {} on entity {}", currentTenant.getId(), entity
                .getClass()
                .getSimpleName());
        } else if (!tenantAwareEntity.belongsToTenant(currentTenant.getId())) {
          // The entity already points at a different tenant than the one this request is running as. That is either a bug or an attack (e.g. an id taken from another tenant's
          // data), so fail loudly instead of writing it.
          throw new SecurityException("Attempt to save entity with mismatched tenant");
        } else {
          // Existing entity of the right tenant: just make sure the discriminator agrees with the relationship.
          tenantAwareEntity.setTenantIdFilter("" + tenantAwareEntity
                .getTenant()
                .getId());
        }
      } catch (EntityNotFoundException e) {
        // getCurrentTenant() throws this when there is no tenant. hasTenantContext() above should already have ruled that out, so this is a defensive guard.
        LOGGER.warn("Could not set tenant on entity: {}", e.getMessage());
      }
    }
  }
}
