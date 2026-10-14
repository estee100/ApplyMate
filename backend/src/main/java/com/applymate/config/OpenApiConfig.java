package com.applymate.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI document at /api/v3/api-docs, Swagger UI at /api/swagger-ui.html. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI applyMateOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ApplyMate API")
                        .version("2")
                        .description("Job search, AI match analysis, tailored documents and application tracking. "
                                + "Sign in with POST /auth/login, then press Authorize and paste the token."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
