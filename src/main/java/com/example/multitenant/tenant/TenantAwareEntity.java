package com.example.multitenant.tenant;

import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Abstract class to be extended by all entities that need to be tenant-aware.
 *
 * <p>Two columns carry the tenant:
 * <ul>
 *   <li>{@code tenant_id}: a regular foreign key to {@code tenants(id)}, used for relational integrity and for joins.</li>
 *   <li>{@code tenant_id_filter}: the Hibernate {@link TenantId} discriminator. Hibernate appends {@code tenant_id_filter = ?} to every query on the entity and populates it on
 *   insert, using the value from {@link CurrentTenantIdentifierResolverImpl}.</li>
 * </ul>
 */
@MappedSuperclass
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public abstract class TenantAwareEntity {

  @Id
  @Tsid
  protected Long id;

  @ManyToOne
  @JoinColumn(name = "tenant_id")
  protected Tenant tenant;

  @TenantId
  protected String tenantIdFilter;

  @CreationTimestamp
  protected LocalDateTime createdAt;

  @UpdateTimestamp
  protected LocalDateTime updatedAt;

  public boolean belongsToTenant(Long tenantId) {
    return tenant != null && tenant.getId().equals(tenantId);
  }
}
