package com.curiouskids.club.shared.internal.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadata of the exported OpenAPI document; must match {@code contracts/openapi.yaml}. */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

  @Bean
  OpenAPI clubOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Curiouskids Club API")
                .version("1.0.0")
                .description("Lending library API. Errors are RFC 9457 problem+json."));
  }
}
