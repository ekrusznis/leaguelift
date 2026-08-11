package com.rally26.integration.sportsdata.domain

import com.rally26.integration.core.domain.IntegrationProvider

/**
 * DESIGN-DOC.md section 14.1L §30.4 — the evidence-import capability tiers a sports-data
 * provider can claim to support. Distinct from [com.rally26.eligibility.domain.EvidenceClassification]
 * (the strength ladder an org's requirement accepts) — this describes what a *provider's API*
 * can supply at all, before any of it is trusted at a particular classification tier.
 */
enum class ProviderEligibilityCapability {
    ROSTER_IMPORT,
    GUARDIAN_IMPORT,
    REGISTRATION_STATUS_IMPORT,
    REGISTRATION_ANSWERS_IMPORT,
    WAIVER_ACKNOWLEDGMENT_IMPORT,
    DOCUMENT_METADATA_IMPORT,
    DOCUMENT_CONTENT_DOWNLOAD,
    PAYMENT_STATUS_IMPORT,
    ELIGIBILITY_IMPORT,
}

/**
 * DESIGN-DOC.md section 14.1L §30.5's provider-specific scope, encoded as data rather than
 * scattered `when` branches so a future commit-import step (still pending real TeamSnap/
 * SportsEngine developer-application activation, see V73/V76) has one place to check what a
 * provider is allowed to claim. Any provider absent from this map — including GameChanger,
 * which ADR-098 removed from the connectable catalog entirely for having no public API — has
 * no eligibility-evidence capability at all: roster/schedule only, via the existing reviewed
 * CSV import, never a waiver claim.
 */
object ProviderEligibilityCapabilities {
    private val capabilities: Map<IntegrationProvider, Set<ProviderEligibilityCapability>> =
        mapOf(
            IntegrationProvider.TEAMSNAP to
                setOf(
                    ProviderEligibilityCapability.ROSTER_IMPORT,
                    ProviderEligibilityCapability.GUARDIAN_IMPORT,
                    ProviderEligibilityCapability.WAIVER_ACKNOWLEDGMENT_IMPORT,
                    ProviderEligibilityCapability.DOCUMENT_METADATA_IMPORT,
                ),
            IntegrationProvider.SPORTSENGINE to
                setOf(
                    ProviderEligibilityCapability.ROSTER_IMPORT,
                    ProviderEligibilityCapability.GUARDIAN_IMPORT,
                    ProviderEligibilityCapability.REGISTRATION_STATUS_IMPORT,
                    ProviderEligibilityCapability.REGISTRATION_ANSWERS_IMPORT,
                    ProviderEligibilityCapability.WAIVER_ACKNOWLEDGMENT_IMPORT,
                    ProviderEligibilityCapability.DOCUMENT_METADATA_IMPORT,
                ),
        )

    fun forProvider(provider: IntegrationProvider): Set<ProviderEligibilityCapability> = capabilities[provider] ?: emptySet()
}
