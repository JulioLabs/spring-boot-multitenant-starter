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
 * <p>{@link TransmittableThreadLocal} is used instead of a plain {@code ThreadLocal}, but note that it only propagates to other threads when they are created through a TTL-aware
 * executor (or the TTL Java agent). See the "Known limitations" section of the README.
 */
public class TenantContextHolder {

  private static final TransmittableThreadLocal<Tenant> TENANT = new TransmittableThreadLocal<>();
  private static final TransmittableThreadLocal<Boolean> PUBLIC_REQUEST = new TransmittableThreadLocal<>();

  /**
   * Sets the current tenant. Also marks the request as non-public, so tenant filtering is enforced.
   */
  public static void setTenant(Tenant tenant) {
    TENANT.set(tenant);
    PUBLIC_REQUEST.set(false);
  }

  public static Boolean getPublicRequest() {
    return PUBLIC_REQUEST.get();
  }

  public static Tenant getTenant() {
    return TENANT.get();
  }

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

  public static void setPublicRequest(boolean b) {
    PUBLIC_REQUEST.set(b);
  }

  /**
   * Must be called in a {@code finally} block at the end of every unit of work (request, async task, scheduled job) that set the context.
   */
  public static void clear() {
    TENANT.remove();
    PUBLIC_REQUEST.remove();
  }
}
