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
 */
@SpringBootTest
@Testcontainers
class TenantIsolationIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

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
    // Tenant is not tenant-aware, so it can be created under any context.
    tenantA = tenantRepository.save(Tenant.builder().name("tenant-a-" + UUID.randomUUID()).build());
    tenantB = tenantRepository.save(Tenant.builder().name("tenant-b-" + UUID.randomUUID()).build());
  }

  @AfterEach
  void tearDown() {
    // Root mode sees every tenant's rows, so this cleans up all of them.
    TenantContextHolder.setPublicRequest(true);
    projectRepository.deleteAll();
    TenantContextHolder.clear();
  }

  @Test
  void save_WithTenantContext_StampsTheCurrentTenant() {
    TenantContextHolder.setTenant(tenantA);

    Project saved = projectRepository.save(Project.builder().name("alpha").build());

    assertThat(saved.getTenant().getId()).isEqualTo(tenantA.getId());
    assertThat(saved.getTenantIdFilter()).isEqualTo(String.valueOf(tenantA.getId()));
  }

  @Test
  void queries_AsAnotherTenant_DoNotSeeTheRows() {
    TenantContextHolder.setTenant(tenantA);
    Project alpha = projectRepository.save(Project.builder().name("alpha").build());

    TenantContextHolder.setTenant(tenantB);

    assertThat(projectRepository.findAll()).isEmpty();
    assertThat(projectRepository.findById(alpha.getId())).isEmpty();
    assertThat(projectRepository.findByName("alpha")).isEmpty();

    TenantContextHolder.setTenant(tenantA);

    assertThat(projectRepository.findAll()).hasSize(1);
  }

  @Test
  void queries_WithoutTenantContext_SeeNothing() {
    TenantContextHolder.setTenant(tenantA);
    projectRepository.save(Project.builder().name("alpha").build());

    // No tenant, not a public request: the resolver falls back to "-1", which matches no row.
    TenantContextHolder.clear();

    assertThat(projectRepository.findAll()).isEmpty();
  }

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

  @Test
  void save_WithEntityOfAnotherTenant_IsRejected() {
    TenantContextHolder.setTenant(tenantA);
    Project foreign = Project.builder().name("gamma").tenant(tenantB).build();

    assertThatThrownBy(() -> projectRepository.save(foreign))
          .isInstanceOf(SecurityException.class)
          .hasMessageContaining("mismatched tenant");
  }
}
