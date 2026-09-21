package com.example.multitenant.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Users are global, not tenant-scoped: there is no {@code @TenantId} here. A user belongs to a tenant only through a
 * {@link com.example.multitenant.tenant.TenantUser} row. (Reduced to the fields this reference needs.)
 */
@Table(name = "users")
@Entity
@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Globally unique (not per tenant), and the key the request filter uses to find a user's tenant. See TenantUserRepository#findByUserEmail.
  @Column(nullable = false, unique = true)
  private String email;
}
