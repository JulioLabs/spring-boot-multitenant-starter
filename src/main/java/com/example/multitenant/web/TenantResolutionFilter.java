package com.example.multitenant.web;

import com.example.multitenant.tenant.TenantContextHolder;
import com.example.multitenant.tenant.TenantUser;
import com.example.multitenant.tenant.TenantUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.GenericFilterBean;

/**
 * Resolves the current tenant for each request and publishes it in {@link TenantContextHolder}.
 *
 * <p>NOTE: this is a simplified rewrite of the production filter, which additionally resolves tenants from the request host and from API tokens. Only the session-based branch is
 * kept here, since that is the one that shows how the tenant context is wired into Hibernate.
 *
 * <p>Rules:
 * <ul>
 *   <li>Static resources and health checks are skipped.</li>
 *   <li>Non-API paths (login pages, public pages) run as "public requests": no tenant, Hibernate root mode.</li>
 *   <li>API paths with an authenticated user resolve the tenant from the user's {@link TenantUser} membership.</li>
 *   <li>API paths without authentication are public requests.</li>
 *   <li>The context is always cleared in {@code finally}, so a pooled servlet thread never carries a tenant into the next request.</li>
 * </ul>
 *
 * <p>This filter must run AFTER Spring Security, because it reads the {@link Authentication} from {@link SecurityContextHolder}. See {@link TenantFilterConfig}.
 */
public class TenantResolutionFilter extends GenericFilterBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantResolutionFilter.class);

    private static final String TENANT_SCOPED_PATH_PREFIX = "/api";

    private final TenantUserRepository tenantUserRepository;

    public TenantResolutionFilter(TenantUserRepository tenantUserRepository) {
        this.tenantUserRepository = tenantUserRepository;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (!((servletRequest instanceof HttpServletRequest httpRequest) && (servletResponse instanceof HttpServletResponse))) {
            throw new ServletException("TenantResolutionFilter only supports HTTP requests");
        }

        String path = httpRequest.getRequestURI();

        // Skip filter for static resources, health checks, etc.
        if (shouldSkipFilter(path)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        try {
            resolveTenant(path);
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private void resolveTenant(String path) {
        if (!path.startsWith(TENANT_SCOPED_PATH_PREFIX)) {
            // Not a tenant-scoped endpoint: public request, Hibernate runs in root mode.
            TenantContextHolder.setPublicRequest(true);
            return;
        }

        Authentication auth = SecurityContextHolder
                .getContext()
                .getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserDetails userDetails) {
            // Native query: it must run before the tenant is known. See TenantUserRepository.
            TenantUser membership = tenantUserRepository
                    .findByUserEmail(userDetails.getUsername())
                    .orElseThrow(() -> new IllegalStateException("Authenticated user has no tenant membership"));

            TenantContextHolder.setTenant(membership.getTenant());
            LOGGER.trace("Set tenant {} for authenticated user", membership
                    .getTenant()
                    .getId());
        } else {
            TenantContextHolder.setTenant(null);
            TenantContextHolder.setPublicRequest(true);
            LOGGER.debug("No authenticated user, treating as public request");
        }
    }

    private boolean shouldSkipFilter(String path) {
        return path.startsWith("/static/") || path.startsWith("/assets/") || path.startsWith("/favicon.ico") || path.startsWith("/health") || path.startsWith("/actuator/");
    }
}
