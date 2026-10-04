package com.curiouskids.club.support;

import com.curiouskids.club.shared.internal.web.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for tests that need the application, a real PostgreSQL and a real SMTP server (Mailpit). One
 * container of each is started for the whole test run and shared by every test class (singleton
 * container pattern). Scheduled jobs are off; tests run them directly.
 */
@SpringBootTest(properties = {"springdoc.api-docs.enabled=true", "club.jobs.enabled=false"})
@Import(TestEmailConfig.class)
public abstract class IntegrationTest {

  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

  static final GenericContainer<?> MAILPIT =
      new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.4"))
          .withExposedPorts(1025, 8025);

  static {
    POSTGRES.start();
    MAILPIT.start();
  }

  /** Mailpit's web API, for reading what was sent. */
  protected static Mailpit mailpit() {
    return new Mailpit("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025));
  }

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.mail.host", MAILPIT::getHost);
    registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
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
