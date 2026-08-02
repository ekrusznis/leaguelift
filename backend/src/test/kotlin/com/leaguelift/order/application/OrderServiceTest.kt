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
import com.leaguelift.order.domain.Fulfillment
import com.leaguelift.order.domain.FulfillmentHistory
import com.leaguelift.order.domain.FulfillmentSource
import com.leaguelift.order.domain.FulfillmentStatus
import com.leaguelift.order.domain.Order
import com.leaguelift.order.domain.OrderItem
import com.leaguelift.order.domain.OrderStatus
import com.leaguelift.order.infra.OrderCheckoutSession
import com.leaguelift.order.infra.StripeOrderCheckoutClient
import com.leaguelift.order.persistence.FulfillmentHistoryRepository
import com.leaguelift.order.persistence.FulfillmentRepository
import com.leaguelift.order.persistence.OrderItemRepository
import com.leaguelift.order.persistence.OrderRepository
import com.leaguelift.outbox.application.OutboxWriter
import com.leaguelift.store.domain.CatalogSource
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
	private val fulfillmentHistoryRepository = mockk<FulfillmentHistoryRepository>()
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
		orderRepository, orderItemRepository, fulfillmentRepository, fulfillmentHistoryRepository, storeRepository,
		productRepository, productVariantRepository, stripeOrderCheckoutClient, printifyOrderClient,
		mediaAssignmentService, mediaReadService, membershipService, auditService, ledgerService, outboxWriter, ObjectMapper(),
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
	fun `createCheckoutSession rejects a store that is not ACTIVE`() {
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
		every { stripeOrderCheckoutClient.createOrderCheckoutSession(pendingOrder.id, any(), any(), any()) } returns OrderCheckoutSession("cs_test_123", "https://checkout.stripe.com/test")
		every { orderRepository.attachStripeSession(pendingOrder.id, "cs_test_123") } returns 1

		val result = service.createCheckoutSession("spring-store", listOf(OrderLineItemRequest(variant.id, 2)), "Jane Doe", null, "https://x/success", "https://x/cancel")

		assertEquals("https://checkout.stripe.com/test", result.checkoutUrl)
		verify(exactly = 1) { orderRepository.attachStripeSession(pendingOrder.id, "cs_test_123") }
	}

	@Test
	fun `createCheckoutSession rejects mixed manual and Printify sources`() {
		val store = store()
		val printify = product(store.id)
		val manual = product(store.id, CatalogSource.MANUAL)
		val printifyVariant = productVariant(printify.id)
		val manualVariant = productVariant(manual.id, CatalogSource.MANUAL)
		every { storeRepository.findBySlug("spring-store") } returns store
		every { productVariantRepository.findById(printifyVariant.id, orgId) } returns printifyVariant
		every { productVariantRepository.findById(manualVariant.id, orgId) } returns manualVariant
		every { productRepository.findById(printify.id, orgId) } returns printify
		every { productRepository.findById(manual.id, orgId) } returns manual

		assertFailsWith<ValidationException> {
			service.createCheckoutSession(
				"spring-store",
				listOf(OrderLineItemRequest(printifyVariant.id, 1), OrderLineItemRequest(manualVariant.id, 1)),
				null, null, "https://x/success", "https://x/cancel",
			)
		}
	}

	@Test
	fun `confirmFromWebhook is a no-op when Stripe reports the session as unpaid`() {
		val order = pendingOrder(store())
		every { orderRepository.findByStripeCheckoutSessionId("cs_test_123") } returns order
		assertEquals(OrderStatus.PENDING, service.confirmFromWebhook("cs_test_123", "unpaid", null, null)?.status)
		verify(exactly = 0) { orderRepository.markConfirmed(any(), any(), any()) }
	}

	@Test
	fun `confirmFromWebhook confirms a paid Printify order and records fulfillment history`() {
		val store = store()
		val product = product(store.id)
		val variant = productVariant(product.id)
		val order = pendingOrder(store)
		val confirmed = order.copy(status = OrderStatus.CONFIRMED, confirmedAt = Instant.now())
		stubConfirmation(order, confirmed, product, variant)
		val designAssignment = mockk<com.leaguelift.media.domain.MediaAssignment>()
		every { mediaAssignmentService.getActiveAssignment(com.leaguelift.media.domain.MediaEntityType.PRODUCT, product.id, com.leaguelift.media.domain.MediaUsageSlot.PRODUCT_DESIGN) } returns designAssignment
		every { mediaReadService.describe(designAssignment) } returns mockk { every { url } returns "https://signed.example.com/design.png" }
		every { printifyOrderClient.createDraftOrder(order.id.toString(), any()) } returns PrintifyDraftOrder("printify_order_1")
		val fulfillment = fulfillment(order.id, FulfillmentSource.PRINTIFY, FulfillmentStatus.DRAFT_CREATED, "printify_order_1")
		every { fulfillmentRepository.insert(order.id, FulfillmentSource.PRINTIFY, FulfillmentStatus.DRAFT_CREATED, "printify_order_1", null, null) } returns fulfillment
		every { fulfillmentHistoryRepository.insert(orgId, fulfillment.id, null, FulfillmentStatus.DRAFT_CREATED, any(), null) } returns history(fulfillment)

		val result = service.confirmFromWebhook("cs_test_123", "paid", null, null)

		assertEquals(OrderStatus.CONFIRMED, result?.status)
		verify(exactly = 1) { fulfillmentHistoryRepository.insert(orgId, fulfillment.id, null, FulfillmentStatus.DRAFT_CREATED, any(), null) }
	}

	@Test
	fun `confirmFromWebhook creates READY manual fulfillment without calling Printify`() {
		val store = store()
		val vendorId = UUID.randomUUID()
		val product = product(store.id, CatalogSource.MANUAL, vendorId)
		val variant = productVariant(product.id, CatalogSource.MANUAL)
		val order = pendingOrder(store)
		val confirmed = order.copy(status = OrderStatus.CONFIRMED, confirmedAt = Instant.now())
		stubConfirmation(order, confirmed, product, variant)
		val fulfillment = fulfillment(order.id, FulfillmentSource.MANUAL, FulfillmentStatus.READY, manualVendorId = vendorId)
		every { fulfillmentRepository.insert(order.id, FulfillmentSource.MANUAL, FulfillmentStatus.READY, null, vendorId, null) } returns fulfillment
		every { fulfillmentHistoryRepository.insert(orgId, fulfillment.id, null, FulfillmentStatus.READY, any(), null) } returns history(fulfillment)

		val result = service.confirmFromWebhook("cs_test_123", "paid", null, null)

		assertEquals(OrderStatus.CONFIRMED, result?.status)
		verify(exactly = 0) { printifyOrderClient.createDraftOrder(any(), any()) }
		verify(exactly = 1) { fulfillmentRepository.insert(order.id, FulfillmentSource.MANUAL, FulfillmentStatus.READY, null, vendorId, null) }
	}

	@Test
	fun `confirmFromWebhook keeps order CONFIRMED when Printify draft creation fails`() {
		val store = store()
		val product = product(store.id)
		val variant = productVariant(product.id)
		val order = pendingOrder(store)
		val confirmed = order.copy(status = OrderStatus.CONFIRMED, confirmedAt = Instant.now())
		stubConfirmation(order, confirmed, product, variant)
		val designAssignment = mockk<com.leaguelift.media.domain.MediaAssignment>()
		every { mediaAssignmentService.getActiveAssignment(com.leaguelift.media.domain.MediaEntityType.PRODUCT, product.id, com.leaguelift.media.domain.MediaUsageSlot.PRODUCT_DESIGN) } returns designAssignment
		every { mediaReadService.describe(designAssignment) } returns mockk { every { url } returns "https://signed.example.com/design.png" }
		every { printifyOrderClient.createDraftOrder(order.id.toString(), any()) } throws org.springframework.web.client.RestClientException("Printify is unreachable")
		val fulfillment = fulfillment(order.id, FulfillmentSource.PRINTIFY, FulfillmentStatus.FAILED, lastError = "Printify is unreachable")
		every { fulfillmentRepository.insert(order.id, FulfillmentSource.PRINTIFY, FulfillmentStatus.FAILED, null, null, "Printify is unreachable") } returns fulfillment
		every { fulfillmentHistoryRepository.insert(orgId, fulfillment.id, null, FulfillmentStatus.FAILED, any(), null) } returns history(fulfillment)

		assertEquals(OrderStatus.CONFIRMED, service.confirmFromWebhook("cs_test_123", "paid", null, null)?.status)
		verify(exactly = 1) { orderRepository.markConfirmed(order.id, null, null) }
	}

	@Test
	fun `confirmFromWebhook writes order confirmed email event when supporter email exists`() {
		val store = store()
		val product = product(store.id, CatalogSource.MANUAL)
		val variant = productVariant(product.id, CatalogSource.MANUAL)
		val order = pendingOrder(store).copy(supporterEmail = "supporter@example.com")
		val confirmed = order.copy(status = OrderStatus.CONFIRMED, confirmedAt = Instant.now())
		stubConfirmation(order, confirmed, product, variant)
		val fulfillment = fulfillment(order.id, FulfillmentSource.MANUAL, FulfillmentStatus.READY)
		every { fulfillmentRepository.insert(order.id, FulfillmentSource.MANUAL, FulfillmentStatus.READY, null, null, null) } returns fulfillment
		every { fulfillmentHistoryRepository.insert(orgId, fulfillment.id, null, FulfillmentStatus.READY, any(), null) } returns history(fulfillment)
		val payload = slot<String>()
		every { outboxWriter.write("order", order.id, orgId, "order.confirmed", capture(payload)) } just runs

		service.confirmFromWebhook("cs_test_123", "paid", null, null)

		assertEquals(true, payload.captured.contains("supporter@example.com"))
	}

	@Test
	fun `confirmFromWebhook is idempotent and does not resubmit fulfillment`() {
		val confirmed = pendingOrder(store()).copy(status = OrderStatus.CONFIRMED, confirmedAt = Instant.now())
		every { orderRepository.findByStripeCheckoutSessionId("cs_test_123") } returns confirmed
		every { orderRepository.markConfirmed(confirmed.id, null, null) } returns 0
		every { orderRepository.findById(confirmed.id, orgId) } returns confirmed

		assertEquals(OrderStatus.CONFIRMED, service.confirmFromWebhook("cs_test_123", "paid", null, null)?.status)
		verify(exactly = 0) { printifyOrderClient.createDraftOrder(any(), any()) }
	}

	@Test
	fun `refund records a ledger reversal for gross sale amount`() {
		val store = store()
		val order = pendingOrder(store).copy(status = OrderStatus.CONFIRMED, stripePaymentIntentId = "pi_test_123", confirmedAt = Instant.now())
		val manager = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")
		every { membershipService.requireManagerRole(orgId, manager) } returns managerMembership(manager)
		every { orderRepository.findById(order.id, orgId) } returns order
		every { orderItemRepository.findByOrder(order.id) } returns listOf(OrderItem(UUID.randomUUID(), order.id, UUID.randomUUID(), 2, 2500L, 1200L))
		every { stripeOrderCheckoutClient.createRefund("pi_test_123") } returns "re_test_123"
		every { orderRepository.markRefunded(order.id) } returns 1
		every { ledgerService.recordRefund(orgId, LedgerSourceType.ORDER, order.id, 5000L, order.currency, "re_test_123") } just runs
		every { auditService.record(manager.userId, orgId, "order.refunded", "order", order.id) } just runs

		service.refund(orgId, order.id, manager)

		verify(exactly = 1) { ledgerService.recordRefund(orgId, LedgerSourceType.ORDER, order.id, 5000L, order.currency, "re_test_123") }
	}

	@Test
	fun `refund rejects an order that was never confirmed`() {
		val order = pendingOrder(store())
		val manager = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")
		every { membershipService.requireManagerRole(orgId, manager) } returns managerMembership(manager)
		every { orderRepository.findById(order.id, orgId) } returns order
		assertFailsWith<ValidationException> { service.refund(orgId, order.id, manager) }
	}

	@Test
	fun `refund rejects an order confirmed more than 14 days ago`() {
		val order = pendingOrder(store()).copy(status = OrderStatus.CONFIRMED, stripePaymentIntentId = "pi_test_123", confirmedAt = Instant.now().minus(Duration.ofDays(15)))
		val manager = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")
		every { membershipService.requireManagerRole(orgId, manager) } returns managerMembership(manager)
		every { orderRepository.findById(order.id, orgId) } returns order
		assertFailsWith<ValidationException> { service.refund(orgId, order.id, manager) }
	}

	private fun stubConfirmation(order: Order, confirmed: Order, product: Product, variant: ProductVariant) {
		every { orderRepository.findByStripeCheckoutSessionId("cs_test_123") } returns order
		every { orderRepository.markConfirmed(order.id, null, null) } returns 1
		every { auditService.record(null, orgId, "order.confirmed", "order", order.id) } just runs
		every { orderItemRepository.findByOrder(order.id) } returns listOf(OrderItem(UUID.randomUUID(), order.id, variant.id, 1, variant.priceMinor, variant.costMinor))
		every { ledgerService.recordConfirmedOrder(any(), any()) } just runs
		every { productVariantRepository.findById(variant.id, orgId) } returns variant
		every { productRepository.findById(product.id, orgId) } returns product
		every { orderRepository.findById(order.id, orgId) } returns confirmed
	}

	private fun history(fulfillment: Fulfillment) = FulfillmentHistory(UUID.randomUUID(), orgId, fulfillment.id, null, fulfillment.status, "created", null, Instant.now())

	private fun fulfillment(
		orderId: UUID,
		source: FulfillmentSource,
		status: FulfillmentStatus,
		printifyOrderId: String? = null,
		manualVendorId: UUID? = null,
		lastError: String? = null,
	) = Fulfillment(
		id = UUID.randomUUID(), orderId = orderId, source = source, status = status, printifyOrderId = printifyOrderId,
		manualVendorId = manualVendorId, manualVendorName = null, vendorOrderReference = null, carrier = null,
		trackingNumber = null, trackingUrl = null, internalNotes = null, attentionReason = null, lastError = lastError,
		statusChangedAt = Instant.now(), shippedAt = null, deliveredAt = null, createdAt = Instant.now(), updatedAt = Instant.now(),
	)

	private fun managerMembership(manager: CurrentUser) = OrganizationMembership(
		id = UUID.randomUUID(), organizationId = orgId, userId = manager.userId, role = MembershipRole.ADMINISTRATOR,
		status = MembershipStatus.ACTIVE, createdAt = Instant.now(), updatedAt = Instant.now(),
	)

	private fun store(status: StoreStatus = StoreStatus.ACTIVE) = Store(
		id = UUID.randomUUID(), organizationId = orgId, teamId = null, name = "Spring Store", slug = "spring-store",
		status = status, createdAt = Instant.now(), updatedAt = Instant.now(),
	)

	private fun product(storeId: UUID, source: CatalogSource = CatalogSource.PRINTIFY, vendorId: UUID? = null) = Product(
		id = UUID.randomUUID(), organizationId = orgId, storeId = storeId, name = "Team Hoodie", description = null,
		catalogSource = source, manualVendorId = vendorId, manualVendorName = null,
		printifyBlueprintId = if (source == CatalogSource.PRINTIFY) 12L else null,
		printifyImageId = if (source == CatalogSource.PRINTIFY) "img_1" else null, printifyPrintPosition = "front",
		status = ProductStatus.ACTIVE, createdAt = Instant.now(), updatedAt = Instant.now(),
	)

	private fun productVariant(productId: UUID, source: CatalogSource = CatalogSource.PRINTIFY) = ProductVariant(
		id = UUID.randomUUID(), organizationId = orgId, productId = productId, catalogSource = source, label = "M / Navy",
		sku = if (source == CatalogSource.MANUAL) "M-NAVY" else null, size = "M", color = "Navy",
		printifyPrintProviderId = if (source == CatalogSource.PRINTIFY) 5L else null,
		printifyVariantId = if (source == CatalogSource.PRINTIFY) 100L else null,
		currency = "USD", costMinor = 1200L, priceMinor = 2500L, isActive = true,
		createdAt = Instant.now(), updatedAt = Instant.now(),
	)

	private fun pendingOrder(store: Store) = Order(
		id = UUID.randomUUID(), organizationId = orgId, storeId = store.id, status = OrderStatus.PENDING, currency = "USD",
		supporterName = "Jane Doe", supporterEmail = null, shippingAddress = null, stripeCheckoutSessionId = "cs_test_123",
		stripePaymentIntentId = null, confirmedAt = null, refundedAt = null, createdAt = Instant.now(),
	)
}
