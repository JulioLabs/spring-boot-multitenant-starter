package com.example.multitenant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point. Component scanning starts at this package, which is what picks up the tenancy beans (the Hibernate resolver, the aspect, the filter registration) and the entities.
 * If you move this class, make sure those packages stay under it.
 */
@SpringBootApplication
public class MultitenantStarterApplication {

  public static void main(String[] args) {
    SpringApplication.run(MultitenantStarterApplication.class, args);
  }
}
