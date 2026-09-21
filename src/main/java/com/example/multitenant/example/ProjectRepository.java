package com.example.multitenant.example;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Note that no method mentions a tenant. Hibernate appends {@code tenant_id_filter = ?} to every derived query, {@code findAll()} and {@code findById()} for us.
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

  // Returns only the current tenant's projects with this name. Another tenant's project with the same name is invisible, which the isolation test checks.
  List<Project> findByName(String name);
}
