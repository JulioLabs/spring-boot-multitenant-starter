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
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.example.multitenant", entityManagerFactoryRef = "entityManagerFactory", transactionManagerRef = "transactionManager")
public class PrimaryDataSourceConfig {

  @Primary
  @Bean(name = "dataSource", destroyMethod = "close")
  public DataSource dataSource(@Value("${spring.datasource.url}") String url, @Value("${spring.datasource.username}") String username, @Value("${spring.datasource.password}") String password,
                               @Value("${spring.datasource.driver-class-name}") String driverClassName) {

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

    return builder
          .dataSource(dataSource)
          .packages("com.example.multitenant")
          .persistenceUnit("primaryPU")
          .properties(Map.of("hibernate.tenant_identifier_resolver", tenantIdentifierResolver))
          .build();
  }

  @Primary
  @Bean(name = "transactionManager")
  public PlatformTransactionManager transactionManager(@Qualifier("entityManagerFactory") EntityManagerFactory emf) {

    return new JpaTransactionManager(emf);
  }
}
