package com.rally26.order.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.AuthorizationContext
import com.rally26.authorization.domain.Capabilities
import com.rally26.authorization.domain.ContextType
import com.rally26.common.error.ConflictException
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.config.FrontendProperties
import com.rally26.config.PrintifyProperties
import com.rally26.credit.application.FamilyCreditService
import com.rally26.integration.printify.application.PrintifyOwnershipPrefixService
import com.rally26.integration.printify.infra.PrintifyCatalogClient
import com.rally26.integration.printify.infra.PrintifyCatalogVariant
import com.rally26.integration.printify.infra.PrintifyDraftOrder
import com.rally26.integration.printify.infra.PrintifyOrderClient
import com.rally26.integration.printify.infra.PrintifyOrderLineItem
import com.rally26.ledger.application.LedgerService
import com.rally26.ledger.domain.LedgerSourceType
import com.rally26.media.application.MediaAssignmentService
import com.rally26.media.application.MediaReadService
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.order.domain.Fulfillment
import com.rally26.order.domain.FulfillmentHistory
import com.rally26.order.domain.FulfillmentSource
import com.rally26.order.domain.FulfillmentStatus
import com.rally26.order.domain.Order
import com.rally26.order.domain.OrderItem
import com.rally26.order.domain.OrderStatus
import com.rally26.order.domain.PersonalizationPlacement
import com.rally26.order.domain.SwagLogoSize
import com.rally26.order.infra.OrderCheckoutSession
import com.rally26.order.infra.StripeOrderCheckoutClient
import com.rally26.order.persistence.FulfillmentHistoryRepository
import com.rally26.order.persistence.FulfillmentRepository
import com.rally26.order.persistence.OrderItemRepository
import com.rally26.order.persistence.OrderItemWithOrder
import com.rally26.order.persistence.OrderRepository
import com.rally26.outbox.application.OutboxWriter
import com.rally26.participant.domain.Participant
import com.rally26.participant.domain.ParticipantStatus
import com.rally26.participant.persistence.ParticipantRepository
import com.rally26.store.application.AthleteStorefrontPublic
import com.rally26.store.application.AthleteStorefrontService
import com.rally26.store.application.SwagCompositeResult
import com.rally26.store.application.SwagDesignCompositor
import com.rally26.store.domain.AthleteStorefront
import com.rally26.store.domain.AthleteStorefrontStatus
import com.rally26.store.domain.CatalogSource
import com.rally26.store.domain.Product
import com.rally26.store.domain.ProductStatus
import com.rally26.store.domain.ProductVariant
import com.rally26.store.domain.Store
import com.rally26.store.domain.StoreStatus
import com.rally26.store.persistence.ProductRepository
import com.rally26.store.persistence.ProductVariantRepository
import com.rally26.store.persistence.StoreRepository
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
    private val swagDesignCompositor = mockk<SwagDesignCompositor>()
    private val participantRepository = mockk<ParticipantRepository>()
    private val authorizationService = mockk<AuthorizationService>()
    private val frontendProperties = FrontendProperties()
    private val membershipService = mockk<MembershipService>()
    private val auditService = mockk<AuditService>()
    private val ledgerService = mockk<LedgerService>()
    private val outboxWriter = mockk<OutboxWriter>()
    private val athleteStorefrontService = mockk<AthleteStorefrontService>()
    private val familyCreditService = mockk<FamilyCreditService>()
    private val printifyOwnershipPrefixService = mockk<PrintifyOwnershipPrefixService>()
    private val printifyProperties = PrintifyProperties(apiToken = "test-token", shopId = "shop_123")
    private val printifyCatalogClient = mockk<PrintifyCatalogClient>()
    private val service =
        OrderService(
            orderRepository,
            orderItemRepository,
            fulfillmentRepository,
            fulfillmentHistoryRepository,
            storeRepository,
            productRepository,
            productVariantRepository,
            stripeOrderCheckoutClient,
            printifyOrderClient,
            mediaAssignmentService,
            mediaReadService,
            swagDesignCompositor,
            participantRepository,
            authorizationService,
            frontendProperties,
            membershipService,
            auditService,
            ledgerService,
            outboxWriter,
            ObjectMapper(),
            athleteStorefrontService,
            familyCreditService,
            printifyOwnershipPrefixService,
            printifyProperties,
            printifyCatalogClient,
        )
    private val orgId = UUID.randomUUID()

    /** The default `product()`/`productVariant()` fixtures are always PRINTIFY-sourced with real blueprint/print-provider/variant ids — stub the catalog as still offering that exact combination unless a test needs otherwise. */
    private fun stubVariantStillAvailable() {
        every { printifyCatalogClient.listVariants(12L, 5L) } returns
            listOf(PrintifyCatalogVariant(id = 100L, title = "M / Navy", options = null, placeholders = null))
    }

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
            service.createCheckoutSession(
                "nope",
                listOf(OrderLineItemRequest(UUID.randomUUID(), 1)),
                null,
                null,
                "https://x/success",
                "https://x/cancel",
            )
        }
    }

    @Test
    fun `createCheckoutSession rejects a store that is not ACTIVE`() {
        every { storeRepository.findBySlug("spring-store") } returns store(status = StoreStatus.DRAFT)
        assertFailsWith<ValidationException> {
            service.createCheckoutSession(
                "spring-store",
                listOf(OrderLineItemRequest(UUID.randomUUID(), 1)),
                null,
                null,
                "https://x/success",
                "https://x/cancel",
            )
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

        val result =
            service.createCheckoutSession(
                "spring-store",
                listOf(OrderLineItemRequest(variant.id, 2)),
                "Jane Doe",
                null,
                "https://x/success",
                "https://x/cancel",
            )

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
                null,
                null,
                "https://x/success",
                "https://x/cancel",
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
        val designAssignment = mockk<com.rally26.media.domain.MediaAssignment>()
        every {
            mediaAssignmentService.getActiveAssignment(
                com.rally26.media.domain.MediaEntityType.PRODUCT,
                product.id,
                com.rally26.media.domain.MediaUsageSlot.PRODUCT_DESIGN,
            )
        } returns designAssignment
        every { mediaReadService.describe(designAssignment) } returns
            mockk { every { url } returns "https://signed.example.com/design.png" }
        every { printifyOwnershipPrefixService.orderExternalId(orgId, store.id, order.id) } returns
            "riverside-soccer/spring-store:${order.id}"
        every { printifyOrderClient.createDraftOrder("riverside-soccer/spring-store:${order.id}", any()) } returns
            PrintifyDraftOrder("printify_order_1")
        val fulfillment = fulfillment(order.id, FulfillmentSource.PRINTIFY, FulfillmentStatus.DRAFT_CREATED, "printify_order_1")
        every {
            fulfillmentRepository.insert(
                order.id,
                FulfillmentSource.PRINTIFY,
                FulfillmentStatus.DRAFT_CREATED,
                "printify_order_1",
                null,
                null,
                printifyShopId = "shop_123",
            )
        } returns fulfillment
        every { fulfillmentHistoryRepository.insert(orgId, fulfillment.id, null, FulfillmentStatus.DRAFT_CREATED, any(), null) } returns
            history(fulfillment)

        val result = service.confirmFromWebhook("cs_test_123", "paid", null, null)

        assertEquals(OrderStatus.CONFIRMED, result?.status)
        verify(
            exactly = 1,
        ) { fulfillmentHistoryRepository.insert(orgId, fulfillment.id, null, FulfillmentStatus.DRAFT_CREATED, any(), null) }
    }

    @Test
    fun `confirmFromWebhook submits a real second print position for BACK-placed personalization`() {
        val store = store()
        val product = product(store.id).copy(swagLogoMediaAssetId = UUID.randomUUID(), printifyPrintPosition = "front")
        val variant =
            productVariant(product.id).copy(
                printAreaWidthPx = 1000,
                printAreaHeightPx = 1000,
                backPrintAreaWidthPx = 800,
                backPrintAreaHeightPx = 900,
            )
        val order = pendingOrder(store)
        val confirmed = order.copy(status = OrderStatus.CONFIRMED, confirmedAt = Instant.now())
        val participantId = UUID.randomUUID()
        every { orderRepository.findByStripeCheckoutSessionId("cs_test_123") } returns order
        every { orderRepository.markConfirmed(order.id, null, null) } returns 1
        every { auditService.record(null, orgId, "order.confirmed", "order", order.id) } just runs
        every { orderItemRepository.findByOrder(order.id) } returns
            listOf(
                OrderItem(
                    UUID.randomUUID(),
                    order.id,
                    variant.id,
                    1,
                    variant.priceMinor,
                    variant.costMinor,
                    participantId,
                    "Johnson",
                    "7",
                    PersonalizationPlacement.BACK,
                ),
            )
        every { ledgerService.recordConfirmedOrder(any(), any()) } just runs
        every { ledgerService.recordStripeProcessingFee(any(), any(), any(), any(), any()) } just runs
        every { productVariantRepository.findById(variant.id, orgId) } returns variant
        every { productRepository.findById(product.id, orgId) } returns product
        every { orderRepository.findById(order.id, orgId) } returns confirmed
        every {
            swagDesignCompositor.compose(
                organizationId = orgId,
                orderId = order.id,
                orderItemId = any(),
                swagLogoMediaAssetId = product.swagLogoMediaAssetId!!,
                printAreaWidthPx = 1000,
                printAreaHeightPx = 1000,
                backPrintAreaWidthPx = 800,
                backPrintAreaHeightPx = 900,
                personalizationName = "Johnson",
                personalizationNumber = "7",
                personalizationPlacement = PersonalizationPlacement.BACK,
            )
        } returns SwagCompositeResult("https://signed.example.com/front.png", "https://signed.example.com/back.png")
        every { printifyOwnershipPrefixService.orderExternalId(orgId, store.id, order.id) } returns
            "riverside-soccer/spring-store:${order.id}"
        val lineItems = slot<List<PrintifyOrderLineItem>>()
        every { printifyOrderClient.createDraftOrder("riverside-soccer/spring-store:${order.id}", capture(lineItems)) } returns
            PrintifyDraftOrder("printify_order_1")
        val fulfillment = fulfillment(order.id, FulfillmentSource.PRINTIFY, FulfillmentStatus.DRAFT_CREATED, "printify_order_1")
        every {
            fulfillmentRepository.insert(
                order.id,
                FulfillmentSource.PRINTIFY,
                FulfillmentStatus.DRAFT_CREATED,
                "printify_order_1",
                null,
                null,
                printifyShopId = "shop_123",
            )
        } returns fulfillment
        every { fulfillmentHistoryRepository.insert(orgId, fulfillment.id, null, FulfillmentStatus.DRAFT_CREATED, any(), null) } returns
            history(fulfillment)

        service.confirmFromWebhook("cs_test_123", "paid", null, null)

        val printAreas = lineItems.captured.single().printAreaImagesByPosition
        assertEquals(2, printAreas.size)
        assertEquals("https://signed.example.com/front.png", printAreas["front"])
        assertEquals("https://signed.example.com/back.png", printAreas["back"])
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
        every {
            fulfillmentRepository.insert(
                order.id,
                FulfillmentSource.MANUAL,
                FulfillmentStatus.READY,
                null,
                vendorId,
                null,
                printifyShopId = null,
            )
        } returns fulfillment
        every { fulfillmentHistoryRepository.insert(orgId, fulfillment.id, null, FulfillmentStatus.READY, any(), null) } returns
            history(fulfillment)

        val result = service.confirmFromWebhook("cs_test_123", "paid", null, null)

        assertEquals(OrderStatus.CONFIRMED, result?.status)
        verify(exactly = 0) { printifyOrderClient.createDraftOrder(any(), any()) }
        verify(exactly = 1) {
            fulfillmentRepository.insert(
                order.id,
                FulfillmentSource.MANUAL,
                FulfillmentStatus.READY,
                null,
                vendorId,
                null,
                printifyShopId = null,
            )
        }
    }

    @Test
    fun `confirmFromWebhook keeps order CONFIRMED when Printify draft creation fails`() {
        val store = store()
        val product = product(store.id)
        val variant = productVariant(product.id)
        val order = pendingOrder(store)
        val confirmed = order.copy(status = OrderStatus.CONFIRMED, confirmedAt = Instant.now())
        stubConfirmation(order, confirmed, product, variant)
        val designAssignment = mockk<com.rally26.media.domain.MediaAssignment>()
        every {
            mediaAssignmentService.getActiveAssignment(
                com.rally26.media.domain.MediaEntityType.PRODUCT,
                product.id,
                com.rally26.media.domain.MediaUsageSlot.PRODUCT_DESIGN,
            )
        } returns designAssignment
        every { mediaReadService.describe(designAssignment) } returns
            mockk { every { url } returns "https://signed.example.com/design.png" }
        every { printifyOwnershipPrefixService.orderExternalId(orgId, store.id, order.id) } returns
            "riverside-soccer/spring-store:${order.id}"
        every { printifyOrderClient.createDraftOrder("riverside-soccer/spring-store:${order.id}", any()) } throws
            org.springframework.web.client
                .RestClientException("Printify is unreachable")
        val fulfillment = fulfillment(order.id, FulfillmentSource.PRINTIFY, FulfillmentStatus.FAILED, lastError = "Printify is unreachable")
        every {
            fulfillmentRepository.insert(
                order.id,
                FulfillmentSource.PRINTIFY,
                FulfillmentStatus.FAILED,
                null,
                null,
                "Printify is unreachable",
                printifyShopId = null,
            )
        } returns fulfillment
        every { fulfillmentHistoryRepository.insert(orgId, fulfillment.id, null, FulfillmentStatus.FAILED, any(), null) } returns
            history(fulfillment)

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
        every {
            fulfillmentRepository.insert(
                order.id,
                FulfillmentSource.MANUAL,
                FulfillmentStatus.READY,
                null,
                null,
                null,
                printifyShopId = null,
            )
        } returns
            fulfillment
        every { fulfillmentHistoryRepository.insert(orgId, fulfillment.id, null, FulfillmentStatus.READY, any(), null) } returns
            history(fulfillment)
        val payload = slot<String>()
        every { outboxWriter.write("order", order.id, orgId, "order.confirmed", capture(payload)) } just runs
        every { storeRepository.findById(order.storeId, orgId) } returns store

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
        val order =
            pendingOrder(
                store,
            ).copy(status = OrderStatus.CONFIRMED, stripePaymentIntentId = "pi_test_123", confirmedAt = Instant.now())
        val manager = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")
        every { membershipService.requireManagerRole(orgId, manager) } returns managerMembership(manager)
        every { orderRepository.findById(order.id, orgId) } returns order
        every { orderItemRepository.findByOrder(order.id) } returns
            listOf(OrderItem(UUID.randomUUID(), order.id, UUID.randomUUID(), 2, 2500L, 1200L))
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
        val order =
            pendingOrder(
                store(),
            ).copy(
                status = OrderStatus.CONFIRMED,
                stripePaymentIntentId = "pi_test_123",
                confirmedAt = Instant.now().minus(Duration.ofDays(15)),
            )
        val manager = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")
        every { membershipService.requireManagerRole(orgId, manager) } returns managerMembership(manager)
        every { orderRepository.findById(order.id, orgId) } returns order
        assertFailsWith<ValidationException> { service.refund(orgId, order.id, manager) }
    }

    private fun stubConfirmation(
        order: Order,
        confirmed: Order,
        product: Product,
        variant: ProductVariant,
    ) {
        every { orderRepository.findByStripeCheckoutSessionId("cs_test_123") } returns order
        every { orderRepository.markConfirmed(order.id, null, null) } returns 1
        every { auditService.record(null, orgId, "order.confirmed", "order", order.id) } just runs
        every { orderItemRepository.findByOrder(order.id) } returns
            listOf(OrderItem(UUID.randomUUID(), order.id, variant.id, 1, variant.priceMinor, variant.costMinor))
        every { ledgerService.recordConfirmedOrder(any(), any()) } just runs
        every { ledgerService.recordStripeProcessingFee(any(), any(), any(), any(), any()) } just runs
        every { productVariantRepository.findById(variant.id, orgId) } returns variant
        every { productRepository.findById(product.id, orgId) } returns product
        every { orderRepository.findById(order.id, orgId) } returns confirmed
    }

    private fun history(fulfillment: Fulfillment) =
        FulfillmentHistory(UUID.randomUUID(), orgId, fulfillment.id, null, fulfillment.status, "created", null, Instant.now())

    private fun fulfillment(
        orderId: UUID,
        source: FulfillmentSource,
        status: FulfillmentStatus,
        printifyOrderId: String? = null,
        manualVendorId: UUID? = null,
        lastError: String? = null,
    ) = Fulfillment(
        id = UUID.randomUUID(),
        orderId = orderId,
        source = source,
        status = status,
        printifyOrderId = printifyOrderId,
        manualVendorId = manualVendorId,
        manualVendorName = null,
        vendorOrderReference = null,
        carrier = null,
        trackingNumber = null,
        trackingUrl = null,
        internalNotes = null,
        attentionReason = null,
        lastError = lastError,
        statusChangedAt = Instant.now(),
        shippedAt = null,
        deliveredAt = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun managerMembership(manager: CurrentUser) =
        OrganizationMembership(
            id = UUID.randomUUID(),
            organizationId = orgId,
            userId = manager.userId,
            role = MembershipRole.ADMINISTRATOR,
            status = MembershipStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun store(status: StoreStatus = StoreStatus.ACTIVE) =
        Store(
            id = UUID.randomUUID(),
            organizationId = orgId,
            teamId = null,
            name = "Spring Store",
            slug = "spring-store",
            status = status,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun product(
        storeId: UUID,
        source: CatalogSource = CatalogSource.PRINTIFY,
        vendorId: UUID? = null,
    ) = Product(
        id = UUID.randomUUID(),
        organizationId = orgId,
        storeId = storeId,
        name = "Team Hoodie",
        description = null,
        catalogSource = source,
        manualVendorId = vendorId,
        manualVendorName = null,
        printifyBlueprintId = if (source == CatalogSource.PRINTIFY) 12L else null,
        printifyImageId = if (source == CatalogSource.PRINTIFY) "img_1" else null,
        printifyPrintPosition = "front",
        status = ProductStatus.ACTIVE,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun productVariant(
        productId: UUID,
        source: CatalogSource = CatalogSource.PRINTIFY,
    ) = ProductVariant(
        id = UUID.randomUUID(),
        organizationId = orgId,
        productId = productId,
        catalogSource = source,
        label = "M / Navy",
        sku = if (source == CatalogSource.MANUAL) "M-NAVY" else null,
        size = "M",
        color = "Navy",
        printifyPrintProviderId = if (source == CatalogSource.PRINTIFY) 5L else null,
        printifyVariantId = if (source == CatalogSource.PRINTIFY) 100L else null,
        currency = "USD",
        costMinor = 1200L,
        priceMinor = 2500L,
        isActive = true,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun pendingOrder(store: Store) =
        Order(
            id = UUID.randomUUID(),
            organizationId = orgId,
            storeId = store.id,
            status = OrderStatus.PENDING,
            currency = "USD",
            supporterName = "Jane Doe",
            supporterEmail = null,
            shippingAddress = null,
            stripeCheckoutSessionId = "cs_test_123",
            stripePaymentIntentId = null,
            confirmedAt = null,
            refundedAt = null,
            createdAt = Instant.now(),
        )

    private fun participant(householdId: UUID) =
        Participant(
            id = UUID.randomUUID(),
            householdId = householdId,
            organizationId = orgId,
            firstName = "Maya",
            lastName = "Johnson",
            dateOfBirth = null,
            notes = null,
            status = ParticipantStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    @Test
    fun `createSwagShopCheckoutSession succeeds for a real guardian ordering for their own household`() {
        val currentUser = CurrentUser(UUID.randomUUID(), "guardian@example.com", "Sarah Johnson")
        val householdId = UUID.randomUUID()
        val participant = participant(householdId)
        val storeEntity = store()
        val product = product(storeEntity.id)
        val variant =
            productVariant(product.id).copy(
                printAreaWidthPx = 1000,
                printAreaHeightPx = 1000,
                backPrintAreaWidthPx = 800,
                backPrintAreaHeightPx = 900,
            )
        every { participantRepository.findById(participant.id, orgId) } returns participant
        every { authorizationService.hasGuardianRelationship(orgId, householdId, currentUser) } returns true
        every { productVariantRepository.findById(variant.id, orgId) } returns variant
        every { productRepository.findById(product.id, orgId) } returns product.copy(swagLogoMediaAssetId = UUID.randomUUID())
        every { storeRepository.findById(product.storeId, orgId) } returns storeEntity
        stubVariantStillAvailable()
        every { orderRepository.insertPending(orgId, storeEntity.id, "USD", currentUser.displayName, currentUser.email) } returns
            pendingOrder(storeEntity)
        every {
            orderItemRepository.insert(
                any(),
                variant.id,
                1,
                variant.priceMinor,
                variant.costMinor,
                participant.id,
                "Johnson",
                "7",
                PersonalizationPlacement.BACK,
                SwagLogoSize.LARGE,
            )
        } returns mockk(relaxed = true)
        every { stripeOrderCheckoutClient.createOrderCheckoutSession(any(), any(), any(), any()) } returns
            OrderCheckoutSession("cs_test_1", "https://checkout.stripe.com/test")
        every { orderRepository.attachStripeSession(any(), "cs_test_1") } returns 1

        val result =
            service.createSwagShopCheckoutSession(
                orgId,
                variant.id,
                participant.id,
                "Johnson",
                "7",
                PersonalizationPlacement.BACK,
                SwagLogoSize.LARGE,
                currentUser,
            )

        assertEquals("https://checkout.stripe.com/test", result.checkoutUrl)
    }

    @Test
    fun `createSwagShopCheckoutSession succeeds with CENTER_FRONT placement`() {
        val currentUser = CurrentUser(UUID.randomUUID(), "guardian@example.com", "Sarah Johnson")
        val householdId = UUID.randomUUID()
        val participant = participant(householdId)
        val storeEntity = store()
        val product = product(storeEntity.id)
        val variant = productVariant(product.id).copy(printAreaWidthPx = 1000, printAreaHeightPx = 1000)
        every { participantRepository.findById(participant.id, orgId) } returns participant
        every { authorizationService.hasGuardianRelationship(orgId, householdId, currentUser) } returns true
        every { productVariantRepository.findById(variant.id, orgId) } returns variant
        every { productRepository.findById(product.id, orgId) } returns product.copy(swagLogoMediaAssetId = UUID.randomUUID())
        every { storeRepository.findById(product.storeId, orgId) } returns storeEntity
        stubVariantStillAvailable()
        every { orderRepository.insertPending(orgId, storeEntity.id, "USD", currentUser.displayName, currentUser.email) } returns
            pendingOrder(storeEntity)
        every {
            orderItemRepository.insert(
                any(),
                variant.id,
                1,
                variant.priceMinor,
                variant.costMinor,
                participant.id,
                "Johnson",
                "7",
                PersonalizationPlacement.CENTER_FRONT,
                SwagLogoSize.STANDARD,
            )
        } returns mockk(relaxed = true)
        every { stripeOrderCheckoutClient.createOrderCheckoutSession(any(), any(), any(), any()) } returns
            OrderCheckoutSession("cs_test_2", "https://checkout.stripe.com/test")
        every { orderRepository.attachStripeSession(any(), "cs_test_2") } returns 1

        val result =
            service.createSwagShopCheckoutSession(
                orgId,
                variant.id,
                participant.id,
                "Johnson",
                "7",
                PersonalizationPlacement.CENTER_FRONT,
                SwagLogoSize.STANDARD,
                currentUser,
            )

        assertEquals("https://checkout.stripe.com/test", result.checkoutUrl)
    }

    @Test
    fun `createSwagShopCheckoutSession rejects BACK placement when the variant has no back print-area dimensions`() {
        val currentUser = CurrentUser(UUID.randomUUID(), "guardian@example.com", "Sarah Johnson")
        val householdId = UUID.randomUUID()
        val participant = participant(householdId)
        val storeEntity = store()
        val product = product(storeEntity.id)
        val variant = productVariant(product.id).copy(printAreaWidthPx = 1000, printAreaHeightPx = 1000)
        every { participantRepository.findById(participant.id, orgId) } returns participant
        every { authorizationService.hasGuardianRelationship(orgId, householdId, currentUser) } returns true
        every { productVariantRepository.findById(variant.id, orgId) } returns variant
        every { productRepository.findById(product.id, orgId) } returns product.copy(swagLogoMediaAssetId = UUID.randomUUID())
        every { storeRepository.findById(product.storeId, orgId) } returns storeEntity
        stubVariantStillAvailable()

        assertFailsWith<ValidationException> {
            service.createSwagShopCheckoutSession(
                orgId,
                variant.id,
                participant.id,
                "Johnson",
                "7",
                PersonalizationPlacement.BACK,
                null,
                currentUser,
            )
        }
    }

    @Test
    fun `createSwagShopCheckoutSession proceeds when Printify's catalog still offers the exact blueprint, print provider, and variant`() {
        val currentUser = CurrentUser(UUID.randomUUID(), "guardian@example.com", "Sarah Johnson")
        val householdId = UUID.randomUUID()
        val participant = participant(householdId)
        val storeEntity = store()
        val product = product(storeEntity.id)
        val variant = productVariant(product.id)
        every { participantRepository.findById(participant.id, orgId) } returns participant
        every { authorizationService.hasGuardianRelationship(orgId, householdId, currentUser) } returns true
        every { productVariantRepository.findById(variant.id, orgId) } returns variant
        every { productRepository.findById(product.id, orgId) } returns product
        every { storeRepository.findById(product.storeId, orgId) } returns storeEntity
        stubVariantStillAvailable()
        every { orderRepository.insertPending(orgId, storeEntity.id, "USD", currentUser.displayName, currentUser.email) } returns
            pendingOrder(storeEntity)
        every {
            orderItemRepository.insert(any(), variant.id, 1, variant.priceMinor, variant.costMinor, participant.id, null, null, null, null)
        } returns
            mockk(relaxed = true)
        every { stripeOrderCheckoutClient.createOrderCheckoutSession(any(), any(), any(), any()) } returns
            OrderCheckoutSession("cs_test_reorder", "https://checkout.stripe.com/test")
        every { orderRepository.attachStripeSession(any(), "cs_test_reorder") } returns 1

        val result = service.createSwagShopCheckoutSession(orgId, variant.id, participant.id, null, null, null, null, currentUser)

        assertEquals("https://checkout.stripe.com/test", result.checkoutUrl)
        verify(exactly = 1) { printifyCatalogClient.listVariants(12L, 5L) }
    }

    @Suppress("ktlint:standard:max-line-length")
    @Test
    fun `createSwagShopCheckoutSession rejects checkout when the vendor no longer carries this exact blueprint, print provider, and variant combination`() {
        val currentUser = CurrentUser(UUID.randomUUID(), "guardian@example.com", "Sarah Johnson")
        val householdId = UUID.randomUUID()
        val participant = participant(householdId)
        val storeEntity = store()
        val product = product(storeEntity.id)
        val variant = productVariant(product.id)
        every { participantRepository.findById(participant.id, orgId) } returns participant
        every { authorizationService.hasGuardianRelationship(orgId, householdId, currentUser) } returns true
        every { productVariantRepository.findById(variant.id, orgId) } returns variant
        every { productRepository.findById(product.id, orgId) } returns product
        every { storeRepository.findById(product.storeId, orgId) } returns storeEntity
        // The vendor's catalog no longer lists variant id 100 for this blueprint/print-provider combination.
        every { printifyCatalogClient.listVariants(12L, 5L) } returns
            listOf(PrintifyCatalogVariant(id = 999L, title = "L / Navy", options = null, placeholders = null))

        val error =
            assertFailsWith<ConflictException> {
                service.createSwagShopCheckoutSession(orgId, variant.id, participant.id, null, null, null, null, currentUser)
            }

        assertEquals("PRINTIFY_VARIANT_UNAVAILABLE", error.code)
        verify(exactly = 0) { orderRepository.insertPending(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `createSwagShopCheckoutSession denies a stranger with no guardian relationship or team access`() {
        val currentUser = CurrentUser(UUID.randomUUID(), "stranger@example.com", "Stranger")
        val householdId = UUID.randomUUID()
        val participant = participant(householdId)
        every { participantRepository.findById(participant.id, orgId) } returns participant
        every { authorizationService.hasGuardianRelationship(orgId, householdId, currentUser) } returns false
        every { authorizationService.hasHouseholdCapability(orgId, householdId, currentUser, Capabilities.HOUSEHOLD_ORDER_CREATE) } returns
            false
        every { participantRepository.listTeamAssignments(participant.id, orgId) } returns emptyList()

        assertFailsWith<ForbiddenException> {
            service.createSwagShopCheckoutSession(orgId, UUID.randomUUID(), participant.id, null, null, null, null, currentUser)
        }
    }

    private fun athleteStorefront(
        storeId: UUID,
        participantId: UUID,
        status: AthleteStorefrontStatus = AthleteStorefrontStatus.PUBLISHED,
    ) = AthleteStorefront(
        id = UUID.randomUUID(),
        organizationId = orgId,
        participantId = participantId,
        teamId = null,
        storeId = storeId,
        slug = "maya-johnson",
        status = status,
        publishedAt = if (status == AthleteStorefrontStatus.PUBLISHED) Instant.now() else null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Test
    fun `createAthleteStorefrontCheckoutSession succeeds for an approved product and snapshots household attribution`() {
        val storeEntity = store()
        val product = product(storeEntity.id)
        val variant = productVariant(product.id)
        val household = participant(UUID.randomUUID())
        val storefront = athleteStorefront(storeEntity.id, household.id)
        val public = AthleteStorefrontPublic(storefront, "Maya J.")
        every { athleteStorefrontService.getPublic("maya-johnson") } returns public
        every { athleteStorefrontService.getPublicProducts(storefront) } returns listOf(product to listOf(variant))
        every { productVariantRepository.findById(variant.id, orgId) } returns variant
        every { productRepository.findById(product.id, orgId) } returns product
        every { storeRepository.findById(storefront.storeId, orgId) } returns storeEntity
        every { participantRepository.findById(storefront.participantId, orgId) } returns household
        val pending = pendingOrder(storeEntity)
        every {
            orderRepository.insertPending(
                orgId,
                storeEntity.id,
                variant.currency,
                "Jane Doe",
                null,
                attributedHouseholdId = household.householdId,
            )
        } returns pending
        every {
            orderItemRepository.insert(
                pending.id,
                variant.id,
                1,
                variant.priceMinor,
                variant.costMinor,
                household.id,
                null,
                null,
                null,
                null,
            )
        } returns mockk(relaxed = true)
        every { stripeOrderCheckoutClient.createOrderCheckoutSession(pending.id, any(), any(), any()) } returns
            OrderCheckoutSession("cs_storefront_1", "https://checkout.stripe.com/storefront")
        every { orderRepository.attachStripeSession(pending.id, "cs_storefront_1") } returns 1

        val result = service.createAthleteStorefrontCheckoutSession("maya-johnson", variant.id, null, null, null, null, "Jane Doe", null)

        assertEquals("https://checkout.stripe.com/storefront", result.checkoutUrl)
        verify(exactly = 1) {
            orderRepository.insertPending(
                orgId,
                storeEntity.id,
                variant.currency,
                "Jane Doe",
                null,
                attributedHouseholdId = household.householdId,
            )
        }
    }

    @Test
    fun `createAthleteStorefrontCheckoutSession rejects an item that is not in the storefront's approved product set`() {
        val storeEntity = store()
        val approvedProduct = product(storeEntity.id)
        val otherProduct = product(storeEntity.id)
        val variant = productVariant(otherProduct.id)
        val household = participant(UUID.randomUUID())
        val storefront = athleteStorefront(storeEntity.id, household.id)
        val public = AthleteStorefrontPublic(storefront, "Maya J.")
        every { athleteStorefrontService.getPublic("maya-johnson") } returns public
        every { athleteStorefrontService.getPublicProducts(storefront) } returns listOf(approvedProduct to emptyList())
        every { productVariantRepository.findById(variant.id, orgId) } returns variant
        every { productRepository.findById(otherProduct.id, orgId) } returns otherProduct

        assertFailsWith<ValidationException> {
            service.createAthleteStorefrontCheckoutSession("maya-johnson", variant.id, null, null, null, null, null, null)
        }
    }

    @Test
    fun `createAthleteStorefrontCheckoutSession throws NotFoundException for an inactive variant`() {
        val storeEntity = store()
        val product = product(storeEntity.id)
        val inactiveVariant = productVariant(product.id).copy(isActive = false)
        val household = participant(UUID.randomUUID())
        val storefront = athleteStorefront(storeEntity.id, household.id)
        val public = AthleteStorefrontPublic(storefront, "Maya J.")
        every { athleteStorefrontService.getPublic("maya-johnson") } returns public
        every { athleteStorefrontService.getPublicProducts(storefront) } returns listOf(product to emptyList())
        every { productVariantRepository.findById(inactiveVariant.id, orgId) } returns inactiveVariant

        assertFailsWith<NotFoundException> {
            service.createAthleteStorefrontCheckoutSession("maya-johnson", inactiveVariant.id, null, null, null, null, null, null)
        }
    }

    @Test
    fun `getAthleteStorefrontOrderStatus throws NotFoundException when the order belongs to a different store`() {
        val storeEntity = store()
        val household = participant(UUID.randomUUID())
        val storefront = athleteStorefront(storeEntity.id, household.id)
        val public = AthleteStorefrontPublic(storefront, "Maya J.")
        val orderFromOtherStore = pendingOrder(store())
        every { athleteStorefrontService.getPublic("maya-johnson") } returns public
        every { orderRepository.findById(orderFromOtherStore.id, orgId) } returns orderFromOtherStore

        assertFailsWith<NotFoundException> {
            service.getAthleteStorefrontOrderStatus("maya-johnson", orderFromOtherStore.id)
        }
    }

    @Test
    fun `confirmFromWebhook grants a family credit when the order carries a household attribution`() {
        val store = store()
        val vendorId = UUID.randomUUID()
        val product = product(store.id, CatalogSource.MANUAL, vendorId)
        val variant = productVariant(product.id, CatalogSource.MANUAL)
        val householdId = UUID.randomUUID()
        val order = pendingOrder(store).copy(attributedHouseholdId = householdId)
        val confirmed = order.copy(status = OrderStatus.CONFIRMED, confirmedAt = Instant.now())
        stubConfirmation(order, confirmed, product, variant)
        val fulfillment = fulfillment(order.id, FulfillmentSource.MANUAL, FulfillmentStatus.READY, manualVendorId = vendorId)
        every {
            fulfillmentRepository.insert(
                order.id,
                FulfillmentSource.MANUAL,
                FulfillmentStatus.READY,
                null,
                vendorId,
                null,
                printifyShopId = null,
            )
        } returns
            fulfillment
        every { fulfillmentHistoryRepository.insert(orgId, fulfillment.id, null, FulfillmentStatus.READY, any(), null) } returns
            history(fulfillment)
        every { familyCreditService.grantForStorefrontOrder(orgId, householdId, order.id, variant.priceMinor, order.currency) } returns
            mockk()

        service.confirmFromWebhook("cs_test_123", "paid", null, null)

        verify(
            exactly = 1,
        ) { familyCreditService.grantForStorefrontOrder(orgId, householdId, order.id, variant.priceMinor, order.currency) }
    }

    @Test
    fun `confirmFromWebhook does not grant a family credit for an order with no household attribution`() {
        val store = store()
        val vendorId = UUID.randomUUID()
        val product = product(store.id, CatalogSource.MANUAL, vendorId)
        val variant = productVariant(product.id, CatalogSource.MANUAL)
        val order = pendingOrder(store)
        val confirmed = order.copy(status = OrderStatus.CONFIRMED, confirmedAt = Instant.now())
        stubConfirmation(order, confirmed, product, variant)
        val fulfillment = fulfillment(order.id, FulfillmentSource.MANUAL, FulfillmentStatus.READY, manualVendorId = vendorId)
        every {
            fulfillmentRepository.insert(
                order.id,
                FulfillmentSource.MANUAL,
                FulfillmentStatus.READY,
                null,
                vendorId,
                null,
                printifyShopId = null,
            )
        } returns
            fulfillment
        every { fulfillmentHistoryRepository.insert(orgId, fulfillment.id, null, FulfillmentStatus.READY, any(), null) } returns
            history(fulfillment)

        service.confirmFromWebhook("cs_test_123", "paid", null, null)

        verify(exactly = 0) { familyCreditService.grantForStorefrontOrder(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `refund reverses the family credit when the order carries a household attribution`() {
        val store = store()
        val householdId = UUID.randomUUID()
        val order =
            pendingOrder(store)
                .copy(
                    status = OrderStatus.CONFIRMED,
                    stripePaymentIntentId = "pi_test_123",
                    confirmedAt = Instant.now(),
                    attributedHouseholdId = householdId,
                )
        val manager = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")
        every { membershipService.requireManagerRole(orgId, manager) } returns managerMembership(manager)
        every { orderRepository.findById(order.id, orgId) } returns order
        every { orderItemRepository.findByOrder(order.id) } returns
            listOf(OrderItem(UUID.randomUUID(), order.id, UUID.randomUUID(), 2, 2500L, 1200L))
        every { stripeOrderCheckoutClient.createRefund("pi_test_123") } returns "re_test_123"
        every { orderRepository.markRefunded(order.id) } returns 1
        every { ledgerService.recordRefund(orgId, LedgerSourceType.ORDER, order.id, 5000L, order.currency, "re_test_123") } just runs
        every { auditService.record(manager.userId, orgId, "order.refunded", "order", order.id) } just runs
        every { familyCreditService.reverseForRefundedOrder(orgId, order.id) } just runs

        service.refund(orgId, order.id, manager)

        verify(exactly = 1) { familyCreditService.reverseForRefundedOrder(orgId, order.id) }
    }

    @Test
    fun `refund does not reverse family credit for an order with no household attribution`() {
        val store = store()
        val order =
            pendingOrder(store).copy(status = OrderStatus.CONFIRMED, stripePaymentIntentId = "pi_test_123", confirmedAt = Instant.now())
        val manager = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")
        every { membershipService.requireManagerRole(orgId, manager) } returns managerMembership(manager)
        every { orderRepository.findById(order.id, orgId) } returns order
        every { orderItemRepository.findByOrder(order.id) } returns
            listOf(OrderItem(UUID.randomUUID(), order.id, UUID.randomUUID(), 2, 2500L, 1200L))
        every { stripeOrderCheckoutClient.createRefund("pi_test_123") } returns "re_test_123"
        every { orderRepository.markRefunded(order.id) } returns 1
        every { ledgerService.recordRefund(orgId, LedgerSourceType.ORDER, order.id, 5000L, order.currency, "re_test_123") } just runs
        every { auditService.record(manager.userId, orgId, "order.refunded", "order", order.id) } just runs

        service.refund(orgId, order.id, manager)

        verify(exactly = 0) { familyCreditService.reverseForRefundedOrder(any(), any()) }
    }

    private fun householdContext(householdId: UUID) =
        AuthorizationContext(
            ContextType.HOUSEHOLD,
            householdId,
            orgId,
            "My household",
            "GUARDIAN",
            setOf(Capabilities.HOUSEHOLD_ORDER_CREATE),
        )

    private fun teamOrderContext(teamId: UUID) =
        AuthorizationContext(ContextType.TEAM, teamId, orgId, "My team", "COACH", setOf(Capabilities.TEAM_ORDER_CREATE))

    private fun confirmedItemRow(
        storeId: UUID,
        variantId: UUID,
        participantId: UUID,
    ) = OrderItemWithOrder(
        orderId = UUID.randomUUID(),
        storeId = storeId,
        confirmedAt = Instant.now(),
        currency = "USD",
        item = OrderItem(UUID.randomUUID(), UUID.randomUUID(), variantId, 1, 2500L, 1200L, participantId, null, null, null, null),
    )

    @Test
    fun `listMySwagShopOrders only returns items for the caller's own household, not another household's`() {
        val currentUser = CurrentUser(UUID.randomUUID(), "guardian@example.com", "Sarah Johnson")
        val myHouseholdId = UUID.randomUUID()
        val myParticipant = participant(myHouseholdId)
        val storeEntity = store()
        val productEntity = product(storeEntity.id)
        val variant = productVariant(productEntity.id)

        every { authorizationService.listContexts(currentUser) } returns listOf(householdContext(myHouseholdId))
        every { participantRepository.findByHousehold(myHouseholdId, orgId) } returns listOf(myParticipant)
        every { orderItemRepository.findConfirmedByParticipants(orgId, listOf(myParticipant.id)) } returns
            listOf(confirmedItemRow(storeEntity.id, variant.id, myParticipant.id))
        every { participantRepository.findById(myParticipant.id, orgId) } returns myParticipant
        every { productVariantRepository.findById(variant.id, orgId) } returns variant
        every { productRepository.findById(productEntity.id, orgId) } returns productEntity
        every { storeRepository.findById(storeEntity.id, orgId) } returns storeEntity

        val result = service.listMySwagShopOrders(orgId, currentUser)

        assertEquals(1, result.size)
        assertEquals(myParticipant.id, result[0].participantId)
        assertEquals(true, result[0].isReorderable)
    }

    @Test
    fun `listMySwagShopOrders includes a coach's team roster items`() {
        val currentUser = CurrentUser(UUID.randomUUID(), "coach@example.com", "Coach Lee")
        val teamId = UUID.randomUUID()
        val rosterParticipant = participant(UUID.randomUUID())
        val storeEntity = store()
        val productEntity = product(storeEntity.id)
        val variant = productVariant(productEntity.id)

        every { authorizationService.listContexts(currentUser) } returns listOf(teamOrderContext(teamId))
        every { participantRepository.findActiveByTeam(teamId, orgId) } returns listOf(rosterParticipant)
        every { orderItemRepository.findConfirmedByParticipants(orgId, listOf(rosterParticipant.id)) } returns
            listOf(confirmedItemRow(storeEntity.id, variant.id, rosterParticipant.id))
        every { participantRepository.findById(rosterParticipant.id, orgId) } returns rosterParticipant
        every { productVariantRepository.findById(variant.id, orgId) } returns variant
        every { productRepository.findById(productEntity.id, orgId) } returns productEntity
        every { storeRepository.findById(storeEntity.id, orgId) } returns storeEntity

        val result = service.listMySwagShopOrders(orgId, currentUser)

        assertEquals(1, result.size)
        assertEquals(rosterParticipant.id, result[0].participantId)
    }

    @Test
    fun `listMySwagShopOrders marks an item non-reorderable when its variant is no longer active`() {
        val currentUser = CurrentUser(UUID.randomUUID(), "guardian@example.com", "Sarah Johnson")
        val householdId = UUID.randomUUID()
        val myParticipant = participant(householdId)
        val storeEntity = store()
        val productEntity = product(storeEntity.id)
        val variant = productVariant(productEntity.id).copy(isActive = false)

        every { authorizationService.listContexts(currentUser) } returns listOf(householdContext(householdId))
        every { participantRepository.findByHousehold(householdId, orgId) } returns listOf(myParticipant)
        every { orderItemRepository.findConfirmedByParticipants(orgId, listOf(myParticipant.id)) } returns
            listOf(confirmedItemRow(storeEntity.id, variant.id, myParticipant.id))
        every { participantRepository.findById(myParticipant.id, orgId) } returns myParticipant
        every { productVariantRepository.findById(variant.id, orgId) } returns variant
        every { productRepository.findById(productEntity.id, orgId) } returns productEntity
        every { storeRepository.findById(storeEntity.id, orgId) } returns storeEntity

        val result = service.listMySwagShopOrders(orgId, currentUser)

        assertEquals(false, result[0].isReorderable)
    }

    @Test
    fun `listMySwagShopOrders returns an empty list when the caller has no household or order-capable team`() {
        val currentUser = CurrentUser(UUID.randomUUID(), "stranger@example.com", "Stranger")
        every { authorizationService.listContexts(currentUser) } returns emptyList()
        every { orderItemRepository.findConfirmedByParticipants(orgId, emptyList()) } returns emptyList()

        val result = service.listMySwagShopOrders(orgId, currentUser)

        assertEquals(emptyList(), result)
    }
}
