package com.rally26.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Springdoc's live-generated view exists for local exploration only. `docs/openapi.yaml`
 * remains the authoritative, hand-maintained contract referenced by DESIGN-DOC.md —
 * keep them in sync manually when endpoints change (section 13.3).
 */
@Configuration
class OpenApiConfig {
    @Bean
    fun rally26OpenApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Rally26 API")
                    .version("0.1.0")
                    .description("See docs/openapi.yaml in the repository for the authoritative contract."),
            ).addSecurityItem(SecurityRequirement().addList("bearerAuth"))
            .components(
                io.swagger.v3.oas.models
                    .Components()
                    .addSecuritySchemes(
                        "bearerAuth",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT"),
                    ),
            )
}
