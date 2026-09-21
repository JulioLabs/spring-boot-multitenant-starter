package com.example.multitenant.tenant;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * Holds tenant information in a ThreadLocal for the current request. This allows the tenant to be accessible throughout request processing, including from Hibernate's
 * {@link CurrentTenantIdentifierResolverImpl}.
 *
 * <p>Two pieces of state live here:
 * <ul>
 *   <li>the current {@link Tenant} (absent for unauthenticated/public requests), and</li>
 *   <li>a "public request" flag. When true, {@link CurrentTenantIdentifierResolverImpl#isRoot(Object)} reports root mode and Hibernate stops appending the tenant predicate.</li>
 * </ul>
 *
 * <p>Timing matters: Hibernate resolves the tenant when it opens a session, so the context must be set before the transaction (and therefore the session) starts. Changing it
 * halfway through a transaction does not change what an already-open session filters on.
 *
 * <p>{@link TransmittableThreadLocal} is used instead of a plain {@code ThreadLocal}, but note that it only propagates to other threads when they are created through a TTL-aware
 * executor (or the TTL Java agent). See the "Known limitations" section of the README.
 */
public class TenantContextHolder {

  // The current tenant. Null means "no tenant known", which the resolver turns into the fail-closed "-1".
  private static final TransmittableThreadLocal<Tenant> TENANT = new TransmittableThreadLocal<>();

  // Null means "never set", which CurrentTenantIdentifierResolverImpl#isRoot treats the same as false.
  private static final TransmittableThreadLocal<Boolean> PUBLIC_REQUEST = new TransmittableThreadLocal<>();

  /**
   * Sets the current tenant. Also marks the request as non-public, so tenant filtering is enforced.
   *
   * <p>Because this resets the public flag, call {@link #setPublicRequest(boolean)} AFTER it (never before) if you need a public request with no tenant.
   */
  public static void setTenant(Tenant tenant) {
    TENANT.set(tenant);
    PUBLIC_REQUEST.set(false);
  }

  /**
   * True when the current unit of work is allowed to see across tenants (Hibernate root mode). Null if never set.
   */
  public static Boolean getPublicRequest() {
    return PUBLIC_REQUEST.get();
  }

  public static Tenant getTenant() {
    return TENANT.get();
  }

  /**
   * Alias of {@link #getTenant()}, kept because both spellings are used across the codebase.
   */
  public static Tenant getCurrentTenant() {
    return TENANT.get();
  }

  /**
   * The tenant id as a String, because {@code @TenantId} columns are Strings. Null when no tenant is set.
   */
  public static String getCurrentTenantId() {
    if (TENANT.get() != null) {
      return TENANT.get().getId() + "";
    } else {
      return null;
    }
  }

  /**
   * Marks the current unit of work as public (no tenant, unfiltered reads) or not. See {@link #setTenant(Tenant)} for the ordering rule.
   */
  public static void setPublicRequest(boolean b) {
    PUBLIC_REQUEST.set(b);
  }

  /**
   * Must be called in a {@code finally} block at the end of every unit of work (request, async task, scheduled job) that set the context. Uses {@code remove()} rather than
   * {@code set(null)} so the value is actually released from pooled threads.
   */
  public static void clear() {
    TENANT.remove();
    PUBLIC_REQUEST.remove();
  }
}
