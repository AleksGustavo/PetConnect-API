package com.petconnect.api.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados da documentação OpenAPI. UI em {@code /swagger-ui.html},
 * spec em {@code /v3/api-docs}.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "firebaseIdToken";

    @Bean
    OpenAPI petConnectOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PetConnect API")
                        .version("v1")
                        .description("API REST do PetConnect. Autenticação via Firebase ID Token "
                                + "no header Authorization: Bearer <token>.")
                        .license(new License().name("Proprietary")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
