package com.leaguelift.order.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ServiceUnavailableException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.integration.printify.infra.PrintifyOrderClient
import com.leaguelift.integration.printify.infra.PrintifyOrderLineItem
import com.leaguelift.media.application.MediaAssignmentService
import com.leaguelift.media.application.MediaReadService
import com.leaguelift.media.domain.MediaEntityType
import com.leaguelift.media.domain.MediaUsageSlot
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.order.domain.Fulfillment
import com.leaguelift.order.domain.FulfillmentStatus
import com.leaguelift.order.domain.Order
import com.leaguelift.order.domain.ShippingAddress
import com.leaguelift.order.infra.OrderCheckoutLineItem
import com.leaguelift.order.infra.StripeOrderCheckoutClient
import com.leaguelift.order.persistence.FulfillmentRepository
import com.leaguelift.order.persistence.OrderItemRepository
import com.leaguelift.order.persistence.OrderRepository
import com.leaguelift.store.domain.ProductStatus
import com.leaguelift.store.domain.StoreStatus
import com.leaguelift.store.persistence.ProductRepository
import com.leaguelift.store.persistence.ProductVariantRepository
import com.leaguelift.store.persistence.StoreRepository
import com.stripe.exception.StripeException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClientException
import java.util.UUID

private val log = LoggerFactory.getLogger(OrderService::class.java)

/** Mirrors Stripe's own `{CHECKOUT_SESSION_ID}` success-url placeholder convention — the frontend can't know the order id until this call returns, so it asks for it to be filled in server-side instead (same pattern as ContributionService.CONTRIBUTION_ID_PLACEHOLDER). */
const val ORDER_ID_PLACEHOLDER = "{ORDER_ID}"

data class OrderLineItemRequest(val productVariantId: UUID, val quantity: Int)
data class OrderCheckout(val orderId: UUID, val checkoutUrl: String)

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
	private val storeRepository: StoreRepository,
	private val productRepository: ProductRepository,
	private val productVariantRepository: ProductVariantRepository,
	private val stripeOrderCheckoutClient: StripeOrderCheckoutClient,
	private val printifyOrderClient: PrintifyOrderClient,
	private val mediaAssignmentService: MediaAssignmentService,
	private val mediaReadService: MediaReadService,
	private val membershipService: MembershipService,
	private val auditService: AuditService,
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
	fun confirmFromWebhook(stripeSessionId: String, stripePaymentStatus: String, shippingAddress: ShippingAddress?): Order? {
		val order = orderRepository.findByStripeCheckoutSessionId(stripeSessionId) ?: return null
		if (stripePaymentStatus != "paid") return order
		val updated = orderRepository.markConfirmed(order.id, shippingAddress)
		if (updated > 0) {
			auditService.record(null, order.organizationId, "order.confirmed", "order", order.id)
			submitFulfillment(order.id, order.organizationId)
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
		membershipService.requireActiveMembership(organizationId, currentUser)
		storeRepository.findById(storeId, organizationId) ?: throw NotFoundException("STORE_NOT_FOUND", "The store could not be found.")
		return orderRepository.findByStore(storeId, offset, limit)
	}

	fun getConfirmedCount(organizationId: UUID, storeId: UUID, currentUser: CurrentUser): Long {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return orderRepository.countConfirmedByStore(storeId)
	}

	fun getFulfillment(organizationId: UUID, orderId: UUID, currentUser: CurrentUser): Fulfillment? {
		membershipService.requireActiveMembership(organizationId, currentUser)
		orderRepository.findById(orderId, organizationId) ?: throw NotFoundException("ORDER_NOT_FOUND", "The order could not be found.")
		return fulfillmentRepository.findByOrder(orderId)
	}

	private fun submitFulfillment(orderId: UUID, organizationId: UUID) {
		try {
			val lineItems = orderItemRepository.findByOrder(orderId).map { item ->
				val variant = productVariantRepository.findById(item.productVariantId, organizationId)
					?: error("order_item ${item.id} references a missing product_variant")
				val product = productRepository.findById(variant.productId, organizationId)
					?: error("product_variant ${variant.id} references a missing product")
				val designAssignment = mediaAssignmentService.getActiveAssignment(MediaEntityType.PRODUCT, product.id, MediaUsageSlot.PRODUCT_DESIGN)
					?: error("product ${product.id} has no design assigned")
				val designUrl = mediaReadService.describe(designAssignment)?.url
					?: error("product ${product.id}'s design asset could not be found")
				PrintifyOrderLineItem(
					printifyBlueprintId = product.printifyBlueprintId,
					printifyPrintProviderId = variant.printifyPrintProviderId,
					printifyVariantId = variant.printifyVariantId,
					quantity = item.quantity,
					printAreaImagesByPosition = mapOf(product.printifyPrintPosition to designUrl),
				)
			}
			val draftOrder = printifyOrderClient.createDraftOrder(orderId.toString(), lineItems)
			fulfillmentRepository.insert(orderId, FulfillmentStatus.DRAFT_CREATED, draftOrder.printifyOrderId, null)
		} catch (e: RestClientException) {
			log.error("Printify draft-order creation failed for order {}: {}", orderId, e.message, e)
			fulfillmentRepository.insert(orderId, FulfillmentStatus.FAILED, null, e.message ?: e.javaClass.simpleName)
		} catch (e: Exception) {
			log.error("Fulfillment submission failed for order {}: {}", orderId, e.message, e)
			fulfillmentRepository.insert(orderId, FulfillmentStatus.FAILED, null, e.message ?: e.javaClass.simpleName)
		}
	}
}
