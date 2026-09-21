package com.example.multitenant.tenant;

import com.example.multitenant.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Membership: links a globally-unique {@link User} to a {@link Tenant}. This is how a logged-in user is mapped to a tenant on each request.
 */
@Table(name = "tenant_users")
@Entity
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class TenantUser extends TenantAwareEntity {

  @ManyToOne
  @JoinColumn(name = "user_id", nullable = false)
  private User user;
}
