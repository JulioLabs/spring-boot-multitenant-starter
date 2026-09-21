package com.example.multitenant.tenant;


import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Tells Hibernate which tenant the current session belongs to. Registered on the EntityManagerFactory in
 * {@code PrimaryDataSourceConfig} via {@code hibernate.tenant_identifier_resolver}.
 *
 * <p>With {@code hibernate.multiTenancy=DISCRIMINATOR}, the value returned here is used for the {@code @TenantId} column on every entity query and insert.
 */
@SuppressWarnings("rawtypes")
@Component
public class CurrentTenantIdentifierResolverImpl implements CurrentTenantIdentifierResolver {

    public static final Long DEFAULT_TENANT_ID = -1L; // Use a value that won't match any real tenant

    // Instead of autowiring, use static reference
    // This avoids circular dependencies during application startup

    @Override
    public String resolveCurrentTenantIdentifier() {
        // Get tenant context from the statically accessible holder
        try {
            // Access TenantContextHolder statically or through a more careful approach
            if (TenantContextHolder.getCurrentTenantId() != null) {
                return TenantContextHolder.getCurrentTenantId();
            }

        } catch (Exception e) {
            // If we can't get tenant context (e.g., during startup), use default
            return "" + DEFAULT_TENANT_ID;
        }

        // Default to an impossible tenant ID to prevent data leakage
        return "" + DEFAULT_TENANT_ID;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        // Return true to indicate we want to validate tenant IDs on each session access
        return true;
    }

    @Override
    public boolean isRoot(Object tenantId) {
        // Only return true (root mode) for public requests where tenant isolation should be bypassed
        // For all tenant-aware requests, return false to enforce tenant discrimination
        Boolean publicRequest = TenantContextHolder.getPublicRequest();
        return publicRequest != null && publicRequest;
    }
}
