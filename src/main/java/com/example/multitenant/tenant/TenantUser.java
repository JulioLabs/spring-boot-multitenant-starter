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
 *
 * <p>It extends {@link TenantAwareEntity}, so membership rows are themselves isolated: inside a tenant's context you can only see that tenant's members. The one lookup that has to
 * cross tenants (finding a user's tenant at login time) is a native query, see {@link TenantUserRepository#findByUserEmail(String)}.
 */
@Table(name = "tenant_users")
@Entity
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class TenantUser extends TenantAwareEntity {

  // The user is global, so this is a plain reference to the users table with no tenant filter on the other side.
  @ManyToOne
  @JoinColumn(name = "user_id", nullable = false)
  private User user;
}
