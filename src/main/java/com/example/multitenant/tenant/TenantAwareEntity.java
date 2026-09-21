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
 *
 * <p>Subclasses need nothing extra: extending this class is the whole opt-in. {@code @SuperBuilder} is used so subclasses can chain their own fields in the builder.
 */
@MappedSuperclass
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public abstract class TenantAwareEntity {

  /**
   * Time-sorted unique id generated in the application (not by the database), so ids are unique across tenants without a shared sequence. These values are larger than 2^53,
   * which is the limit of exact integers in JavaScript, so they must be serialized to JSON as strings.
   */
  @Id
  @Tsid
  protected Long id;

  /**
   * The owning tenant as a relationship, for joins and referential integrity. Isolation itself comes from {@link #tenantIdFilter}, not from this field. It is filled by
   * {@link TenantAwareAspect} on save.
   */
  @ManyToOne
  @JoinColumn(name = "tenant_id")
  protected Tenant tenant;

  /**
   * The discriminator Hibernate filters on. It is a String because that is what {@link CurrentTenantIdentifierResolverImpl} returns. It sits next to {@link #tenant}, which holds
   * the same tenant as a relationship: this column is the value Hibernate manages, that one is the link you can join on.
   */
  @TenantId
  protected String tenantIdFilter;

  @CreationTimestamp
  protected LocalDateTime createdAt;

  @UpdateTimestamp
  protected LocalDateTime updatedAt;

  /**
   * Null-safe ownership check used by {@link TenantAwareAspect} to reject cross-tenant saves.
   */
  public boolean belongsToTenant(Long tenantId) {
    return tenant != null && tenant.getId().equals(tenantId);
  }
}
