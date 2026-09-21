package com.example.multitenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.multitenant.example.Project;
import com.example.multitenant.example.ProjectRepository;
import com.example.multitenant.tenant.Tenant;
import com.example.multitenant.tenant.TenantContextHolder;
import com.example.multitenant.tenant.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shows the guarantees the tenant wiring gives you, against a real PostgreSQL (Testcontainers). Every test sets the tenant context the same way the request filter does, and the
 * context is cleared afterwards.
 *
 * <p>Adapted from the pattern of our internal repository isolation tests: two tenants, data written under one, reads attempted under the other.
 *
 * <p>Why a real database and not H2 or mocks: the guarantee being tested is the SQL Hibernate generates ({@code ... WHERE tenant_id_filter = ?}), so the test needs the real
 * engine and the real Flyway schema. Mocked repositories would prove nothing about isolation.
 */
@SpringBootTest
@Testcontainers
class TenantIsolationIntegrationTest {

  // One container shared by all tests in this class. Flyway (auto-configured) creates the schema from db/migration on startup.
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  // The container's port is random, so the datasource settings can only be supplied at runtime. These override the ${DB_URL}-style placeholders in application.yml.
  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private ProjectRepository projectRepository;

  private Tenant tenantA;
  private Tenant tenantB;

  @BeforeEach
  void setUp() {
    TenantContextHolder.clear();
    // Tenant is not tenant-aware, so it can be created under any context. Names are random because tenants.name is unique and the database is shared between tests.
    tenantA = tenantRepository.save(Tenant.builder().name("tenant-a-" + UUID.randomUUID()).build());
    tenantB = tenantRepository.save(Tenant.builder().name("tenant-b-" + UUID.randomUUID()).build());
  }

  @AfterEach
  void tearDown() {
    // Root mode sees every tenant's rows, so this cleans up all of them. Always clear the context afterwards, exactly as the request filter's finally block does; a leftover
    // tenant would leak into whichever test runs next on this thread.
    TenantContextHolder.setPublicRequest(true);
    projectRepository.deleteAll();
    TenantContextHolder.clear();
  }

  // Writes: the tenant comes from the context, not from the caller. Application code never has to pass it.
  @Test
  void save_WithTenantContext_StampsTheCurrentTenant() {
    TenantContextHolder.setTenant(tenantA);

    Project saved = projectRepository.save(Project.builder().name("alpha").build());

    // Both the relationship (tenant) and the Hibernate discriminator (tenantIdFilter) must agree.
    assertThat(saved.getTenant().getId()).isEqualTo(tenantA.getId());
    assertThat(saved.getTenantIdFilter()).isEqualTo(String.valueOf(tenantA.getId()));
  }

  // Reads: this is the core promise. Note that every kind of lookup is covered (findAll, findById, derived query), because each is a different generated query.
  @Test
  void queries_AsAnotherTenant_DoNotSeeTheRows() {
    TenantContextHolder.setTenant(tenantA);
    Project alpha = projectRepository.save(Project.builder().name("alpha").build());

    TenantContextHolder.setTenant(tenantB);

    assertThat(projectRepository.findAll()).isEmpty();
    // Even knowing the exact id of another tenant's row is not enough to load it.
    assertThat(projectRepository.findById(alpha.getId())).isEmpty();
    assertThat(projectRepository.findByName("alpha")).isEmpty();

    // Switching back proves the row was hidden by the filter, not lost.
    TenantContextHolder.setTenant(tenantA);

    assertThat(projectRepository.findAll()).hasSize(1);
  }

  // Fail closed: forgetting to set a tenant must never widen access.
  @Test
  void queries_WithoutTenantContext_SeeNothing() {
    TenantContextHolder.setTenant(tenantA);
    projectRepository.save(Project.builder().name("alpha").build());

    // No tenant, not a public request: the resolver falls back to "-1", which matches no row.
    TenantContextHolder.clear();

    assertThat(projectRepository.findAll()).isEmpty();
  }

  // The deliberate exception. Documents exactly what "public request" costs: no filtering at all.
  @Test
  void queries_InPublicRequestMode_SeeEveryTenant() {
    TenantContextHolder.setTenant(tenantA);
    projectRepository.save(Project.builder().name("alpha").build());
    TenantContextHolder.setTenant(tenantB);
    projectRepository.save(Project.builder().name("beta").build());

    // isRoot() is true, so Hibernate stops appending the tenant predicate.
    TenantContextHolder.clear();
    TenantContextHolder.setPublicRequest(true);

    assertThat(projectRepository.findAll()).extracting(Project::getName).containsExactlyInAnyOrder("alpha", "beta");
  }

  // The aspect's second job: refuse to write an entity that points at another tenant, e.g. one built from an id supplied by a client.
  @Test
  void save_WithEntityOfAnotherTenant_IsRejected() {
    TenantContextHolder.setTenant(tenantA);
    Project foreign = Project.builder().name("gamma").tenant(tenantB).build();

    assertThatThrownBy(() -> projectRepository.save(foreign))
          .isInstanceOf(SecurityException.class)
          .hasMessageContaining("mismatched tenant");
  }
}
