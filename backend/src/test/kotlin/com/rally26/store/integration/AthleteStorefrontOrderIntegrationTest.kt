package com.rally26.store.integration

import com.ninjasquad.springmockk.MockkBean
import com.rally26.common.web.CurrentUser
import com.rally26.credit.domain.CreditSourceType
import com.rally26.credit.persistence.FamilyCreditGrantRepository
import com.rally26.household.application.HouseholdService
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.media.domain.MediaAssetStatus
import com.rally26.media.domain.MediaEntityType
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.media.domain.PublicationStatus
import com.rally26.media.domain.Visibility
import com.rally26.media.persistence.MediaAssetRepository
import com.rally26.media.persistence.MediaAssignmentRepository
import com.rally26.order.application.OrderService
import com.rally26.order.domain.FulfillmentStatus
import com.rally26.order.infra.OrderCheckoutSession
import com.rally26.order.infra.StripeOrderCheckoutClient
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.Organization
import com.rally26.organization.domain.OrganizationType
import com.rally26.participant.application.ParticipantService
import com.rally26.store.application.AthleteStorefrontService
import com.rally26.store.application.ProductService
import com.rally26.store.application.StoreService
import com.rally26.store.domain.CatalogSource
import com.rally26.store.domain.ProductStatus
import com.rally26.store.domain.StoreStatus
import com.rally26.team.application.TeamService
import com.rally26.testsupport.AbstractIntegrationTest
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Full athlete-storefront path against real Postgres (Phase 24 slice 24.3): roster
 * validation, publish, public checkout, webhook confirmation, real household
 * attribution snapshotted onto the order, and a real Phase 23 family-credit grant
 * created only after authoritative payment confirmation. Mirrors
 * `StoreOrderIntegrationTest`'s pattern exactly — only Stripe is mocked; a MANUAL
 * product/variant is used deliberately so Printify is never involved, keeping this
 * test focused on storefront/attribution/credit logic rather than catalog cost
 * discovery (already covered by `StoreOrderIntegrationTest`).
 */
class AthleteStorefrontOrderIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var organizationService: OrganizationService

    @Autowired
    lateinit var passwordAuthenticationService: PasswordAuthenticationService

    @Autowired
    lateinit var teamService: TeamService

    @Autowired
    lateinit var householdService: HouseholdService

    @Autowired
    lateinit var participantService: ParticipantService

    @Autowired
    lateinit var storeService: StoreService

    @Autowired
    lateinit var productService: ProductService

    @Autowired
    lateinit var athleteStorefrontService: AthleteStorefrontService

    @Autowired
    lateinit var orderService: OrderService

    @Autowired
    lateinit var familyCreditGrantRepository: FamilyCreditGrantRepository

    @Autowired
    lateinit var mediaAssetRepository: MediaAssetRepository

    @Autowired
    lateinit var mediaAssignmentRepository: MediaAssignmentRepository

    @MockkBean
    lateinit var stripeOrderCheckoutClient: StripeOrderCheckoutClient

    @Test
    fun `a confirmed public storefront order attributes the household and grants real family credit`() {
        val owner = registerUser("storefront-owner")
        val organization = createOrganization(owner)
        val team = teamService.create(organization.id, "Riverside U10", "Soccer", null, null, owner)
        val household = householdService.create(organization.id, "The Johnson Family", "sarah@example.com", null, null, owner)
        val participant = participantService.create(organization.id, household.id, "Maya", "Johnson", null, null, owner)
        participantService.assignToTeam(organization.id, participant.id, team.id, null, owner)

        val store = storeService.create(organization.id, team.id, "Riverside Swag Shop", "riverside-swag-${System.nanoTime()}", owner)
        storeService.updateStatus(organization.id, store.id, StoreStatus.ACTIVE, owner)
        val product =
            productService.create(organization.id, store.id, "Youth Hoodie", null, CatalogSource.MANUAL, null, null, "front", owner)
        val variant =
            productService.createManualVariant(organization.id, product.id, "Youth M", null, "M", "Navy", "USD", 1200L, 2500L, owner)
        assignReadyDesign(organization.id, product.id, owner)
        productService.updateStatus(organization.id, product.id, ProductStatus.ACTIVE, owner)

        val slug = "maya-johnson-${System.nanoTime()}"
        val storefront =
            athleteStorefrontService.create(organization.id, team.id, participant.id, store.id, listOf(product.id), slug, owner)
        athleteStorefrontService.publish(organization.id, storefront.id, owner)

        val fixedSessionId = "cs_test_${System.nanoTime()}"
        every { stripeOrderCheckoutClient.createOrderCheckoutSession(any(), any(), any(), any()) } returns
            OrderCheckoutSession(fixedSessionId, "https://checkout.stripe.com/test")

        orderService.createAthleteStorefrontCheckoutSession(slug, variant.id, null, null, null, null, "Jane Doe", "jane@example.com")

        val confirmed = orderService.confirmFromWebhook(fixedSessionId, "paid", null, "pi_test_${System.nanoTime()}")

        assertEquals("CONFIRMED", confirmed?.status?.name)
        assertEquals(
            household.id,
            confirmed?.attributedHouseholdId,
            "the order must snapshot the athlete's real household, not a code/link lookup",
        )

        val grant = familyCreditGrantRepository.findBySource(organization.id, CreditSourceType.STOREFRONT_ATTRIBUTION, confirmed!!.id)
        assertNotNull(grant, "a real family_credit_grant row must exist for this storefront order")
        assertEquals(250L, grant.remainingMinor, "10% default credit percent of the 2500 minor-unit order total")
        assertEquals(household.id, grant.householdId)

        val fulfillment = orderService.getFulfillment(organization.id, confirmed.id, owner)
        assertEquals(FulfillmentStatus.READY, fulfillment?.status, "a manual product should be READY immediately with no Printify call")

        // A replayed webhook must not double-grant credit.
        orderService.confirmFromWebhook(fixedSessionId, "paid", null, "pi_test_replay")
        val grantsAfterReplay = familyCreditGrantRepository.listForHousehold(organization.id, household.id)
        assertEquals(1, grantsAfterReplay.size, "confirmation must be idempotent — no duplicate grant on a replayed webhook")
    }

    @Test
    fun `refunding a storefront order reverses the family credit grant`() {
        val owner = registerUser("storefront-refund-owner")
        val organization = createOrganization(owner)
        val team = teamService.create(organization.id, "Riverside U12", "Soccer", null, null, owner)
        val household = householdService.create(organization.id, "The Alvarez Family", "carlos@example.com", null, null, owner)
        val participant = participantService.create(organization.id, household.id, "Diego", "Alvarez", null, null, owner)
        participantService.assignToTeam(organization.id, participant.id, team.id, null, owner)

        val store =
            storeService.create(
                organization.id,
                team.id,
                "Riverside Swag Shop",
                "riverside-swag-refund-${System.nanoTime()}",
                owner,
            )
        storeService.updateStatus(organization.id, store.id, StoreStatus.ACTIVE, owner)
        val product =
            productService.create(organization.id, store.id, "Youth Tee", null, CatalogSource.MANUAL, null, null, "front", owner)
        val variant =
            productService.createManualVariant(organization.id, product.id, "Youth M", null, "M", "Navy", "USD", 800L, 2000L, owner)
        assignReadyDesign(organization.id, product.id, owner)
        productService.updateStatus(organization.id, product.id, ProductStatus.ACTIVE, owner)

        val slug = "diego-alvarez-${System.nanoTime()}"
        val storefront =
            athleteStorefrontService.create(organization.id, team.id, participant.id, store.id, listOf(product.id), slug, owner)
        athleteStorefrontService.publish(organization.id, storefront.id, owner)

        val fixedSessionId = "cs_test_${System.nanoTime()}"
        every { stripeOrderCheckoutClient.createOrderCheckoutSession(any(), any(), any(), any()) } returns
            OrderCheckoutSession(fixedSessionId, "https://checkout.stripe.com/test")
        every { stripeOrderCheckoutClient.createRefund(any()) } returns "re_test_${System.nanoTime()}"

        orderService.createAthleteStorefrontCheckoutSession(slug, variant.id, null, null, null, null, null, null)
        val confirmed = orderService.confirmFromWebhook(fixedSessionId, "paid", null, "pi_test_${System.nanoTime()}")!!

        val grantBeforeRefund =
            familyCreditGrantRepository.findBySource(organization.id, CreditSourceType.STOREFRONT_ATTRIBUTION, confirmed.id)
        assertNotNull(grantBeforeRefund)
        assertEquals("AVAILABLE", grantBeforeRefund.status.name)

        orderService.refund(organization.id, confirmed.id, owner)

        val grantAfterRefund =
            familyCreditGrantRepository.findBySource(organization.id, CreditSourceType.STOREFRONT_ATTRIBUTION, confirmed.id)
        assertNotNull(grantAfterRefund)
        assertEquals("REVOKED", grantAfterRefund.status.name)
        assertEquals(0L, grantAfterRefund.remainingMinor)
    }

    /** Bypasses the real upload/S3-confirm dance (already covered by the media module's own integration tests) — inserts a READY asset + PUBLIC assignment directly, mirroring `StoreOrderIntegrationTest.assignReadyDesign`. */
    private fun assignReadyDesign(
        organizationId: UUID,
        productId: UUID,
        owner: CurrentUser,
    ) {
        val assetId = UUID.randomUUID()
        mediaAssetRepository.insert(
            assetId,
            organizationId,
            owner.userId,
            MediaUsageSlot.PRODUCT_DESIGN,
            "design.png",
            "image/png",
            "organizations/$organizationId/media/$assetId/original.png",
        )
        mediaAssetRepository.markConfirmed(assetId, organizationId, MediaAssetStatus.READY, "image/png", 1024L, "checksum", 500, 500, null)
        mediaAssignmentRepository.insert(
            organizationId = organizationId,
            assetId = assetId,
            entityType = MediaEntityType.PRODUCT,
            entityId = productId,
            usageSlot = MediaUsageSlot.PRODUCT_DESIGN,
            publicationStatus = PublicationStatus.PUBLISHED,
            visibility = Visibility.PUBLIC,
            altText = null,
        )
    }

    private fun registerUser(prefix: String): CurrentUser {
        val appUser = passwordAuthenticationService.register("$prefix-${System.nanoTime()}@example.com", "password1234", "Test User")
        return passwordAuthenticationService.toCurrentUser(appUser)
    }

    private fun createOrganization(owner: CurrentUser): Organization =
        organizationService.create(
            "Riverside Soccer",
            "riverside-soccer-storefront-${System.nanoTime()}",
            OrganizationType.RECREATIONAL_LEAGUE,
            owner,
        )
}
