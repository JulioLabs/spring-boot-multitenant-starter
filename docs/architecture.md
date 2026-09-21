# Architecture walkthrough

This document explains why the tenancy wiring looks the way it does. For a file-by-file map, see the [README](../README.md).

## 1. Choosing an isolation model

| Model | Isolation | Cost |
|---|---|---|
| Database per tenant | Strongest | One connection pool and migration run per tenant, so operational cost grows with tenant count |
| Schema per tenant | Strong | Needs a `MultiTenantConnectionProvider` and a migration per schema |
| **Shared schema + discriminator column** | Enforced in the ORM | One schema, one migration, cheap to add tenants. Isolation depends on every query going through Hibernate |

This design uses the last one. Hibernate 6 supports it natively: set `hibernate.multiTenancy=DISCRIMINATOR`, mark one column with `@TenantId`, and provide a `CurrentTenantIdentifierResolver`. Hibernate then adds `AND tenant_id_filter = ?` to every query on that entity and fills the column on insert. Postgres row-level security is not part of this design.

## 2. The three moving parts

### 2.1 Where the tenant comes from: `TenantContextHolder`

A static holder around two `TransmittableThreadLocal`s (the tenant and the public-request flag). Static access is deliberate: the resolver reads it directly instead of having it injected, which avoids circular dependencies during application startup.

Rules for using it:

1. Whoever sets the context clears it, in a `finally` block. Servlet threads are pooled, so a leaked tenant would be inherited by the next request.
2. `setTenant(...)` also sets the public-request flag to `false`, so it can never leave a stale "public" flag behind.

### 2.2 How Hibernate learns it: `CurrentTenantIdentifierResolverImpl`

- `resolveCurrentTenantIdentifier()` returns the current tenant id, or `"-1"` when there is none. No real tenant has id `-1`, so a request with no tenant context sees no rows rather than all of them. This is the **fail-closed default**.
- `isRoot(...)` returns `true` for public requests. In root mode Hibernate does not add the tenant predicate. This is what lets unauthenticated pages (login, public content) and background jobs read across tenants, and it is also the largest hole in the design: see "Public requests" below.
- The resolver is registered with `hibernate.tenant_identifier_resolver` in `PrimaryDataSourceConfig`, because we build the `EntityManagerFactory` explicitly.

### 2.3 What gets filtered: `TenantAwareEntity`

A `@MappedSuperclass` with two tenant columns:

- `tenant_id`, a normal foreign key. It keeps the data relationally sound and lets you join to `tenants`.
- `tenant_id_filter`, the `@TenantId` discriminator. It is a string because that is the type the resolver returns.

An entity opts in by extending the class. Repositories need no tenant parameters.

## 3. The request lifecycle

1. **Spring Security runs first.** Spring Boot registers its filter chain at order `-100`. `TenantFilterConfig` registers the tenant filter at order `1`, which places it after security. This matters because the filter reads the `Authentication` from `SecurityContextHolder`.
2. **`TenantResolutionFilter`** decides between three cases:
   - static resources and health checks: skipped;
   - non-API paths and unauthenticated API requests: *public request*, no tenant;
   - authenticated API requests: look up the user's `TenantUser` and set the tenant.
3. **The chain continues.** Controllers, services and repositories run with the tenant set.
4. **`finally { TenantContextHolder.clear(); }`**

### The bootstrap query has to bypass the discriminator

At step 2 no tenant is set yet, so the resolver returns `"-1"`. A normal derived query on `TenantUser` would therefore be filtered with `tenant_id_filter = '-1'` and find nothing. `TenantUserRepository.findByUserEmail` is a **native query**, which Hibernate does not rewrite, so it can find the membership row that establishes the tenant in the first place.

The consequence is a rule of thumb for the whole codebase: native queries are outside the safety net, and every one of them must scope by tenant on its own.

## 4. Writes: `TenantAwareAspect`

Hibernate populates `tenant_id_filter` on insert, but nothing populates the `tenant` foreign key or stops code from saving an entity that belongs to another tenant. The aspect intercepts `CrudRepository.save`/`saveAll` and:

- sets `tenant`, `tenantIdFilter` and the timestamps when the entity has no tenant yet;
- throws `SecurityException` when the entity's tenant differs from the current one;
- does nothing when there is no tenant context (system operations, public requests).

It only looks at the first argument, so `saveAll(collection)` is not covered. See the README.

## 5. Public requests

The "public request" flag exists because some code legitimately has no tenant: login pages, public content, scheduled jobs that iterate over tenants. Setting it flips Hibernate into root mode, and **every query then runs unfiltered**.

That makes public request handlers the place where tenant leaks would happen. Keeping them thin, looking things up by an explicit identifier and then setting the tenant from the loaded record, keeps the unfiltered window small.

## 6. Threads and async work

`TransmittableThreadLocal` copies context to threads created by a TTL-wrapped executor, and only to those. With plain `@Async` or `@Scheduled` the context is not carried across, so each such unit of work should:

1. load the tenant it belongs to (from the event or the row it is processing),
2. call `TenantContextHolder.setTenant(...)`,
3. do the work,
4. call `TenantContextHolder.clear()` in `finally`.

A missing `clear()` is a real hazard: the worker thread is reused and the next task silently inherits the previous tenant.

## 7. Schema conventions

See `V1__tenancy_core.sql`:

- Tenants and users are global tables with no discriminator.
- Every tenant-aware table has `tenant_id`, `tenant_id_filter` (`VARCHAR`, default `'-1'`) and application-assigned ids.
- `tenant_id_filter` leads its indexes, since every generated query filters on it.
