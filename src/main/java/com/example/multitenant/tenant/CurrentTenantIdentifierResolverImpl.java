package com.example.multitenant.tenant;


import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Tells Hibernate which tenant the current session belongs to. Registered on the EntityManagerFactory in
 * {@code PrimaryDataSourceConfig} via {@code hibernate.tenant_identifier_resolver}.
 *
 * <p>With {@code hibernate.multiTenancy=DISCRIMINATOR}, the value returned here is used for the {@code @TenantId} column on every entity query and insert.
 *
 * <p>Three decisions live in this class: what to return when there is no tenant (fail closed), when to switch filtering off entirely (root mode), and whether Hibernate should
 * double-check existing sessions.
 */
@SuppressWarnings("rawtypes")
@Component
public class CurrentTenantIdentifierResolverImpl implements CurrentTenantIdentifierResolver {

    /**
     * Returned when no tenant is known. It must never match a real tenant: real ids come from an identity column and start at 1, so {@code -1} can never be a real row. This is
     * the fail-closed default; a request that forgot to set a tenant sees an empty result instead of someone else's data.
     */
    public static final Long DEFAULT_TENANT_ID = -1L; // Use a value that won't match any real tenant

    // Instead of autowiring, use static reference
    // This avoids circular dependencies during application startup

    /**
     * Called by Hibernate when it needs the tenant for a session. The result becomes the value of the {@code @TenantId} column for that session's queries and inserts.
     */
    @Override
    public String resolveCurrentTenantIdentifier() {
        // Get tenant context from the statically accessible holder
        try {
            // Access TenantContextHolder statically or through a more careful approach
            if (TenantContextHolder.getCurrentTenantId() != null) {
                return TenantContextHolder.getCurrentTenantId();
            }

        } catch (Exception e) {
            // Defensive: this can be invoked very early (e.g. during startup, before any request exists). Never let it throw; fall back to the fail-closed value.
            return "" + DEFAULT_TENANT_ID;
        }

        // Default to an impossible tenant ID to prevent data leakage
        return "" + DEFAULT_TENANT_ID;
    }

    /**
     * Asks Hibernate to verify that an already-open "current session" still matches the tenant returned by {@link #resolveCurrentTenantIdentifier()}. Returning true means a
     * session opened for tenant A will not silently be reused after the context switched to tenant B.
     */
    @Override
    public boolean validateExistingCurrentSessions() {
        // Return true to indicate we want to validate tenant IDs on each session access
        return true;
    }

    /**
     * Root mode: when this returns true, Hibernate does not add the tenant predicate at all, so queries see every tenant's rows.
     *
     * <p>We enable it only for requests explicitly marked public (login pages, public content, cross-tenant background jobs). This is the largest hole in the design, and
     * therefore the most important line to audit. See "Public requests" in docs/architecture.md.
     */
    @Override
    public boolean isRoot(Object tenantId) {
        // Only return true (root mode) for public requests where tenant isolation should be bypassed
        // For all tenant-aware requests, return false to enforce tenant discrimination
        Boolean publicRequest = TenantContextHolder.getPublicRequest();
        return publicRequest != null && publicRequest;
    }
}
