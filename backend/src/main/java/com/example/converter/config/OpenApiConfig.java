package com.example.converter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.models.GroupedOpenApi;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI api() {
    return new OpenAPI()
      .info(new Info()
        .title("Converter Backend API")
        .version("v1")
        .description("API documentation for Currency & Unit Conversion Service")
        .contact(new Contact()
          .name("Backend Team")
        )
      );
  }

  @Bean
  public GroupedOpenApi allApis() {
    return GroupedOpenApi.builder()
      .group("all")
      .pathsToMatch("/**")
      .build();
  }

  @Bean
  public GroupedOpenApi authApi() {
    return GroupedOpenApi.builder()
      .group("authentication")
      .pathsToMatch("/api/auth/**") // Only unified auth patterns
      .build();
  }

  @Bean
  public GroupedOpenApi conversionApi() {
    return GroupedOpenApi.builder()
      .group("conversion")
      .pathsToMatch("/convert/**", "/api/convert/**") // Conversion endpoints
      .build();
  }

  @Bean
  public GroupedOpenApi newsApi() {
    return GroupedOpenApi.builder()
      .group("news")
      .pathsToMatch("/news/**", "/api/news/**") // News endpoints
      .build();
  }

  @Bean
  public GroupedOpenApi internalApi() {
    return GroupedOpenApi.builder()
      .group("internal")
      .pathsToMatch("/internal/**", "/api/internal/**") // Internal endpoints
      .build();
  }
}
