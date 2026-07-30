package com.leaguelift.order.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.integration.printify.infra.PrintifyDraftOrder
import com.leaguelift.integration.printify.infra.PrintifyOrderClient
import com.leaguelift.ledger.application.LedgerService
import com.leaguelift.ledger.domain.LedgerSourceType
import com.leaguelift.media.application.MediaAssignmentService
import com.leaguelift.media.application.MediaReadService
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.membership.domain.MembershipStatus
import com.leaguelift.membership.domain.OrganizationMembership
import com.leaguelift.order.domain.Order
import com.leaguelift.order.domain.OrderItem
import com.leaguelift.order.domain.OrderStatus
import com.leaguelift.order.domain.FulfillmentStatus
import com.leaguelift.order.infra.OrderCheckoutSession
import com.leaguelift.order.infra.StripeOrderCheckoutClient
import com.leaguelift.order.persistence.FulfillmentRepository
import com.leaguelift.order.persistence.OrderItemRepository
import com.leaguelift.order.persistence.OrderRepository
import com.leaguelift.outbox.application.OutboxWriter
import com.leaguelift.store.domain.Product
import com.leaguelift.store.domain.ProductStatus
import com.leaguelift.store.domain.ProductVariant
import com.leaguelift.store.domain.Store
import com.leaguelift.store.domain.StoreStatus
import com.leaguelift.store.persistence.ProductRepository
import com.leaguelift.store.persistence.ProductVariantRepository
import com.leaguelift.store.persistence.StoreRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OrderServiceTest {

	private val orderRepository = mockk<OrderRepository>()
	private val orderItemRepository = mockk<OrderItemRepository>()
	private val fulfillmentRepository = mockk<FulfillmentRepository>()
	private val storeRepository = mockk<StoreRepository>()
	private val productRepository = mockk<ProductRepository>()
	private val productVariantRepository = mockk<ProductVariantRepository>()
	private val stripeOrderCheckoutClient = mockk<StripeOrderCheckoutClient>()
	private val printifyOrderClient = mockk<PrintifyOrderClient>()
	private val mediaAssignmentService = mockk<MediaAssignmentService>()
	private val mediaReadService = mockk<MediaReadService>()
	private val membershipService = mockk<MembershipService>()
	private val auditService = mockk<AuditService>()
	private val ledgerService = mockk<LedgerService>()
	private val outboxWriter = mockk<OutboxWriter>()
	private val service = OrderService(
		orderRepository, orderItemRepository, fulfillmentRepository, storeRepository, productRepository,
		productVariantRepository, stripeOrderCheckoutClient, printifyOrderClient, mediaAssignmentService,
		mediaReadService, membershipService, auditService, ledgerService, outboxWriter, ObjectMapper(),
	)

	private val orgId = UUID.randomUUID()

	@Test
	fun `createCheckoutSession rejects an empty item list`() {
		assertFailsWith<ValidationException> {
			service.createCheckoutSession("spring-store", emptyList(), null, null, "https://x/success", "https://x/cancel")
		}
	}

	@Test
	fun `createCheckoutSession throws NotFoundException for an unknown store slug`() {
		every { storeRepository.findBySlug("nope") } returns null

		assertFailsWith<NotFoundException> {
			service.createCheckoutSession("nope", listOf(OrderLineItemRequest(UUID.randomUUID(), 1)), null, null, "https://x/success", "https://x/cancel")
		}
	}

	@Test
	fun `createCheckoutSession rejects a store that isn't ACTIVE`() {
		every { storeRepository.findBySlug("spring-store") } returns store(status = StoreStatus.DRAFT)

		assertFailsWith<ValidationException> {
			service.createCheckoutSession("spring-store", listOf(OrderLineItemRequest(UUID.randomUUID(), 1)), null, null, "https://x/success", "https://x/cancel")
		}
	}

	@Test
	fun `createCheckoutSession succeeds and attaches the Stripe session id`() {
		val store = store()
		val product = product(store.id)
		val variant = productVariant(product.id)
		every { storeRepository.findBySlug("spring-store") } returns store
		every { productVariantRepository.findById(variant.id, orgId) } returns variant
		every { productRepository.findById(product.id, orgId) } returns product
		val pendingOrder = pendingOrder(store)
		every { orderRepository.insertPending(orgId, store.id, "USD", "Jane Doe", null) } returns pendingOrder
		every { orderItemRepository.insert(pendingOrder.id, variant.id, 2, variant.priceMinor, variant.costMinor) } returns mockk()
		every { stripeOrderCheckoutClient.createOrderCheckoutSession(pendingOrder.id, any(), any(), any()) } returns
			OrderCheckoutSession("cs_test_123", "https://checkout.stripe.com/test")
		every { orderRepository.attachStripeSession(pendingOrder.id, "cs_test_123") } returns 1

		val result = service.createCheckoutSession(
			"spring-store", listOf(OrderLineItemRequest(variant.id, 2)), "Jane Doe", null, "https://x/success", "https://x/cancel",
		)

		assertEquals("https://checkout.stripe.com/test", result.checkoutUrl)
		verify(exactly = 1) { orderRepository.attachStripeSession(pendingOrder.id, "cs_test_123") }
	}

	@Test
	fun `confirmFromWebhook is a no-op when Stripe reports the session as unpaid`() {
		val order = pendingOrder(store())
		every { orderRepository.findByStripeCheckoutSessionId("cs_test_123") } returns order

		val result = service.confirmFromWebhook("cs_test_123", "unpaid", null, null)

		assertEquals(OrderStatus.PENDING, result?.status)
		verify(exactly = 0) { orderRepository.markConfirmed(any(), any(), any()) }
	}

	@Test
	fun `confirmFromWebhook confirms a paid order and submits fulfillment`() {
		val store = store()
		val product = product(store.id)
		val variant = productVariant(product.id)
		val order = pendingOrder(store)
		val confirmed = order.copy(status = OrderStatus.CONFIRMED, confirmedAt = Instant.now())
		every { orderRepository.findByStripeCheckoutSessionId("cs_test_123") } returns order
		every { orderRepository.markConfirmed(order.id, null, null) } returns 1
		every { auditService.record(null, orgId, "order.confirmed", "order", order.id) } just runs
		every { orderItemRepository.findByOrder(order.id) } returns listOf(
			com.leaguelift.order.domain.OrderItem(UUID.randomUUID(), order.id, variant.id, 1, variant.priceMinor, variant.costMinor),
		)
		every { ledgerService.recordConfirmedOrder(any(), any()) } just runs
		every { productVariantRepository.findById(variant.id, orgId) } returns variant
		every { productRepository.findById(product.id, orgId) } returns product
		val designAssignment = mockk<com.leaguelift.media.domain.MediaAssignment>()
		every { mediaAssignmentService.getActiveAssignment(com.leaguelift.media.domain.MediaEntityType.PRODUCT, product.id, com.leaguelift.media.domain.MediaUsageSlot.PRODUCT_DESIGN) } returns designAssignment
		every { mediaReadService.describe(designAssignment) } returns mockk { every { url } returns "https://signed.example.com/design.png" }
		every { printifyOrderClient.createDraftOrder(order.id.toString(), any()) } returns PrintifyDraftOrder("printify_order_1")
		every { fulfillmentRepository.insert(order.id, FulfillmentStatus.DRAFT_CREATED, "printify_order_1", null) } returns mockk()
		every { orderRepository.findById(order.id, orgId) } returns confirmed

		val result = service.confirmFromWebhook("cs_test_123", "paid", null, null)

		assertEquals(OrderStatus.CONFIRMED, result?.status)
		verify(exactly = 1) { fulfillmentRepository.insert(order.id, FulfillmentStatus.DRAFT_CREATED, "printify_order_1", null) }
		verify(exactly = 0) { outboxWriter.write(any(), any(), any(), any(), any()) } // no supporterEmail on this fixture order
	}

	@Test
	fun `confirmFromWebhook writes an order_confirmed outbox event when the order has a supporter email`() {
		val store = store()
		val product = product(store.id)
		val variant = productVariant(product.id)
		val order = pendingOrder(store).copy(supporterEmail = "supporter@example.com")
		val confirmed = order.copy(status = OrderStatus.CONFIRMED, confirmedAt = Instant.now())
		every { orderRepository.findByStripeCheckoutSessionId("cs_test_123") } returns order
		every { orderRepository.markConfirmed(order.id, null, null) } returns 1
		every { auditService.record(null, orgId, "order.confirmed", "order", order.id) } just runs
		every { orderItemRepository.findByOrder(order.id) } returns listOf(
			com.leaguelift.order.domain.OrderItem(UUID.randomUUID(), order.id, variant.id, 1, variant.priceMinor, variant.costMinor),
		)
		every { ledgerService.recordConfirmedOrder(any(), any()) } just runs
		every { productVariantRepository.findById(variant.id, orgId) } returns variant
		every { productRepository.findById(product.id, orgId) } returns product
		val designAssignment = mockk<com.leaguelift.media.domain.MediaAssignment>()
		every { mediaAssignmentService.getActiveAssignment(com.leaguelift.media.domain.MediaEntityType.PRODUCT, product.id, com.leaguelift.media.domain.MediaUsageSlot.PRODUCT_DESIGN) } returns designAssignment
		every { mediaReadService.describe(designAssignment) } returns mockk { every { url } returns "https://signed.example.com/design.png" }
		every { printifyOrderClient.createDraftOrder(order.id.toString(), any()) } returns PrintifyDraftOrder("printify_order_1")
		every { fulfillmentRepository.insert(order.id, FulfillmentStatus.DRAFT_CREATED, "printify_order_1", null) } returns mockk()
		every { orderRepository.findById(order.id, orgId) } returns confirmed
		val payloadSlot = slot<String>()
		every {
			outboxWriter.write(
				aggregateType = "order", aggregateId = order.id, organizationId = orgId,
				eventType = "order.confirmed", payloadJson = capture(payloadSlot),
			)
		} just runs

		service.confirmFromWebhook("cs_test_123", "paid", null, null)

		verify(exactly = 1) { outboxWriter.write(any(), any(), any(), any(), any()) }
		assertEquals(true, payloadSlot.captured.contains("supporter@example.com"))
	}

	@Test
	fun `confirmFromWebhook is idempotent — re-confirming doesn't resubmit fulfillment`() {
		val confirmed = pendingOrder(store()).copy(status = OrderStatus.CONFIRMED, confirmedAt = Instant.now())
		every { orderRepository.findByStripeCheckoutSessionId("cs_test_123") } returns confirmed
		every { orderRepository.markConfirmed(confirmed.id, null, null) } returns 0
		every { orderRepository.findById(confirmed.id, orgId) } returns confirmed

		val result = service.confirmFromWebhook("cs_test_123", "paid", null, null)

		assertEquals(OrderStatus.CONFIRMED, result?.status)
		verify(exactly = 0) { printifyOrderClient.createDraftOrder(any(), any()) }
		verify(exactly = 0) { auditService.record(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `refund calls Stripe, marks REFUNDED, and records a ledger reversal for the gross sale amount`() {
		val store = store()
		val order = pendingOrder(store).copy(status = OrderStatus.CONFIRMED, stripePaymentIntentId = "pi_test_123", confirmedAt = Instant.now())
		val manager = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")
		every { membershipService.requireManagerRole(orgId, manager) } returns managerMembership(manager)
		every { orderRepository.findById(order.id, orgId) } returns order
		every { orderItemRepository.findByOrder(order.id) } returns listOf(
			OrderItem(UUID.randomUUID(), order.id, UUID.randomUUID(), 2, 2_500L, 1_200L),
		)
		every { stripeOrderCheckoutClient.createRefund("pi_test_123") } returns "re_test_123"
		every { orderRepository.markRefunded(order.id) } returns 1
		every { ledgerService.recordRefund(orgId, LedgerSourceType.ORDER, order.id, 5_000L, order.currency, "re_test_123") } just runs
		every { auditService.record(manager.userId, orgId, "order.refunded", "order", order.id) } just runs

		service.refund(orgId, order.id, manager)

		verify(exactly = 1) { stripeOrderCheckoutClient.createRefund("pi_test_123") }
		verify(exactly = 1) { ledgerService.recordRefund(orgId, LedgerSourceType.ORDER, order.id, 5_000L, order.currency, "re_test_123") }
	}

	@Test
	fun `refund rejects an order that was never confirmed`() {
		val order = pendingOrder(store())
		val manager = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")
		every { membershipService.requireManagerRole(orgId, manager) } returns managerMembership(manager)
		every { orderRepository.findById(order.id, orgId) } returns order

		assertFailsWith<ValidationException> {
			service.refund(orgId, order.id, manager)
		}
		verify(exactly = 0) { stripeOrderCheckoutClient.createRefund(any()) }
	}

	@Test
	fun `refund rejects an order confirmed more than 14 days ago`() {
		val stale = pendingOrder(store()).copy(
			status = OrderStatus.CONFIRMED,
			stripePaymentIntentId = "pi_test_123",
			confirmedAt = Instant.now().minus(Duration.ofDays(15)),
		)
		val manager = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")
		every { membershipService.requireManagerRole(orgId, manager) } returns managerMembership(manager)
		every { orderRepository.findById(stale.id, orgId) } returns stale

		assertFailsWith<ValidationException> {
			service.refund(orgId, stale.id, manager)
		}
		verify(exactly = 0) { stripeOrderCheckoutClient.createRefund(any()) }
	}

	private fun managerMembership(manager: CurrentUser) = OrganizationMembership(
		id = UUID.randomUUID(), organizationId = orgId, userId = manager.userId, role = MembershipRole.ADMINISTRATOR,
		status = MembershipStatus.ACTIVE, createdAt = Instant.now(), updatedAt = Instant.now(),
	)

	private fun store(status: StoreStatus = StoreStatus.ACTIVE) = Store(
		id = UUID.randomUUID(), organizationId = orgId, teamId = null, name = "Spring Store", slug = "spring-store",
		status = status, createdAt = Instant.now(), updatedAt = Instant.now(),
	)

	private fun product(storeId: UUID) = Product(
		id = UUID.randomUUID(), organizationId = orgId, storeId = storeId, name = "Team Hoodie", description = null,
		printifyBlueprintId = 12L, printifyImageId = "img_1", printifyPrintPosition = "front",
		status = ProductStatus.ACTIVE, createdAt = Instant.now(), updatedAt = Instant.now(),
	)

	private fun productVariant(productId: UUID) = ProductVariant(
		id = UUID.randomUUID(), organizationId = orgId, productId = productId, label = "M / Navy",
		printifyPrintProviderId = 5L, printifyVariantId = 100L, currency = "USD", costMinor = 1200L, priceMinor = 2500L,
		isActive = true, createdAt = Instant.now(), updatedAt = Instant.now(),
	)

	private fun pendingOrder(store: Store) = Order(
		id = UUID.randomUUID(), organizationId = orgId, storeId = store.id, status = OrderStatus.PENDING, currency = "USD",
		supporterName = "Jane Doe", supporterEmail = null, shippingAddress = null, stripeCheckoutSessionId = "cs_test_123",
		stripePaymentIntentId = null, confirmedAt = null, refundedAt = null, createdAt = Instant.now(),
	)
}
