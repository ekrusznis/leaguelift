package com.rally26.config

import com.fasterxml.jackson.databind.SerializationFeature
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring Boot 4.1 runs two independent Jackson generations side by side (see
 * spring-boot-4-quirks memory note #2):
 *
 * - Jackson 2.x (`com.fasterxml.jackson.*`) is *not* auto-configured as a Spring bean at
 *   all. [com.fasterxml.jackson.databind.ObjectMapper] is only needed here for a few
 *   repositories (e.g. `OrganizationRepository`, `OrderRepository`) that
 *   serialize/deserialize JSONB columns directly, so [objectMapper] below still provides
 *   it explicitly.
 * - Jackson 3.x (`tools.jackson.*`) is what Spring Boot 4.1 actually uses for HTTP
 *   request/response body conversion, via
 *   [org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration] building a
 *   [tools.jackson.databind.json.JsonMapper] from a
 *   [tools.jackson.databind.json.JsonMapper.Builder]. That builder is customizable
 *   through a [JsonMapperBuilderCustomizer] bean, which [jsonMapperBuilderCustomizer]
 *   provides.
 *
 * Both generations need `KotlinFeature.NullIsSameAsDefault` enabled on their respective
 * Kotlin modules (`com.fasterxml.jackson.module:jackson-module-kotlin` for the Jackson 2
 * mapper, `tools.jackson.module:jackson-module-kotlin` — relocated coordinates, same
 * class/package shape under `tools.jackson` — for the Jackson 3 mapper): without it, an
 * omitted JSON field whose Kotlin constructor parameter has a default value (e.g.
 * `CreateFeeTemplateRequest.currency: String = "USD"`) is passed as `null` by the
 * Kotlin-aware constructor lookup instead of being left unset for the default to apply,
 * which throws even though omitting the field is valid input. This does not affect
 * required (non-default) properties, which still fail to bind and are handled as 400s by
 * [com.rally26.common.error.GlobalExceptionHandler]. Only the Jackson 3 mapper is
 * actually on the HTTP request path, but both are fixed for consistency since either
 * could be handed a Kotlin data class with default parameters.
 */
@Configuration
class JacksonConfig {

    @Bean
    @ConditionalOnMissingBean
    fun objectMapper(): com.fasterxml.jackson.databind.ObjectMapper {
        val kotlinModule = com.fasterxml.jackson.module.kotlin.KotlinModule.Builder()
            .enable(com.fasterxml.jackson.module.kotlin.KotlinFeature.NullIsSameAsDefault)
            .build()
        return com.fasterxml.jackson.databind.json.JsonMapper.builder()
            .addModule(kotlinModule)
            .build()
            .apply {
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                findAndRegisterModules()
            }
    }

    @Bean
    @ConditionalOnMissingBean
    fun jsonMapperBuilderCustomizer(): JsonMapperBuilderCustomizer =
        JsonMapperBuilderCustomizer { builder: tools.jackson.databind.json.JsonMapper.Builder ->
            val kotlinModule = tools.jackson.module.kotlin.KotlinModule.Builder()
                .enable(tools.jackson.module.kotlin.KotlinFeature.NullIsSameAsDefault)
                .build()
            builder.addModule(kotlinModule)
        }
}
