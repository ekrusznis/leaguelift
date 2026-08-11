package com.rally26.integration.sportsdata.application

import com.rally26.common.error.ServiceUnavailableException
import com.rally26.config.IntegrationProperties
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.integration.sportsdata.domain.ProviderEligibilityCapability
import com.rally26.integration.sportsdata.domain.SportsDataEntityType
import com.rally26.integration.sportsdata.domain.SportsDataExternalRecord
import org.springframework.stereotype.Component

interface SportsDataProviderClient {
    fun supports(provider: IntegrationProvider): Boolean

    fun fetchSnapshot(
        provider: IntegrationProvider,
        accessToken: String,
    ): List<SportsDataExternalRecord>
}

@Component
class ScaffoldSportsEngineProviderClient(
    private val properties: IntegrationProperties,
) : SportsDataProviderClient {
    override fun supports(provider: IntegrationProvider): Boolean = provider == IntegrationProvider.SPORTSENGINE

    override fun fetchSnapshot(
        provider: IntegrationProvider,
        accessToken: String,
    ): List<SportsDataExternalRecord> {
        if (!supports(provider) || !properties.stubMode || !accessToken.startsWith("stub-access-")) {
            throw ServiceUnavailableException(
                "SPORTSENGINE_CLIENT_NOT_ACTIVATED",
                "SportsEngine is scaffolded but has not been activated against a verified current provider contract.",
            )
        }
        return listOf(
            SportsDataExternalRecord(
                SportsDataEntityType.ORGANIZATION,
                "se-org-1",
                null,
                "Rally26 Test Club",
                mapOf("sport" to "VOLLEYBALL"),
            ),
            SportsDataExternalRecord(SportsDataEntityType.TEAM, "se-team-1", "se-org-1", "16U National", mapOf("season" to "2026")),
            SportsDataExternalRecord(
                SportsDataEntityType.EVENT,
                "se-event-1",
                "se-team-1",
                "Tournament Match",
                mapOf(
                    "startAt" to "2026-09-12T14:00:00Z",
                ),
            ),
            // Phase 31 slice 31.3 — proves ELIGIBILITY_EVIDENCE flows through the same generic
            // preview pipeline as every other entity type. Parented to the team stub record since
            // this scaffold has no participant-level stub data yet; a real SportsEngine adapter
            // would parent to a roster-membership/participant external id instead.
            SportsDataExternalRecord(
                SportsDataEntityType.ELIGIBILITY_EVIDENCE,
                "se-eligibility-1",
                "se-team-1",
                "Registration Waiver Acknowledgment",
                mapOf(
                    "capability" to ProviderEligibilityCapability.WAIVER_ACKNOWLEDGMENT_IMPORT.name,
                    "classificationHint" to "EXTERNAL_ACKNOWLEDGMENT",
                ),
            ),
        )
    }
}

@Component
class ScaffoldTeamSnapProviderClient(
    private val properties: IntegrationProperties,
) : SportsDataProviderClient {
    override fun supports(provider: IntegrationProvider): Boolean = provider == IntegrationProvider.TEAMSNAP

    override fun fetchSnapshot(
        provider: IntegrationProvider,
        accessToken: String,
    ): List<SportsDataExternalRecord> {
        if (!supports(provider) || !properties.stubMode || !accessToken.startsWith("stub-access-")) {
            throw ServiceUnavailableException(
                "TEAMSNAP_CLIENT_NOT_ACTIVATED",
                "TeamSnap is scaffolded but has not been activated against a registered and verified developer application.",
            )
        }
        return listOf(
            SportsDataExternalRecord(
                SportsDataEntityType.ORGANIZATION,
                "ts-org-1",
                null,
                "Rally26 Test Club",
                mapOf("sport" to "SOCCER"),
            ),
            SportsDataExternalRecord(SportsDataEntityType.TEAM, "ts-team-1", "ts-org-1", "12U Coed", mapOf("season" to "2026")),
            SportsDataExternalRecord(
                SportsDataEntityType.EVENT,
                "ts-event-1",
                "ts-team-1",
                "League Match",
                mapOf(
                    "startAt" to "2026-09-19T15:00:00Z",
                ),
            ),
            // Phase 31 slice 31.3 — proves ELIGIBILITY_EVIDENCE flows through the same generic
            // preview pipeline as every other entity type. Parented to the team stub record since
            // this scaffold has no participant-level stub data yet; a real TeamSnap adapter would
            // parent to a roster-membership/participant external id instead.
            SportsDataExternalRecord(
                SportsDataEntityType.ELIGIBILITY_EVIDENCE,
                "ts-eligibility-1",
                "ts-team-1",
                "Registration Waiver Acknowledgment",
                mapOf(
                    "capability" to ProviderEligibilityCapability.WAIVER_ACKNOWLEDGMENT_IMPORT.name,
                    "classificationHint" to "EXTERNAL_ACKNOWLEDGMENT",
                ),
            ),
        )
    }
}
