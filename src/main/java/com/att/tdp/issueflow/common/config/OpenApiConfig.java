package com.att.tdp.issueflow.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger metadata for IssueFlow. Defines the API title/description and a
 * JWT bearer security scheme so the Swagger UI can carry the token (obtained from
 * {@code POST /auth/login}) on protected endpoints via the "Authorize" button.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI issueFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("IssueFlow API")
                        .description("RESTful backend for a lightweight project/issue tracking "
                                + "platform: users, projects, tickets, comments, plus audit log, "
                                + "dependencies, attachments, @mentions, CSV import/export, "
                                + "auto-assignment, and auto-escalation. All endpoints except "
                                + "POST /auth/login require a JWT bearer token.")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
