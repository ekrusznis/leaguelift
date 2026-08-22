package com.rally26.social

import com.rally26.fundraising.application.CampaignService
import com.rally26.fundraising.domain.CampaignType
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.OrganizationType
import com.rally26.social.application.SocialDraftService
import com.rally26.social.domain.SocialDraftSourceType
import com.rally26.testsupport.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Social Sharing & Connected Accounts, Slice 3 — real-Postgres proof of the fundraiser draft end to end. */
class SocialDraftIntegrationTest : AbstractIntegrationTest() {
    @Autowired lateinit var passwordAuthenticationService: PasswordAuthenticationService

    @Autowired lateinit var organizationService: OrganizationService

    @Autowired lateinit var campaignService: CampaignService

    @Autowired lateinit var socialDraftService: SocialDraftService

    @Autowired lateinit var jdbcClient: JdbcClient

    private fun registerUser(prefix: String) =
        passwordAuthenticationService.toCurrentUser(
            passwordAuthenticationService.register("$prefix-${System.nanoTime()}@example.com", "password1234", prefix),
        )

    @Test
    fun `a real fundraiser draft reflects the real confirmed total and links to the real public campaign page`() {
        val owner = registerUser("draftOwner")
        val organization =
            organizationService.create("Draft Test Org", "draft-test-org-${System.nanoTime()}", OrganizationType.TRAVEL_CLUB, owner)
        val campaign =
            campaignService.create(
                organizationId = organization.id,
                teamId = null,
                name = "12U National",
                slug = "12u-national-${System.nanoTime()}",
                description = null,
                campaignType = CampaignType.TEAM_GENERAL,
                goalAmountMinor = 500000,
                currency = "USD",
                startDate = null,
                endDate = null,
                currentUser = owner,
            )
        // Simulates a real confirmed Stripe contribution — ContributionService.confirmFromWebhook
        // is exercised by its own tests; this only needs a real CONFIRMED row to exist.
        jdbcClient
            .sql(
                """
                insert into contribution (id, organization_id, campaign_id, amount_minor, currency, payment_source, status, confirmed_at, created_at)
                values (:id, :orgId, :campaignId, 385000, 'USD', 'OFFLINE', 'CONFIRMED', :now, :now)
                """.trimIndent(),
            ).param("id", UUID.randomUUID())
            .param("orgId", organization.id)
            .param("campaignId", campaign.id)
            .param("now", java.sql.Timestamp.from(Instant.now()))
            .update()

        val draft = socialDraftService.createDraft(organization.id, SocialDraftSourceType.FUNDRAISER, campaign.id, owner)

        // application-test.yml doesn't override rally26.frontend.base-url, so this is
        // FrontendProperties' Kotlin default (http://localhost:5173), not the real
        // production domain — the point here is the *shape* (base + /campaigns/{slug}),
        // not the literal host.
        assertEquals("http://localhost:5173/campaigns/${campaign.slug}", draft.publicUrl)
        assertTrue(draft.caption.contains("$3,850.00 of $5,000.00 raised"))
        assertTrue(draft.caption.contains(draft.publicUrl))
        assertEquals(listOf(IntegrationProvider.INSTAGRAM, IntegrationProvider.FACEBOOK, IntegrationProvider.X), draft.allowedProviders)

        val fetched = socialDraftService.findForUser(draft.id, owner)
        assertEquals(draft.id, fetched.id)
    }
}
