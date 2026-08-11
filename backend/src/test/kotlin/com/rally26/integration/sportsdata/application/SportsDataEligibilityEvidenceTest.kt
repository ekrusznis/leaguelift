package com.rally26.integration.sportsdata.application

import com.rally26.common.error.ServiceUnavailableException
import com.rally26.config.IntegrationProperties
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.integration.sportsdata.domain.ProviderEligibilityCapabilities
import com.rally26.integration.sportsdata.domain.ProviderEligibilityCapability
import com.rally26.integration.sportsdata.domain.SportsDataEntityType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Phase 31 slice 31.3 — provider eligibility-evidence discovery, still preview-only pending real OAuth activation (V73/V76). */
class SportsDataEligibilityEvidenceTest {
    private val stubProperties = IntegrationProperties(stubMode = true)

    @Test
    fun `TeamSnap scaffold emits an eligibility-evidence stub record parented to its team stub`() {
        val client = ScaffoldTeamSnapProviderClient(stubProperties)

        val records = client.fetchSnapshot(IntegrationProvider.TEAMSNAP, "stub-access-teamsnap")

        val evidence = records.single { it.entityType == SportsDataEntityType.ELIGIBILITY_EVIDENCE }
        assertEquals("ts-team-1", evidence.externalParentId)
        assertEquals(ProviderEligibilityCapability.WAIVER_ACKNOWLEDGMENT_IMPORT.name, evidence.payload["capability"])
    }

    @Test
    fun `SportsEngine scaffold emits an eligibility-evidence stub record parented to its team stub`() {
        val client = ScaffoldSportsEngineProviderClient(stubProperties)

        val records = client.fetchSnapshot(IntegrationProvider.SPORTSENGINE, "stub-access-sportsengine")

        val evidence = records.single { it.entityType == SportsDataEntityType.ELIGIBILITY_EVIDENCE }
        assertEquals("se-team-1", evidence.externalParentId)
        assertEquals(ProviderEligibilityCapability.WAIVER_ACKNOWLEDGMENT_IMPORT.name, evidence.payload["capability"])
    }

    @Test
    fun `an unactivated TeamSnap client still refuses non-stub access tokens`() {
        val client = ScaffoldTeamSnapProviderClient(stubProperties)

        assertFailsWith<ServiceUnavailableException> {
            client.fetchSnapshot(IntegrationProvider.TEAMSNAP, "real-access-token")
        }
    }

    @Test
    fun `TeamSnap and SportsEngine both claim waiver-acknowledgment and document-metadata capabilities`() {
        val teamSnap = ProviderEligibilityCapabilities.forProvider(IntegrationProvider.TEAMSNAP)
        val sportsEngine = ProviderEligibilityCapabilities.forProvider(IntegrationProvider.SPORTSENGINE)

        assertTrue(ProviderEligibilityCapability.WAIVER_ACKNOWLEDGMENT_IMPORT in teamSnap)
        assertTrue(ProviderEligibilityCapability.DOCUMENT_METADATA_IMPORT in teamSnap)
        assertTrue(ProviderEligibilityCapability.WAIVER_ACKNOWLEDGMENT_IMPORT in sportsEngine)
        assertTrue(ProviderEligibilityCapability.REGISTRATION_STATUS_IMPORT in sportsEngine)
    }

    @Test
    fun `a provider with no registered eligibility scope has no capabilities at all`() {
        assertEquals(emptySet(), ProviderEligibilityCapabilities.forProvider(IntegrationProvider.GOOGLE_CALENDAR))
    }
}
