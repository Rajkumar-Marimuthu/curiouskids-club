package com.curiouskids.club.support;

import com.curiouskids.club.shared.internal.web.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for tests that need the application and a real PostgreSQL. One container is started for the
 * whole test run and shared by every test class (singleton container pattern).
 */
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
public abstract class IntegrationTest {

  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired protected WebApplicationContext context;

  protected MockMvc mvc;

  @BeforeEach
  void setUpMockMvc() {
    mvc =
        MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean(TraceIdFilter.class))
            .build();
  }
}
