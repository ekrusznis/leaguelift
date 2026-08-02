package com.leaguelift.integration.core.application

import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.config.IntegrationProperties
import com.leaguelift.integration.core.domain.IntegrationAdapterMode
import com.leaguelift.integration.core.domain.IntegrationConnection
import com.leaguelift.integration.core.domain.IntegrationOwnerType
import com.leaguelift.integration.core.domain.IntegrationProvider
import com.leaguelift.integration.core.domain.IntegrationProviderDefinition
import com.leaguelift.integration.core.domain.IntegrationReadiness
import com.leaguelift.integration.core.persistence.IntegrationConnectionRepository
import com.leaguelift.integration.core.persistence.IntegrationProviderRepository
import com.leaguelift.membership.application.MembershipService
import org.springframework.stereotype.Service
import java.util.UUID

data class IntegrationCatalogItem(
    val definition: IntegrationProviderDefinition,
    val readiness: IntegrationReadiness,
    val connection: IntegrationConnection?,
    val stub: Boolean,
)

@Service
class IntegrationCatalogService(
    private val providerRepository: IntegrationProviderRepository,
    private val connectionRepository: IntegrationConnectionRepository,
    private val membershipService: MembershipService,
    private val properties: IntegrationProperties,
    private val adapterRegistry: IntegrationAdapterRegistry,
) {
    fun listForOrganization(organizationId: UUID, currentUser: CurrentUser): List<IntegrationCatalogItem> {
        membershipService.requireManagerRole(organizationId, currentUser)
        val connections = connectionRepository.listForOrganization(organizationId)
            .groupBy { it.provider }
            .mapValues { (_, values) -> values.maxBy { it.updatedAt } }
        return providerRepository.list(IntegrationOwnerType.ORGANIZATION)
            .map { definition -> item(definition, connections[definition.provider]) }
    }

    fun listForUser(currentUser: CurrentUser): List<IntegrationCatalogItem> {
        val connections = connectionRepository.listForUser(currentUser.userId)
            .groupBy { it.provider }
            .mapValues { (_, values) -> values.maxBy { it.updatedAt } }
        return providerRepository.list(IntegrationOwnerType.USER)
            .map { definition -> item(definition, connections[definition.provider]) }
    }

    fun requireDefinition(provider: IntegrationProvider): IntegrationProviderDefinition =
        providerRepository.find(provider)
            ?: throw NotFoundException("INTEGRATION_PROVIDER_NOT_FOUND", "The integration provider could not be found.")

    fun readiness(definition: IntegrationProviderDefinition): IntegrationReadiness = when {
        definition.baselineReadiness == IntegrationReadiness.PARTNER_PENDING -> IntegrationReadiness.PARTNER_PENDING
        definition.baselineReadiness == IntegrationReadiness.UNSUPPORTED -> IntegrationReadiness.UNSUPPORTED
        definition.baselineReadiness == IntegrationReadiness.PLATFORM_MANAGED -> IntegrationReadiness.PLATFORM_MANAGED
        definition.adapterMode == IntegrationAdapterMode.EXISTING -> IntegrationReadiness.AVAILABLE
        definition.adapterMode == IntegrationAdapterMode.FILE_IMPORT -> IntegrationReadiness.AVAILABLE
        else -> {
            val runtime = properties.provider(definition.provider)
            val adapterReady = adapterRegistry.find(definition.provider) != null
            val oauthConfigured = properties.stubMode || (
                runtime.clientId.isNotBlank() && runtime.clientSecret.isNotBlank() &&
                    runtime.authorizationUri.isNotBlank() && runtime.tokenUri.isNotBlank()
                )
            if (runtime.enabled && adapterReady && oauthConfigured) IntegrationReadiness.AVAILABLE
            else IntegrationReadiness.NOT_CONFIGURED
        }
    }

    private fun item(definition: IntegrationProviderDefinition, connection: IntegrationConnection?) =
        IntegrationCatalogItem(
            definition = definition,
            readiness = readiness(definition),
            connection = connection,
            stub = properties.stubMode && adapterRegistry.find(definition.provider) != null,
        )
}
