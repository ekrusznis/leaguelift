package com.leaguelift.integration.core.persistence

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.leaguelift.integration.core.domain.IntegrationAdapterMode
import com.leaguelift.integration.core.domain.IntegrationAuthMode
import com.leaguelift.integration.core.domain.IntegrationCategory
import com.leaguelift.integration.core.domain.IntegrationOwnerType
import com.leaguelift.integration.core.domain.IntegrationProvider
import com.leaguelift.integration.core.domain.IntegrationProviderDefinition
import com.leaguelift.integration.core.domain.IntegrationReadiness
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class IntegrationProviderRepository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper,
) {
    fun find(provider: IntegrationProvider): IntegrationProviderDefinition? =
        jdbcClient.sql("select $COLUMNS from integration_provider_catalog where provider = :provider")
            .param("provider", provider.name)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun list(ownerType: IntegrationOwnerType, customerVisibleOnly: Boolean = true): List<IntegrationProviderDefinition> {
        val visibleClause = if (customerVisibleOnly) "and visible_to_customers = true" else ""
        return jdbcClient.sql(
            """
            select $COLUMNS
            from integration_provider_catalog
            where ownership_scope = :ownerType $visibleClause
            order by sort_order, display_name
            """.trimIndent(),
        )
            .param("ownerType", ownerType.name)
            .query(::mapRow)
            .list()
    }

    private fun mapRow(rs: java.sql.ResultSet, rowNum: Int) = IntegrationProviderDefinition(
        provider = IntegrationProvider.valueOf(rs.getString("provider")),
        displayName = rs.getString("display_name"),
        category = IntegrationCategory.valueOf(rs.getString("category")),
        ownershipScope = IntegrationOwnerType.valueOf(rs.getString("ownership_scope")),
        primaryAuthMode = IntegrationAuthMode.valueOf(rs.getString("primary_auth_mode")),
        supportedAuthModes = readStrings(rs.getString("supported_auth_modes")).map(IntegrationAuthMode::valueOf),
        baselineReadiness = IntegrationReadiness.valueOf(rs.getString("baseline_readiness")),
        adapterMode = IntegrationAdapterMode.valueOf(rs.getString("adapter_mode")),
        description = rs.getString("description"),
        activationRequirement = rs.getString("activation_requirement"),
        defaultScopes = readStrings(rs.getString("default_scopes")),
        sortOrder = rs.getInt("sort_order"),
        visibleToCustomers = rs.getBoolean("visible_to_customers"),
    )

    private fun readStrings(json: String): List<String> =
        objectMapper.readValue(json, object : TypeReference<List<String>>() {})

    private companion object {
        const val COLUMNS = "provider, display_name, category, ownership_scope, primary_auth_mode, supported_auth_modes, baseline_readiness, adapter_mode, description, activation_requirement, default_scopes, sort_order, visible_to_customers"
    }
}
