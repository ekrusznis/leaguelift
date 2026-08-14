package com.rally26.order.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.authorization.domain.ContextType
import com.rally26.common.error.ConflictException
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ServiceUnavailableException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.config.FrontendProperties
import com.rally26.config.PrintifyProperties
import com.rally26.credit.application.FamilyCreditService
import com.rally26.integration.printify.application.PrintifyOwnershipPrefixService
import com.rally26.integration.printify.infra.PrintifyCatalogClient
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
import com.rally26.order.domain.PersonalizationPlacement
import com.rally26.order.domain.ShippingAddress
import com.rally26.order.domain.SwagLogoSize
import com.rally26.order.infra.OrderCheckoutLineItem
import com.rally26.order.infra.StripeOrderCheckoutClient
import com.rally26.order.persistence.FulfillmentHistoryRepository
import com.rally26.order.persistence.FulfillmentRepository
import com.rally26.order.persistence.OrderItemRepository
import com.rally26.order.persistence.OrderRepository
import com.rally26.outbox.application.OutboxWriter
import com.rally26.participant.persistence.ParticipantRepository
import com.rally26.store.application.AthleteStorefrontService
import com.rally26.store.application.SwagDesignCompositor
import com.rally26.store.domain.CatalogSource
import com.rally26.store.domain.Product
import com.rally26.store.domain.ProductStatus
import com.rally26.store.domain.ProductVariant
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

data class OrderLineItemRequest(
    val productVariantId: UUID,
    val quantity: Int,
)

data class OrderCheckout(
    val orderId: UUID,
    val checkoutUrl: String,
)

/** Swag Shop "my past orders" (see OrderService.listMySwagShopOrders) — one confirmed order_item, enriched with display fields the frontend needs for a history card and a reorder prefill. */
data class SwagShopOrderHistoryItem(
    val orderId: UUID,
    val confirmedAt: Instant,
    val participantId: UUID,
    val participantName: String,
    val productId: UUID,
    val productName: String,
    val variantId: UUID,
    val variantLabel: String,
    val size: String?,
    val color: String?,
    val mockupFrontUrl: String?,
    val personalizationName: String?,
    val personalizationNumber: String?,
    val personalizationPlacement: PersonalizationPlacement?,
    val personalizationLogoSize: SwagLogoSize?,
    val unitPriceMinor: Long,
    val currency: String,
    val isReorderable: Boolean,
)

/** `order.confirmed` outbox payload (Phase 8 slice 2) — consumed by `OrderConfirmationEmailHandler`. */
data class OrderConfirmedPayload(
    val supporterEmail: String,
    val supporterName: String?,
    val totalMinor: Long,
    val currency: String,
    val storeSlug: String,
    val confirmedAt: String,
)

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
    private val swagDesignCompositor: SwagDesignCompositor,
    private val participantRepository: ParticipantRepository,
    private val authorizationService: AuthorizationService,
    private val frontendProperties: FrontendProperties,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
    private val ledgerService: LedgerService,
    private val outboxWriter: OutboxWriter,
    private val objectMapper: ObjectMapper,
    private val athleteStorefrontService: AthleteStorefrontService,
    private val familyCreditService: FamilyCreditService,
    private val printifyOwnershipPrefixService: PrintifyOwnershipPrefixService,
    private val printifyProperties: PrintifyProperties,
    private val printifyCatalogClient: PrintifyCatalogClient,
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
        val store =
            storeRepository.findBySlug(storeSlug)
                ?: throw NotFoundException("STORE_NOT_FOUND", "The store could not be found.")
        if (store.status != StoreStatus.ACTIVE) {
            throw ValidationException("This store isn't currently open for orders.")
        }

        val resolvedItems =
            items.map { item ->
                if (item.quantity <= 0) throw ValidationException("Quantity must be greater than 0.")
                val variant =
                    productVariantRepository
                        .findById(item.productVariantId, store.organizationId)
                        ?.takeIf { it.isActive }
                        ?: throw NotFoundException("PRODUCT_VARIANT_NOT_FOUND", "A selected product variant could not be found.")
                val product =
                    productRepository
                        .findById(variant.productId, store.organizationId)
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
            val lineItems =
                resolvedItems.map { (product, variant, quantity) ->
                    OrderCheckoutLineItem(
                        name = "${product.name} - ${variant.label}",
                        quantity = quantity.toLong(),
                        unitPriceMinor = variant.priceMinor,
                        currency = currency,
                    )
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

    /**
     * Swag Shop (DESIGN-DOC.md section 13): the first authenticated (non-public)
     * Stripe-Checkout-redirect flow in this codebase — a coach ordering for a roster
     * athlete, or a guardian ordering for their own household's athlete, while signed
     * in. Reuses the exact Stripe/webhook machinery createCheckoutSession already
     * established; confirmFromWebhook doesn't care which controller created the order.
     * One item only (Path 1/Quick scope — no multi-item cart for this flow).
     */
    @Transactional
    fun createSwagShopCheckoutSession(
        organizationId: UUID,
        productVariantId: UUID,
        participantId: UUID,
        personalizationName: String?,
        personalizationNumber: String?,
        personalizationPlacement: PersonalizationPlacement?,
        personalizationLogoSize: SwagLogoSize?,
        currentUser: CurrentUser,
    ): OrderCheckout {
        val participant =
            participantRepository.findById(participantId, organizationId)
                ?: throw NotFoundException("PARTICIPANT_NOT_FOUND", "The participant could not be found.")
        requireSwagShopOrderAccess(organizationId, participant.householdId, participantId, currentUser)

        val variant =
            productVariantRepository
                .findById(productVariantId, organizationId)
                ?.takeIf { it.isActive }
                ?: throw NotFoundException("PRODUCT_VARIANT_NOT_FOUND", "The selected apparel type could not be found.")
        val product =
            productRepository
                .findById(variant.productId, organizationId)
                ?.takeIf { it.status == ProductStatus.ACTIVE }
                ?: throw NotFoundException("PRODUCT_NOT_FOUND", "The selected apparel type could not be found.")
        if (product.catalogSource != CatalogSource.PRINTIFY) {
            throw ValidationException("Only Printify-backed Swag Shop apparel can be personalized and ordered this way.")
        }
        val store =
            storeRepository.findById(product.storeId, organizationId)
                ?: throw NotFoundException("STORE_NOT_FOUND", "The Swag Shop could not be found.")
        if (store.status != StoreStatus.ACTIVE) {
            throw ValidationException("This Swag Shop isn't currently open for orders.")
        }
        requireVariantStillAvailable(product, variant)

        val isPersonalized = personalizationName != null || personalizationNumber != null || personalizationPlacement != null
        if (isPersonalized) {
            if (product.swagLogoMediaAssetId == null) {
                throw ValidationException("This apparel type isn't set up for personalization yet — ask staff to add the team logo.")
            }
            if (variant.printAreaWidthPx == null || variant.printAreaHeightPx == null) {
                throw ValidationException("This apparel type is missing print-area dimensions — ask staff to re-create the variant.")
            }
            // BACK personalization is a real second physical print (2026-08-05) — reject
            // cleanly here rather than failing deep in fulfillment for a variant created
            // before this capability, or from a blueprint with no "back" placeholder.
            if (personalizationPlacement == PersonalizationPlacement.BACK &&
                (variant.backPrintAreaWidthPx == null || variant.backPrintAreaHeightPx == null)
            ) {
                throw ValidationException("This apparel type doesn't support back placement yet — ask staff to re-create the variant.")
            }
        }

        return try {
            val order =
                orderRepository.insertPending(
                    organizationId,
                    store.id,
                    variant.currency,
                    currentUser.displayName,
                    currentUser.email,
                )
            orderItemRepository.insert(
                orderId = order.id,
                productVariantId = variant.id,
                quantity = 1,
                unitPriceMinor = variant.priceMinor,
                unitCostMinor = variant.costMinor,
                participantId = participant.id,
                personalizationName = personalizationName,
                personalizationNumber = personalizationNumber,
                personalizationPlacement = personalizationPlacement,
                personalizationLogoSize = personalizationLogoSize,
            )
            val lineItems =
                listOf(
                    OrderCheckoutLineItem(
                        name = "${product.name} - ${variant.label}",
                        quantity = 1,
                        unitPriceMinor = variant.priceMinor,
                        currency = variant.currency,
                    ),
                )
            // Redirects back to the order form itself (not a separate confirmation page/route,
            // which doesn't exist) with a status flag the page reads to show a banner —
            // matches the founder's own suggested UX after this was tested live and landed
            // on a dead route.
            val successUrl =
                @Suppress("ktlint:standard:max-line-length")
                "${frontendProperties.baseUrl}/app/organizations/$organizationId/swag-shop/order?orderId=${order.id}&status=success"
            val cancelUrl = "${frontendProperties.baseUrl}/app/organizations/$organizationId/swag-shop/order?status=canceled"
            val session = stripeOrderCheckoutClient.createOrderCheckoutSession(order.id, lineItems, successUrl, cancelUrl)
            orderRepository.attachStripeSession(order.id, session.sessionId)
            OrderCheckout(order.id, session.checkoutUrl)
        } catch (e: StripeException) {
            log.warn("Stripe Swag Shop checkout session creation failed: {}", e.message, e)
            throw ServiceUnavailableException(
                "ORDER_PROVIDER_UNAVAILABLE",
                "Payments provider is not available right now. If this is local/staging, confirm STRIPE_SECRET_KEY is set.",
            )
        }
    }

    /**
     * Phase 24 slice 24.3 (DESIGN-DOC.md section 14.1G): public, unauthenticated
     * checkout through a published athlete storefront — a supporter with the link
     * (e.g. a grandparent) buys for one specific, fixed athlete, no Rally26 account
     * needed. Unlike `createSwagShopCheckoutSession`, household attribution is
     * resolved directly from the storefront's own `participantId` (never from
     * caller-supplied input) and snapshotted onto the order so `confirmFromWebhook`
     * can grant a Phase 23 family credit once payment is confirmed.
     */
    @Transactional
    fun createAthleteStorefrontCheckoutSession(
        storefrontSlug: String,
        productVariantId: UUID,
        personalizationName: String?,
        personalizationNumber: String?,
        personalizationPlacement: PersonalizationPlacement?,
        personalizationLogoSize: SwagLogoSize?,
        supporterName: String?,
        supporterEmail: String?,
    ): OrderCheckout {
        val public = athleteStorefrontService.getPublic(storefrontSlug)
        val storefront = public.storefront
        val approvedProductIds = athleteStorefrontService.getPublicProducts(storefront).map { it.first.id }.toSet()

        val variant =
            productVariantRepository
                .findById(productVariantId, storefront.organizationId)
                ?.takeIf { it.isActive }
                ?: throw NotFoundException("PRODUCT_VARIANT_NOT_FOUND", "The selected apparel type could not be found.")
        val product =
            productRepository
                .findById(variant.productId, storefront.organizationId)
                ?.takeIf { it.status == ProductStatus.ACTIVE }
                ?: throw NotFoundException("PRODUCT_NOT_FOUND", "The selected apparel type could not be found.")
        if (product.id !in approvedProductIds) {
            throw ValidationException("This item isn't offered on this storefront.")
        }
        val store =
            storeRepository.findById(storefront.storeId, storefront.organizationId)
                ?: throw NotFoundException("STORE_NOT_FOUND", "The Swag Shop could not be found.")
        if (store.status != StoreStatus.ACTIVE) {
            throw ValidationException("This Swag Shop isn't currently open for orders.")
        }

        val isPersonalized = personalizationName != null || personalizationNumber != null || personalizationPlacement != null
        if (isPersonalized) {
            if (product.swagLogoMediaAssetId == null) {
                throw ValidationException("This apparel type isn't set up for personalization yet — ask staff to add the team logo.")
            }
            if (variant.printAreaWidthPx == null || variant.printAreaHeightPx == null) {
                throw ValidationException("This apparel type is missing print-area dimensions — ask staff to re-create the variant.")
            }
            if (personalizationPlacement == PersonalizationPlacement.BACK &&
                (variant.backPrintAreaWidthPx == null || variant.backPrintAreaHeightPx == null)
            ) {
                throw ValidationException("This apparel type doesn't support back placement yet — ask staff to re-create the variant.")
            }
        }

        val participant =
            participantRepository.findById(storefront.participantId, storefront.organizationId)
                ?: error("Athlete storefront ${storefront.id} references a missing participant ${storefront.participantId}")

        return try {
            val order =
                orderRepository.insertPending(
                    storefront.organizationId,
                    store.id,
                    variant.currency,
                    supporterName,
                    supporterEmail,
                    attributedHouseholdId = participant.householdId,
                )
            orderItemRepository.insert(
                orderId = order.id,
                productVariantId = variant.id,
                quantity = 1,
                unitPriceMinor = variant.priceMinor,
                unitCostMinor = variant.costMinor,
                participantId = participant.id,
                personalizationName = personalizationName,
                personalizationNumber = personalizationNumber,
                personalizationPlacement = personalizationPlacement,
                personalizationLogoSize = personalizationLogoSize,
            )
            val lineItems =
                listOf(
                    OrderCheckoutLineItem(
                        name = "${product.name} - ${variant.label}",
                        quantity = 1,
                        unitPriceMinor = variant.priceMinor,
                        currency = variant.currency,
                    ),
                )
            val successUrl =
                @Suppress("ktlint:standard:max-line-length")
                "${frontendProperties.baseUrl}/swag-shop/athlete/$storefrontSlug?orderId=${order.id}&status=success"
            val cancelUrl = "${frontendProperties.baseUrl}/swag-shop/athlete/$storefrontSlug?status=canceled"
            val session = stripeOrderCheckoutClient.createOrderCheckoutSession(order.id, lineItems, successUrl, cancelUrl)
            orderRepository.attachStripeSession(order.id, session.sessionId)
            OrderCheckout(order.id, session.checkoutUrl)
        } catch (e: StripeException) {
            log.warn("Stripe athlete storefront checkout session creation failed: {}", e.message, e)
            throw ServiceUnavailableException(
                "ORDER_PROVIDER_UNAVAILABLE",
                "Payments provider is not available right now. If this is local/staging, confirm STRIPE_SECRET_KEY is set.",
            )
        }
    }

    fun getAthleteStorefrontOrderStatus(
        storefrontSlug: String,
        orderId: UUID,
    ): Order {
        val storefront = athleteStorefrontService.getPublic(storefrontSlug).storefront
        return orderRepository
            .findById(orderId, storefront.organizationId)
            ?.takeIf { it.storeId == storefront.storeId }
            ?: throw NotFoundException("ORDER_NOT_FOUND", "The order could not be found.")
    }

    /**
     * Swag Shop "my past orders" — every confirmed order_item for a participant the
     * caller could place a Swag Shop order for today (same reach as
     * requireSwagShopOrderAccess: their own household's participants, plus any team's
     * roster they hold TEAM_ORDER_CREATE for), newest first. Self-scoping by
     * construction — there's no caller-supplied household/team id to guard against,
     * the participant set is derived entirely from the caller's own contexts.
     */
    fun listMySwagShopOrders(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): List<SwagShopOrderHistoryItem> {
        val contexts = authorizationService.listContexts(currentUser).filter { it.organizationId == organizationId }
        val householdIds = contexts.filter { it.contextType == ContextType.HOUSEHOLD }.mapNotNull { it.resourceId }
        val orderTeamIds =
            contexts
                .filter { it.contextType == ContextType.TEAM && Capabilities.TEAM_ORDER_CREATE in it.capabilities }
                .mapNotNull { it.resourceId }

        val participantIds =
            (
                householdIds.flatMap { participantRepository.findByHousehold(it, organizationId) } +
                    orderTeamIds.flatMap { participantRepository.findActiveByTeam(it, organizationId) }
            ).map { it.id }.distinct()

        return orderItemRepository.findConfirmedByParticipants(organizationId, participantIds).mapNotNull { row ->
            val participant =
                participantRepository.findById(row.item.participantId ?: return@mapNotNull null, organizationId) ?: return@mapNotNull null
            val variant = productVariantRepository.findById(row.item.productVariantId, organizationId) ?: return@mapNotNull null
            val product = productRepository.findById(variant.productId, organizationId) ?: return@mapNotNull null
            val store = storeRepository.findById(row.storeId, organizationId)
            SwagShopOrderHistoryItem(
                orderId = row.orderId,
                confirmedAt = row.confirmedAt,
                participantId = participant.id,
                participantName = "${participant.firstName} ${participant.lastName}",
                productId = product.id,
                productName = product.name,
                variantId = variant.id,
                variantLabel = variant.label,
                size = variant.size,
                color = variant.color,
                mockupFrontUrl = variant.mockupFrontUrl,
                personalizationName = row.item.personalizationName,
                personalizationNumber = row.item.personalizationNumber,
                personalizationPlacement = row.item.personalizationPlacement,
                personalizationLogoSize = row.item.personalizationLogoSize,
                unitPriceMinor = row.item.unitPriceMinor,
                currency = row.currency,
                isReorderable = variant.isActive && product.status == ProductStatus.ACTIVE && store?.status == StoreStatus.ACTIVE,
            )
        }
    }

    /**
     * A guardian may order for their own household's participant (real relationship
     * check, or org owner/admin acting on any household — mirrors
     * EventRsvpService.resolveSource's deliberate avoidance of the broader
     * hasHouseholdCapability "any active org member" branch for this same class of
     * impersonation-risk action). A coach may order for any participant on a team
     * they hold TEAM_ORDER_CREATE for.
     */
    private fun requireSwagShopOrderAccess(
        organizationId: UUID,
        householdId: UUID,
        participantId: UUID,
        currentUser: CurrentUser,
    ) {
        val asGuardian =
            authorizationService.hasGuardianRelationship(organizationId, householdId, currentUser) ||
                authorizationService.hasHouseholdCapability(organizationId, householdId, currentUser, Capabilities.HOUSEHOLD_ORDER_CREATE)
        if (asGuardian) return
        val participantTeamIds = participantRepository.listTeamAssignments(participantId, organizationId).map { it.teamId }
        val asCoach =
            participantTeamIds.any {
                authorizationService.hasTeamCapability(organizationId, it, currentUser, Capabilities.TEAM_ORDER_CREATE)
            }
        if (asCoach) return
        throw ForbiddenException("SWAG_SHOP_ORDER_ACCESS_DENIED", "You do not have access to order for this athlete.")
    }

    /**
     * A vendor's catalog can change after a product was first set up — most concretely,
     * when a family reorders an item weeks or months later and Printify's print
     * provider no longer carries the exact blueprint+variant combination. Reuses the
     * same read-only catalog lookup `VendorSelectionService` already calls at
     * product-setup time; runs on every checkout (not reorder-only), since it's one
     * cheap read call and protects a first-time order just as well as a reorder.
     */
    private fun requireVariantStillAvailable(
        product: Product,
        variant: ProductVariant,
    ) {
        val blueprintId = product.printifyBlueprintId
        val printProviderId = variant.printifyPrintProviderId
        val printifyVariantId = variant.printifyVariantId
        if (blueprintId == null || printProviderId == null || printifyVariantId == null) return
        val stillOffered = printifyCatalogClient.listVariants(blueprintId, printProviderId).any { it.id == printifyVariantId }
        if (!stillOffered) {
            throw ConflictException(
                "PRINTIFY_VARIANT_UNAVAILABLE",
                "The vendor no longer carries this exact item. It may need to be re-made through a different vendor.",
            )
        }
    }

    /** Idempotent: a duplicate webhook delivery or an already-confirmed order is a safe no-op. */
    @Transactional
    fun confirmFromWebhook(
        stripeSessionId: String,
        stripePaymentStatus: String,
        shippingAddress: ShippingAddress?,
        stripePaymentIntentId: String?,
    ): Order? {
        val order = orderRepository.findByStripeCheckoutSessionId(stripeSessionId) ?: return null
        if (stripePaymentStatus != "paid") return order
        val updated = orderRepository.markConfirmed(order.id, shippingAddress, stripePaymentIntentId)
        if (updated > 0) {
            auditService.record(null, order.organizationId, "order.confirmed", "order", order.id)
            val items = orderItemRepository.findByOrder(order.id)
            ledgerService.recordConfirmedOrder(order.copy(status = OrderStatus.CONFIRMED), items)
            ledgerService.recordStripeProcessingFee(
                order.organizationId,
                LedgerSourceType.ORDER,
                order.id,
                order.currency,
                stripePaymentIntentId,
            )
            createInitialFulfillment(order.id, order.organizationId, order.storeId)
            val totalMinor = items.sumOf { it.unitPriceMinor * it.quantity }
            if (order.supporterEmail != null) {
                val store = storeRepository.findById(order.storeId, order.organizationId)
                outboxWriter.write(
                    aggregateType = "order",
                    aggregateId = order.id,
                    organizationId = order.organizationId,
                    eventType = "order.confirmed",
                    payloadJson =
                        objectMapper.writeValueAsString(
                            OrderConfirmedPayload(
                                order.supporterEmail,
                                order.supporterName,
                                totalMinor,
                                order.currency,
                                store?.slug ?: "",
                                Instant.now().toString(),
                            ),
                        ),
                )
            }
            // Phase 24 slice 24.3: only set for an order placed through a published
            // athlete storefront — a regular authenticated Swag Shop order never
            // grants credit (§13's "storefront revenue model" scopes credit-granting
            // specifically to storefront-attributed swag sales).
            if (order.attributedHouseholdId != null) {
                familyCreditService.grantForStorefrontOrder(
                    order.organizationId,
                    order.attributedHouseholdId,
                    order.id,
                    totalMinor,
                    order.currency,
                )
            }
        }
        return orderRepository.findById(order.id, order.organizationId)
    }

    fun getStatus(
        storeSlug: String,
        orderId: UUID,
    ): Order {
        val store =
            storeRepository.findBySlug(storeSlug)
                ?: throw NotFoundException("STORE_NOT_FOUND", "The store could not be found.")
        val order =
            orderRepository
                .findById(orderId, store.organizationId)
                ?.takeIf { it.storeId == store.id }
                ?: throw NotFoundException("ORDER_NOT_FOUND", "The order could not be found.")
        return order
    }

    fun listForStore(
        organizationId: UUID,
        storeId: UUID,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<Order> {
        membershipService.requireManagerRole(organizationId, currentUser)
        storeRepository.findById(storeId, organizationId) ?: throw NotFoundException("STORE_NOT_FOUND", "The store could not be found.")
        return orderRepository.findByStore(storeId, offset, limit)
    }

    fun getConfirmedCount(
        organizationId: UUID,
        storeId: UUID,
        currentUser: CurrentUser,
    ): Long {
        membershipService.requireManagerRole(organizationId, currentUser)
        return orderRepository.countConfirmedByStore(storeId)
    }

    fun getFulfillment(
        organizationId: UUID,
        orderId: UUID,
        currentUser: CurrentUser,
    ): Fulfillment? {
        membershipService.requireManagerRole(organizationId, currentUser)
        orderRepository.findById(orderId, organizationId) ?: throw NotFoundException("ORDER_NOT_FOUND", "The order could not be found.")
        return fulfillmentRepository.findByOrder(orderId)
    }

    /** Refunds the gross sale amount; production cost already spent with Printify is not returned (see LedgerService.recordRefund). */
    @Transactional
    fun refund(
        organizationId: UUID,
        orderId: UUID,
        currentUser: CurrentUser,
    ): Order {
        membershipService.requireManagerRole(organizationId, currentUser)
        val order =
            orderRepository.findById(orderId, organizationId)
                ?: throw NotFoundException("ORDER_NOT_FOUND", "The order could not be found.")
        if (order.status != OrderStatus.CONFIRMED || order.stripePaymentIntentId == null) {
            throw ValidationException("Only a confirmed order with a recorded payment can be refunded.")
        }
        val confirmedAt = order.confirmedAt ?: throw ValidationException("This order has no confirmation date on record.")
        if (Duration.between(confirmedAt, Instant.now()) > ORDER_REFUND_WINDOW) {
            throw ValidationException(
                "This order can no longer be refunded — it was confirmed more than ${ORDER_REFUND_WINDOW.toDays()} days ago.",
            )
        }
        val stripeRefundId =
            try {
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
        if (order.attributedHouseholdId != null) {
            familyCreditService.reverseForRefundedOrder(organizationId, order.id)
        }
        return orderRepository.findById(order.id, organizationId)!!
    }

    private fun createInitialFulfillment(
        orderId: UUID,
        organizationId: UUID,
        storeId: UUID,
    ) {
        val items = orderItemRepository.findByOrder(orderId)
        val resolved =
            items.map { item ->
                val variant =
                    productVariantRepository.findById(item.productVariantId, organizationId)
                        ?: error("order_item ${item.id} references a missing product_variant")
                val product =
                    productRepository.findById(variant.productId, organizationId)
                        ?: error("product_variant ${variant.id} references a missing product")
                Triple(item, product, variant)
            }
        val source =
            resolved.map { it.second.catalogSource }.distinct().singleOrNull()
                ?: error("order $orderId contains mixed catalog sources")
        if (source == CatalogSource.MANUAL) {
            val vendorId = resolved.map { it.second.manualVendorId }.distinct().singleOrNull()
            val fulfillment =
                fulfillmentRepository.insert(
                    orderId = orderId,
                    source = FulfillmentSource.MANUAL,
                    status = FulfillmentStatus.READY,
                    printifyOrderId = null,
                    manualVendorId = vendorId,
                    lastError = null,
                )
            fulfillmentHistoryRepository.insert(
                organizationId,
                fulfillment.id,
                null,
                fulfillment.status,
                "Manual fulfillment created after payment confirmation.",
                null,
            )
            return
        }

        try {
            val lineItems =
                resolved.map { (item, product, variant) ->
                    val isPersonalized =
                        item.personalizationName != null || item.personalizationNumber != null || item.personalizationPlacement != null
                    // Swag Shop (DESIGN-DOC.md section 13): a personalized item's print file is
                    // composited fresh per order (logo + name/number); a non-personalized item
                    // reuses today's unchanged static per-product design, exactly as before.
                    // BACK placement (2026-08-05) submits a real second print position — the
                    // buyer's name/number physically prints on the garment's back, not a
                    // back-style layout on the front.
                    val printAreaImagesByPosition =
                        if (isPersonalized) {
                            val swagLogoMediaAssetId =
                                product.swagLogoMediaAssetId ?: error("product ${product.id} has no Swag Shop logo assigned")
                            val widthPx = variant.printAreaWidthPx ?: error("product_variant ${variant.id} has no print-area width")
                            val heightPx = variant.printAreaHeightPx ?: error("product_variant ${variant.id} has no print-area height")
                            val result =
                                swagDesignCompositor.compose(
                                    organizationId = organizationId,
                                    orderId = orderId,
                                    orderItemId = item.id,
                                    swagLogoMediaAssetId = swagLogoMediaAssetId,
                                    printAreaWidthPx = widthPx,
                                    printAreaHeightPx = heightPx,
                                    backPrintAreaWidthPx = variant.backPrintAreaWidthPx,
                                    backPrintAreaHeightPx = variant.backPrintAreaHeightPx,
                                    personalizationName = item.personalizationName,
                                    personalizationNumber = item.personalizationNumber,
                                    personalizationPlacement = item.personalizationPlacement,
                                    personalizationLogoSize = item.personalizationLogoSize,
                                )
                            buildMap {
                                put(product.printifyPrintPosition, result.frontUrl)
                                if (result.backUrl != null) put("back", result.backUrl)
                            }
                        } else {
                            val designAssignment =
                                mediaAssignmentService.getActiveAssignment(
                                    MediaEntityType.PRODUCT,
                                    product.id,
                                    MediaUsageSlot.PRODUCT_DESIGN,
                                )
                                    ?: error("product ${product.id} has no design assigned")
                            val designUrl =
                                mediaReadService.describe(designAssignment)?.url
                                    ?: error("product ${product.id}'s design asset could not be found")
                            mapOf(product.printifyPrintPosition to designUrl)
                        }
                    PrintifyOrderLineItem(
                        printifyBlueprintId = product.printifyBlueprintId ?: error("PRINTIFY product ${product.id} has no blueprint ID"),
                        printifyPrintProviderId =
                            variant.printifyPrintProviderId ?: error("PRINTIFY variant ${variant.id} has no provider ID"),
                        printifyVariantId = variant.printifyVariantId ?: error("PRINTIFY variant ${variant.id} has no variant ID"),
                        quantity = item.quantity,
                        printAreaImagesByPosition = printAreaImagesByPosition,
                    )
                }
            // Phase 24 slice 24.4 (ADR-070): internal org/store traceability prefix on
            // the external_id Printify sees — never shown to a buyer or admin in our
            // own UI.
            val externalId = printifyOwnershipPrefixService.orderExternalId(organizationId, storeId, orderId)
            val draftOrder = printifyOrderClient.createDraftOrder(externalId, lineItems)
            val fulfillment =
                fulfillmentRepository.insert(
                    orderId,
                    FulfillmentSource.PRINTIFY,
                    FulfillmentStatus.DRAFT_CREATED,
                    draftOrder.printifyOrderId,
                    null,
                    null,
                    printifyShopId = printifyProperties.shopId,
                )
            fulfillmentHistoryRepository.insert(
                organizationId,
                fulfillment.id,
                null,
                fulfillment.status,
                "Printify draft order created after payment confirmation.",
                null,
            )
        } catch (e: RestClientException) {
            recordPrintifyFailure(orderId, organizationId, e.message ?: e.javaClass.simpleName, e)
        } catch (e: Exception) {
            recordPrintifyFailure(orderId, organizationId, e.message ?: e.javaClass.simpleName, e)
        }
    }

    private fun recordPrintifyFailure(
        orderId: UUID,
        organizationId: UUID,
        message: String,
        exception: Exception,
    ) {
        log.error("Fulfillment submission failed for order {}: {}", orderId, message, exception)
        val fulfillment =
            fulfillmentRepository.insert(
                orderId,
                FulfillmentSource.PRINTIFY,
                FulfillmentStatus.FAILED,
                null,
                null,
                message,
            )
        fulfillmentHistoryRepository.insert(
            organizationId,
            fulfillment.id,
            null,
            fulfillment.status,
            "Printify draft-order creation failed; payment remains confirmed.",
            null,
        )
    }
}
