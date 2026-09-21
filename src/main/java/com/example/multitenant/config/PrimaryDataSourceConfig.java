package com.example.multitenant.config;

import com.example.multitenant.tenant.CurrentTenantIdentifierResolverImpl;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Builds the primary DataSource / EntityManagerFactory and hands Hibernate our {@link CurrentTenantIdentifierResolverImpl}.
 *
 * <p>Together with {@code spring.jpa.properties.hibernate.multiTenancy=DISCRIMINATOR} (see application.yml), the {@code hibernate.tenant_identifier_resolver} property is the piece
 * that switches Hibernate into discriminator-column multi-tenancy.
 *
 * <p>The three beans (DataSource, EntityManagerFactory, TransactionManager) are declared by hand so the Spring-managed resolver can be handed to the EntityManagerFactory. Declaring
 * them explicitly also leaves room for a second, non-tenant-aware persistence unit next to this one, for data that should never be filtered by tenant. The {@code @Primary}
 * markers keep this one the default.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.example.multitenant", entityManagerFactoryRef = "entityManagerFactory", transactionManagerRef = "transactionManager")
public class PrimaryDataSourceConfig {

  @Primary
  @Bean(name = "dataSource", destroyMethod = "close")
  public DataSource dataSource(@Value("${spring.datasource.url}") String url, @Value("${spring.datasource.username}") String username, @Value("${spring.datasource.password}") String password,
                               @Value("${spring.datasource.driver-class-name}") String driverClassName) {

    // Connection details come from configuration (environment variables in application.yml), never from code.
    return DataSourceBuilder
          .create()
          .url(url)
          .username(username)
          .password(password)
          .driverClassName(driverClassName)
          .build();
  }

  @Primary
  @Bean(name = "entityManagerFactory")
  public LocalContainerEntityManagerFactoryBean entityManagerFactory(
      EntityManagerFactoryBuilder builder,
      @Qualifier("dataSource") DataSource dataSource,
      CurrentTenantIdentifierResolverImpl tenantIdentifierResolver) {

    // The builder is provided by Spring Boot and carries the spring.jpa.* settings, including hibernate.multiTenancy=DISCRIMINATOR. The one thing it cannot know about is our
    // resolver, so we add it here. Passing the bean instance (not a class name) means Hibernate uses the exact object Spring manages.
    return builder
          .dataSource(dataSource)
          .packages("com.example.multitenant")  // where Hibernate looks for @Entity classes
          .persistenceUnit("primaryPU")
          .properties(Map.of("hibernate.tenant_identifier_resolver", tenantIdentifierResolver))
          .build();
  }

  @Primary
  @Bean(name = "transactionManager")
  public PlatformTransactionManager transactionManager(@Qualifier("entityManagerFactory") EntityManagerFactory emf) {

    // Transactions must be bound to the tenant-aware EntityManagerFactory above, otherwise repositories would get sessions that ignore the tenant.
    return new JpaTransactionManager(emf);
  }
}
