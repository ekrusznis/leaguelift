package com.leaguelift.store.integration

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.identity.application.PasswordAuthenticationService
import com.leaguelift.integration.printify.infra.PrintifyDraftOrder
import com.leaguelift.integration.printify.infra.PrintifyImageClient
import com.leaguelift.integration.printify.infra.PrintifyOrderClient
import com.leaguelift.integration.printify.infra.PrintifyProductClient
import com.leaguelift.integration.printify.infra.PrintifyProductResult
import com.leaguelift.integration.printify.infra.PrintifyProductVariantCost
import com.leaguelift.integration.printify.infra.PrintifyUploadedImage
import com.leaguelift.media.domain.MediaAssetStatus
import com.leaguelift.media.domain.MediaEntityType
import com.leaguelift.media.domain.MediaUsageSlot
import com.leaguelift.media.domain.PublicationStatus
import com.leaguelift.media.domain.Visibility
import com.leaguelift.media.persistence.MediaAssetRepository
import com.leaguelift.media.persistence.MediaAssignmentRepository
import com.leaguelift.order.application.OrderLineItemRequest
import com.leaguelift.order.application.OrderService
import com.leaguelift.order.domain.FulfillmentStatus
import com.leaguelift.order.infra.OrderCheckoutSession
import com.leaguelift.order.infra.StripeOrderCheckoutClient
import com.leaguelift.organization.application.OrganizationService
import com.leaguelift.organization.domain.OrganizationType
import com.leaguelift.store.application.ProductService
import com.leaguelift.store.application.StoreService
import com.leaguelift.store.domain.CatalogSource
import com.leaguelift.store.domain.ProductStatus
import com.leaguelift.store.domain.StoreStatus
import com.leaguelift.testsupport.AbstractIntegrationTest
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Full store -> product -> variant (real Printify cost-discovery call, mocked) ->
 * checkout (mocked Stripe) -> webhook confirmation -> Printify draft-order
 * submission (mocked) flow against real Postgres. Mirrors
 * `fundraising/integration/ContributionIntegrationTest.kt`'s pattern — external
 * providers mocked via @MockkBean, everything else (org isolation, status
 * transitions, idempotency) exercised for real.
 */
class StoreOrderIntegrationTest : AbstractIntegrationTest() {

	@Autowired
	lateinit var organizationService: OrganizationService

	@Autowired
	lateinit var passwordAuthenticationService: PasswordAuthenticationService

	@Autowired
	lateinit var storeService: StoreService

	@Autowired
	lateinit var productService: ProductService

	@Autowired
	lateinit var orderService: OrderService

	@Autowired
	lateinit var mediaAssetRepository: MediaAssetRepository

	@Autowired
	lateinit var mediaAssignmentRepository: MediaAssignmentRepository

	@MockkBean
	lateinit var printifyImageClient: PrintifyImageClient

	@MockkBean
	lateinit var printifyProductClient: PrintifyProductClient

	@MockkBean
	lateinit var printifyOrderClient: PrintifyOrderClient

	@MockkBean
	lateinit var stripeOrderCheckoutClient: StripeOrderCheckoutClient

	@Test
	fun `a confirmed order snapshots real Printify cost, transitions PENDING to CONFIRMED once, and submits a draft fulfillment`() {
		val owner = registerUser("store-owner")
		val organization = organizationService.create(
			"Riverside Soccer", "riverside-soccer-store-${System.nanoTime()}", OrganizationType.RECREATIONAL_LEAGUE, owner,
		)
		val store = storeService.create(organization.id, null, "Spring Store", "spring-store-${System.nanoTime()}", owner)
		storeService.updateStatus(organization.id, store.id, StoreStatus.ACTIVE, owner)

		val product = productService.create(
			organization.id, store.id, "Team Hoodie", null, CatalogSource.PRINTIFY, null, 12L, "front", owner,
		)
		assignReadyDesign(organization.id, product.id, owner)

		every { printifyProductClient.createProduct("Team Hoodie", 12L, 5L, listOf(100L), 2500L, any(), "front") } returns
			PrintifyProductResult("printify_product_1", listOf(PrintifyProductVariantCost(100L, costMinor = 1200L, priceMinor = 2500L)))

		val variant = productService.createVariant(organization.id, product.id, 5L, 100L, "M / Navy", 2500L, owner)
		assertEquals(1200L, variant.costMinor, "cost must be Printify's real returned value, never guessed")
		productService.updateStatus(organization.id, product.id, ProductStatus.ACTIVE, owner)

		val fixedSessionId = "cs_test_${System.nanoTime()}"
		every { stripeOrderCheckoutClient.createOrderCheckoutSession(any(), any(), any(), any()) } returns
			OrderCheckoutSession(fixedSessionId, "https://checkout.stripe.com/test")

		orderService.createCheckoutSession(
			store.slug, listOf(OrderLineItemRequest(variant.id, 2)), "Jane Doe", "jane@example.com",
			"https://app.local/success", "https://app.local/cancel",
		)

		every { printifyOrderClient.createDraftOrder(any(), any()) } returns PrintifyDraftOrder("printify_order_1")

		val confirmed = orderService.confirmFromWebhook(fixedSessionId, "paid", null, "pi_test_${System.nanoTime()}")
		assertEquals("CONFIRMED", confirmed?.status?.name)

		val fulfillment = orderService.getFulfillment(organization.id, confirmed!!.id, owner)
		assertEquals(FulfillmentStatus.DRAFT_CREATED, fulfillment?.status)
		assertEquals("printify_order_1", fulfillment?.printifyOrderId)

		// A replayed webhook must not resubmit fulfillment or double-process the order.
		val replayed = orderService.confirmFromWebhook(fixedSessionId, "paid", null, "pi_test_${System.nanoTime()}")
		assertEquals("CONFIRMED", replayed?.status?.name)

		val confirmedOrders = orderService.listForStore(organization.id, store.id, owner, 0, 20)
		assertEquals(1, confirmedOrders.size)
	}

	/** Bypasses the real upload/S3-confirm dance (already covered by the media module's own integration tests) — inserts a READY asset + PUBLIC assignment directly, since this test's focus is store/order commerce logic. */
	private fun assignReadyDesign(organizationId: UUID, productId: UUID, owner: CurrentUser) {
		val assetId = UUID.randomUUID()
		mediaAssetRepository.insert(assetId, organizationId, owner.userId, MediaUsageSlot.PRODUCT_DESIGN, "design.png", "image/png", "organizations/$organizationId/media/$assetId/original.png")
		mediaAssetRepository.markConfirmed(assetId, organizationId, MediaAssetStatus.READY, "image/png", 1024L, "checksum", 500, 500, null)
		mediaAssignmentRepository.insert(
			organizationId = organizationId, assetId = assetId, entityType = MediaEntityType.PRODUCT, entityId = productId,
			usageSlot = MediaUsageSlot.PRODUCT_DESIGN, publicationStatus = PublicationStatus.PUBLISHED, visibility = Visibility.PUBLIC, altText = null,
		)
		every { printifyImageClient.uploadImage(any(), any()) } returns PrintifyUploadedImage("printify_img_1", "design.png")
	}

	private fun registerUser(prefix: String): CurrentUser {
		val appUser = passwordAuthenticationService.register("$prefix-${System.nanoTime()}@example.com", "password1234", "Test User")
		return passwordAuthenticationService.toCurrentUser(appUser)
	}
}
