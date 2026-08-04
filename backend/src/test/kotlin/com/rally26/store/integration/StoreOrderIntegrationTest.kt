package com.rally26.store.integration

import com.rally26.common.web.CurrentUser
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.integration.printify.infra.PrintifyDraftOrder
import com.rally26.integration.printify.infra.PrintifyImageClient
import com.rally26.integration.printify.infra.PrintifyOrderClient
import com.rally26.integration.printify.infra.PrintifyProductClient
import com.rally26.integration.printify.infra.PrintifyProductResult
import com.rally26.integration.printify.infra.PrintifyProductVariantCost
import com.rally26.integration.printify.infra.PrintifyUploadedImage
import com.rally26.media.domain.MediaAssetStatus
import com.rally26.media.domain.MediaEntityType
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.media.domain.PublicationStatus
import com.rally26.media.domain.Visibility
import com.rally26.media.persistence.MediaAssetRepository
import com.rally26.media.persistence.MediaAssignmentRepository
import com.rally26.order.application.OrderLineItemRequest
import com.rally26.order.application.OrderService
import com.rally26.order.domain.FulfillmentStatus
import com.rally26.order.infra.OrderCheckoutSession
import com.rally26.order.infra.StripeOrderCheckoutClient
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.OrganizationType
import com.rally26.store.application.ProductService
import com.rally26.store.application.StoreService
import com.rally26.store.domain.CatalogSource
import com.rally26.store.domain.ProductStatus
import com.rally26.store.domain.StoreStatus
import com.rally26.testsupport.AbstractIntegrationTest
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
