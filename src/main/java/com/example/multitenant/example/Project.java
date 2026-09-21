package com.example.multitenant.example;

import com.example.multitenant.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Example of a tenant-scoped entity. Everything tenancy-related is inherited from {@link TenantAwareEntity}: extending it is all an entity needs to do.
 *
 * <p>This class is invented for the reference repo; it is not part of any real product.
 */
@Table(name = "projects")
@Entity
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public class Project extends TenantAwareEntity {

  @Column(nullable = false)
  private String name;
}
