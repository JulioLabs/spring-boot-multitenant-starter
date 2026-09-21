package com.example.multitenant.tenant;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Tenants are not tenant-aware entities, so these queries are never filtered by the current tenant and work under any context (including none).
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

  Optional<Tenant> findByName(String name);

  // Existence check that answers "is this name taken?" without loading the entity.
  @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Tenant t WHERE t.name = ?1")
  boolean doesTenantExist(String tenantName);
}
