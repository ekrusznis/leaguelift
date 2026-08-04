package com.rally26.order.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ServiceUnavailableException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.integration.printify.infra.PrintifyOrderClient
import com.rally26.integration.printify.infra.PrintifyOrderLineItem
import com.rally26.ledger.application.LedgerService
import com.rally26.ledger.domain.LedgerSourceType
import com.rally26.media.application.MediaAssignmentService
import com.rally26.media.application.MediaReadService
import com.rally26.media.domain.MediaEntityType
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.membership.application.MembershipService
import com.rally26.order.domain.Fulfillment
import com.rally26.order.domain.FulfillmentSource
import com.rally26.order.domain.FulfillmentStatus
import com.rally26.order.domain.Order
import com.rally26.order.domain.OrderStatus
import com.rally26.order.domain.ShippingAddress
import com.rally26.order.infra.OrderCheckoutLineItem
import com.rally26.order.infra.StripeOrderCheckoutClient
import com.rally26.order.persistence.FulfillmentHistoryRepository
import com.rally26.order.persistence.FulfillmentRepository
import com.rally26.order.persistence.OrderItemRepository
import com.rally26.order.persistence.OrderRepository
import com.rally26.outbox.application.OutboxWriter
import com.rally26.store.domain.CatalogSource
import com.rally26.store.domain.ProductStatus
import com.rally26.store.domain.StoreStatus
import com.rally26.store.persistence.ProductRepository
import com.rally26.store.persistence.ProductVariantRepository
import com.rally26.store.persistence.StoreRepository
import com.stripe.exception.StripeException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClientException
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val log = LoggerFactory.getLogger(OrderService::class.java)

/** Mirrors Stripe's own `{CHECKOUT_SESSION_ID}` success-url placeholder convention — the frontend can't know the order id until this call returns, so it asks for it to be filled in server-side instead (same pattern as ContributionService.CONTRIBUTION_ID_PLACEHOLDER). */
const val ORDER_ID_PLACEHOLDER = "{ORDER_ID}"

/** Org-admin-initiated refunds are only allowed within this window of confirmation (ADR-017, 2026-07-29 founder decision; same policy as ContributionService.REFUND_WINDOW). */
val ORDER_REFUND_WINDOW: Duration = Duration.ofDays(14)

data class OrderLineItemRequest(val productVariantId: UUID, val quantity: Int)
data class OrderCheckout(val orderId: UUID, val checkoutUrl: String)

/** `order.confirmed` outbox payload (Phase 8 slice 2) — consumed by `OrderConfirmationEmailHandler`. */
data class OrderConfirmedPayload(val supporterEmail: String, val supporterName: String?, val totalMinor: Long, val currency: String)

/**
 * Order checkout (Phase 4 slice 1) — mirrors
 * `fundraising/application/ContributionService.kt`'s shape closely: confirmation
 * happens only via the Stripe webhook, never a sync refresh-on-return, for the
 * same reason (a supporter who pays and closes the tab shouldn't leave Stripe
 * holding confirmed money we never record). Fulfillment submission to Printify
 * is best-effort on confirmation — a Printify failure never undoes or hides the
 * payment confirmation, it only leaves `fulfillment.status = FAILED` for admin
 * follow-up.
 */
@Service
class OrderService(
	private val orderRepository: OrderRepository,
	private val orderItemRepository: OrderItemRepository,
	private val fulfillmentRepository: FulfillmentRepository,
	private val fulfillmentHistoryRepository: FulfillmentHistoryRepository,
	private val storeRepository: StoreRepository,
	private val productRepository: ProductRepository,
	private val productVariantRepository: ProductVariantRepository,
	private val stripeOrderCheckoutClient: StripeOrderCheckoutClient,
	private val printifyOrderClient: PrintifyOrderClient,
	private val mediaAssignmentService: MediaAssignmentService,
	private val mediaReadService: MediaReadService,
	private val membershipService: MembershipService,
	private val auditService: AuditService,
	private val ledgerService: LedgerService,
	private val outboxWriter: OutboxWriter,
	private val objectMapper: ObjectMapper,
) {

	@Transactional
	fun createCheckoutSession(
		storeSlug: String,
		items: List<OrderLineItemRequest>,
		supporterName: String?,
		supporterEmail: String?,
		successUrl: String,
		cancelUrl: String,
	): OrderCheckout {
		if (items.isEmpty()) throw ValidationException("At least one item is required.")
		val store = storeRepository.findBySlug(storeSlug)
			?: throw NotFoundException("STORE_NOT_FOUND", "The store could not be found.")
		if (store.status != StoreStatus.ACTIVE) {
			throw ValidationException("This store isn't currently open for orders.")
		}

		val resolvedItems = items.map { item ->
			if (item.quantity <= 0) throw ValidationException("Quantity must be greater than 0.")
			val variant = productVariantRepository.findById(item.productVariantId, store.organizationId)
				?.takeIf { it.isActive }
				?: throw NotFoundException("PRODUCT_VARIANT_NOT_FOUND", "A selected product variant could not be found.")
			val product = productRepository.findById(variant.productId, store.organizationId)
				?.takeIf { it.storeId == store.id && it.status == ProductStatus.ACTIVE }
				?: throw NotFoundException("PRODUCT_NOT_FOUND", "A selected product could not be found.")
			Triple(product, variant, item.quantity)
		}
		val currency = resolvedItems.first().second.currency
		if (resolvedItems.any { it.second.currency != currency }) {
			throw ValidationException("All items in a single checkout must use the same currency.")
		}
		val catalogSources = resolvedItems.map { it.first.catalogSource }.toSet()
		if (catalogSources.size != 1) {
			throw ValidationException("Printify and manually fulfilled products cannot be combined in one order.")
		}
		if (resolvedItems.any { (product, variant, _) -> product.catalogSource != variant.catalogSource }) {
			throw ValidationException("A product variant has an invalid fulfillment source.")
		}
		if (catalogSources.single() == CatalogSource.MANUAL) {
			val vendorIds = resolvedItems.map { it.first.manualVendorId }.toSet()
			if (vendorIds.size != 1) {
				throw ValidationException("Manually fulfilled products from different vendors cannot be combined in one order.")
			}
		}

		return try {
			val order = orderRepository.insertPending(store.organizationId, store.id, currency, supporterName, supporterEmail)
			resolvedItems.forEach { (_, variant, quantity) ->
				orderItemRepository.insert(order.id, variant.id, quantity, variant.priceMinor, variant.costMinor)
			}
			val lineItems = resolvedItems.map { (product, variant, quantity) ->
				OrderCheckoutLineItem(name = "${product.name} - ${variant.label}", quantity = quantity.toLong(), unitPriceMinor = variant.priceMinor, currency = currency)
			}
			val resolvedSuccessUrl = successUrl.replace(ORDER_ID_PLACEHOLDER, order.id.toString())
			val session = stripeOrderCheckoutClient.createOrderCheckoutSession(order.id, lineItems, resolvedSuccessUrl, cancelUrl)
			orderRepository.attachStripeSession(order.id, session.sessionId)
			OrderCheckout(order.id, session.checkoutUrl)
		} catch (e: StripeException) {
			log.warn("Stripe order checkout session creation failed: {}", e.message, e)
			throw ServiceUnavailableException(
				"ORDER_PROVIDER_UNAVAILABLE",
				"Payments provider is not available right now. If this is local/staging, confirm STRIPE_SECRET_KEY is set.",
			)
		}
	}

	/** Idempotent: a duplicate webhook delivery or an already-confirmed order is a safe no-op. */
	@Transactional
	fun confirmFromWebhook(stripeSessionId: String, stripePaymentStatus: String, shippingAddress: ShippingAddress?, stripePaymentIntentId: String?): Order? {
		val order = orderRepository.findByStripeCheckoutSessionId(stripeSessionId) ?: return null
		if (stripePaymentStatus != "paid") return order
		val updated = orderRepository.markConfirmed(order.id, shippingAddress, stripePaymentIntentId)
		if (updated > 0) {
			auditService.record(null, order.organizationId, "order.confirmed", "order", order.id)
			val items = orderItemRepository.findByOrder(order.id)
			ledgerService.recordConfirmedOrder(order.copy(status = OrderStatus.CONFIRMED), items)
			createInitialFulfillment(order.id, order.organizationId)
			if (order.supporterEmail != null) {
				val totalMinor = items.sumOf { it.unitPriceMinor * it.quantity }
				outboxWriter.write(
					aggregateType = "order",
					aggregateId = order.id,
					organizationId = order.organizationId,
					eventType = "order.confirmed",
					payloadJson = objectMapper.writeValueAsString(
						OrderConfirmedPayload(order.supporterEmail, order.supporterName, totalMinor, order.currency),
					),
				)
			}
		}
		return orderRepository.findById(order.id, order.organizationId)
	}

	fun getStatus(storeSlug: String, orderId: UUID): Order {
		val store = storeRepository.findBySlug(storeSlug)
			?: throw NotFoundException("STORE_NOT_FOUND", "The store could not be found.")
		val order = orderRepository.findById(orderId, store.organizationId)
			?.takeIf { it.storeId == store.id }
			?: throw NotFoundException("ORDER_NOT_FOUND", "The order could not be found.")
		return order
	}

	fun listForStore(organizationId: UUID, storeId: UUID, currentUser: CurrentUser, offset: Int, limit: Int): List<Order> {
		membershipService.requireManagerRole(organizationId, currentUser)
		storeRepository.findById(storeId, organizationId) ?: throw NotFoundException("STORE_NOT_FOUND", "The store could not be found.")
		return orderRepository.findByStore(storeId, offset, limit)
	}

	fun getConfirmedCount(organizationId: UUID, storeId: UUID, currentUser: CurrentUser): Long {
		membershipService.requireManagerRole(organizationId, currentUser)
		return orderRepository.countConfirmedByStore(storeId)
	}

	fun getFulfillment(organizationId: UUID, orderId: UUID, currentUser: CurrentUser): Fulfillment? {
		membershipService.requireManagerRole(organizationId, currentUser)
		orderRepository.findById(orderId, organizationId) ?: throw NotFoundException("ORDER_NOT_FOUND", "The order could not be found.")
		return fulfillmentRepository.findByOrder(orderId)
	}

	/** Refunds the gross sale amount; production cost already spent with Printify is not returned (see LedgerService.recordRefund). */
	@Transactional
	fun refund(organizationId: UUID, orderId: UUID, currentUser: CurrentUser): Order {
		membershipService.requireManagerRole(organizationId, currentUser)
		val order = orderRepository.findById(orderId, organizationId)
			?: throw NotFoundException("ORDER_NOT_FOUND", "The order could not be found.")
		if (order.status != OrderStatus.CONFIRMED || order.stripePaymentIntentId == null) {
			throw ValidationException("Only a confirmed order with a recorded payment can be refunded.")
		}
		val confirmedAt = order.confirmedAt ?: throw ValidationException("This order has no confirmation date on record.")
		if (Duration.between(confirmedAt, Instant.now()) > ORDER_REFUND_WINDOW) {
			throw ValidationException("This order can no longer be refunded — it was confirmed more than ${ORDER_REFUND_WINDOW.toDays()} days ago.")
		}
		val stripeRefundId = try {
			stripeOrderCheckoutClient.createRefund(order.stripePaymentIntentId)
		} catch (e: StripeException) {
			log.warn("Stripe refund failed for order {}: {}", orderId, e.message, e)
			throw ServiceUnavailableException(
				"ORDER_PROVIDER_UNAVAILABLE",
				"Payments provider is not available right now. If this is local/staging, confirm STRIPE_SECRET_KEY is set.",
			)
		}
		val grossAmountMinor = orderItemRepository.findByOrder(order.id).sumOf { it.unitPriceMinor * it.quantity }
		orderRepository.markRefunded(order.id)
		ledgerService.recordRefund(organizationId, LedgerSourceType.ORDER, order.id, grossAmountMinor, order.currency, stripeRefundId)
		auditService.record(currentUser.userId, organizationId, "order.refunded", "order", order.id)
		return orderRepository.findById(order.id, organizationId)!!
	}

	private fun createInitialFulfillment(orderId: UUID, organizationId: UUID) {
		val items = orderItemRepository.findByOrder(orderId)
		val resolved = items.map { item ->
			val variant = productVariantRepository.findById(item.productVariantId, organizationId)
				?: error("order_item ${item.id} references a missing product_variant")
			val product = productRepository.findById(variant.productId, organizationId)
				?: error("product_variant ${variant.id} references a missing product")
			Triple(item, product, variant)
		}
		val source = resolved.map { it.second.catalogSource }.distinct().singleOrNull()
			?: error("order $orderId contains mixed catalog sources")
		if (source == CatalogSource.MANUAL) {
			val vendorId = resolved.map { it.second.manualVendorId }.distinct().singleOrNull()
			val fulfillment = fulfillmentRepository.insert(
				orderId = orderId, source = FulfillmentSource.MANUAL, status = FulfillmentStatus.READY,
				printifyOrderId = null, manualVendorId = vendorId, lastError = null,
			)
			fulfillmentHistoryRepository.insert(
				organizationId, fulfillment.id, null, fulfillment.status,
				"Manual fulfillment created after payment confirmation.", null,
			)
			return
		}

		try {
			val lineItems = resolved.map { (item, product, variant) ->
				val designAssignment = mediaAssignmentService.getActiveAssignment(MediaEntityType.PRODUCT, product.id, MediaUsageSlot.PRODUCT_DESIGN)
					?: error("product ${product.id} has no design assigned")
				val designUrl = mediaReadService.describe(designAssignment)?.url
					?: error("product ${product.id}'s design asset could not be found")
				PrintifyOrderLineItem(
					printifyBlueprintId = product.printifyBlueprintId ?: error("PRINTIFY product ${product.id} has no blueprint ID"),
					printifyPrintProviderId = variant.printifyPrintProviderId ?: error("PRINTIFY variant ${variant.id} has no provider ID"),
					printifyVariantId = variant.printifyVariantId ?: error("PRINTIFY variant ${variant.id} has no variant ID"),
					quantity = item.quantity,
					printAreaImagesByPosition = mapOf(product.printifyPrintPosition to designUrl),
				)
			}
			val draftOrder = printifyOrderClient.createDraftOrder(orderId.toString(), lineItems)
			val fulfillment = fulfillmentRepository.insert(
				orderId, FulfillmentSource.PRINTIFY, FulfillmentStatus.DRAFT_CREATED, draftOrder.printifyOrderId, null, null,
			)
			fulfillmentHistoryRepository.insert(
				organizationId, fulfillment.id, null, fulfillment.status,
				"Printify draft order created after payment confirmation.", null,
			)
		} catch (e: RestClientException) {
			recordPrintifyFailure(orderId, organizationId, e.message ?: e.javaClass.simpleName, e)
		} catch (e: Exception) {
			recordPrintifyFailure(orderId, organizationId, e.message ?: e.javaClass.simpleName, e)
		}
	}

	private fun recordPrintifyFailure(orderId: UUID, organizationId: UUID, message: String, exception: Exception) {
		log.error("Fulfillment submission failed for order {}: {}", orderId, message, exception)
		val fulfillment = fulfillmentRepository.insert(
			orderId, FulfillmentSource.PRINTIFY, FulfillmentStatus.FAILED, null, null, message,
		)
		fulfillmentHistoryRepository.insert(
			organizationId, fulfillment.id, null, fulfillment.status,
			"Printify draft-order creation failed; payment remains confirmed.", null,
		)
	}

}
