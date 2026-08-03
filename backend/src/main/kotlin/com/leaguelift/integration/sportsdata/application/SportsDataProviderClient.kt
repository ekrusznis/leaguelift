package com.leaguelift.integration.sportsdata.application

import com.leaguelift.common.error.ServiceUnavailableException
import com.leaguelift.config.IntegrationProperties
import com.leaguelift.integration.core.domain.IntegrationProvider
import com.leaguelift.integration.sportsdata.domain.SportsDataEntityType
import com.leaguelift.integration.sportsdata.domain.SportsDataExternalRecord
import org.springframework.stereotype.Component

interface SportsDataProviderClient {
    fun supports(provider: IntegrationProvider): Boolean
    fun fetchSnapshot(provider: IntegrationProvider, accessToken: String): List<SportsDataExternalRecord>
}

@Component
class ScaffoldSportsEngineProviderClient(private val properties: IntegrationProperties) : SportsDataProviderClient {
    override fun supports(provider: IntegrationProvider): Boolean = provider == IntegrationProvider.SPORTSENGINE

    override fun fetchSnapshot(provider: IntegrationProvider, accessToken: String): List<SportsDataExternalRecord> {
        if (!supports(provider) || !properties.stubMode || !accessToken.startsWith("stub-access-")) {
            throw ServiceUnavailableException(
                "SPORTSENGINE_CLIENT_NOT_ACTIVATED",
                "SportsEngine is scaffolded but has not been activated against a verified current provider contract.",
            )
        }
        return listOf(
            SportsDataExternalRecord(SportsDataEntityType.ORGANIZATION, "se-org-1", null, "LeagueLift Test Club", mapOf("sport" to "VOLLEYBALL")),
            SportsDataExternalRecord(SportsDataEntityType.TEAM, "se-team-1", "se-org-1", "16U National", mapOf("season" to "2026")),
            SportsDataExternalRecord(SportsDataEntityType.EVENT, "se-event-1", "se-team-1", "Tournament Match", mapOf("startAt" to "2026-09-12T14:00:00Z")),
        )
    }
}
