package com.example.multitenant.tenant;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, Long> {

  /**
   * Looks up a user's tenant membership by email.
   *
   * <p>This is intentionally a NATIVE query. It runs inside the tenant-resolution filter, before any tenant is known, so the current tenant identifier is still the fail-closed
   * {@code "-1"}. A derived/JPQL query on {@link TenantUser} would get {@code tenant_id_filter = '-1'} appended by Hibernate and never find the row. Native queries are not touched
   * by the {@code @TenantId} discriminator, which is exactly what this bootstrap lookup needs. The flip side: every native query in a tenant-aware codebase must scope by tenant
   * explicitly.
   *
   * <p>Returns an {@link Optional}, so this assumes a user has at most one membership. A user in several tenants would make this query return several rows and fail, and would need
   * a way to choose the active tenant (for example a tenant id in the session).
   */
  @Query(value = "SELECT tu.* FROM tenant_users tu JOIN users u ON tu.user_id = u.id WHERE u.email = :email", nativeQuery = true)
  Optional<TenantUser> findByUserEmail(@Param("email") String email);
}
