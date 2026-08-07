package com.rally26.store.integration

import com.ninjasquad.springmockk.MockkBean
import com.rally26.common.web.CurrentUser
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.integration.printify.infra.PrintifyCatalogClient
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
import com.rally26.media.infra.SpacesClient
import com.rally26.media.persistence.MediaAssetRepository
import com.rally26.media.persistence.MediaAssignmentRepository
import com.rally26.order.application.FulfillmentOperationsService
import com.rally26.order.application.OrderLineItemRequest
import com.rally26.order.application.OrderService
import com.rally26.order.domain.FulfillmentStatus
import com.rally26.order.infra.OrderCheckoutSession
import com.rally26.order.infra.StripeOrderCheckoutClient
import com.rally26.order.persistence.FulfillmentRepository
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.Organization
import com.rally26.organization.domain.OrganizationType
import com.rally26.store.application.ProductService
import com.rally26.store.application.StoreService
import com.rally26.store.domain.CatalogSource
import com.rally26.store.domain.ProductStatus
import com.rally26.store.domain.StoreStatus
import com.rally26.store.persistence.ProductVariantRepository
import com.rally26.testsupport.AbstractIntegrationTest
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Phase 24 slice 24.4 (ADR-070): proves the DB-level cross-organization
 * isolation guarantee — two organizations' products/orders, created against
 * the same single shared Printify shop, never collide or become visible to
 * each other, even when their external Printify ids are adjacent/contrived.
 * Mirrors `StoreOrderIntegrationTest`'s pattern (real Postgres, mocked
 * Printify/Stripe HTTP clients).
 */
class PrintifyShopIsolationIntegrationTest : AbstractIntegrationTest() {
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
    lateinit var fulfillmentOperationsService: FulfillmentOperationsService

    @Autowired
    lateinit var productVariantRepository: ProductVariantRepository

    @Autowired
    lateinit var fulfillmentRepository: FulfillmentRepository

    @Autowired
    lateinit var mediaAssetRepository: MediaAssetRepository

    @Autowired
    lateinit var mediaAssignmentRepository: MediaAssignmentRepository

    @MockkBean
    lateinit var printifyImageClient: PrintifyImageClient

    @MockkBean
    lateinit var printifyProductClient: PrintifyProductClient

    @MockkBean
    lateinit var printifyCatalogClient: PrintifyCatalogClient

    @MockkBean
    lateinit var printifyOrderClient: PrintifyOrderClient

    @MockkBean
    lateinit var stripeOrderCheckoutClient: StripeOrderCheckoutClient

    @MockkBean
    lateinit var spacesClient: SpacesClient

    @Test
    fun `two organizations' Printify products are correctly prefixed, attributed, and never cross-visible`() {
        val ownerA = registerUser("printify-iso-owner-a")
        val orgA = organization(ownerA, "Riverside Soccer")
        val storeA = storeService.create(orgA.id, null, "Spring Store", "spring-store-${System.nanoTime()}", ownerA)
        storeService.updateStatus(orgA.id, storeA.id, StoreStatus.ACTIVE, ownerA)
        val productA = createReadyPrintifyProduct(orgA.id, storeA.id, "Team Hoodie", ownerA)

        val ownerB = registerUser("printify-iso-owner-b")
        val orgB = organization(ownerB, "Lakeside Basketball")
        val storeB = storeService.create(orgB.id, null, "Court Store", "court-store-${System.nanoTime()}", ownerB)
        storeService.updateStatus(orgB.id, storeB.id, StoreStatus.ACTIVE, ownerB)
        val productB = createReadyPrintifyProduct(orgB.id, storeB.id, "Team Hoodie", ownerB)

        val expectedTitleA = "[${orgA.slug}/${storeA.slug}] Team Hoodie"
        val expectedTitleB = "[${orgB.slug}/${storeB.slug}] Team Hoodie"
        every { printifyProductClient.createProduct(expectedTitleA, 12L, 5L, listOf(100L), 2500L, any(), "front") } returns
            PrintifyProductResult(
                "printify_product_A",
                listOf(PrintifyProductVariantCost(100L, costMinor = 1200L, priceMinor = 2500L)),
                emptyList(),
            )
        every { printifyProductClient.createProduct(expectedTitleB, 12L, 5L, listOf(100L), 2500L, any(), "front") } returns
            PrintifyProductResult(
                "printify_product_B",
                listOf(PrintifyProductVariantCost(100L, costMinor = 1200L, priceMinor = 2500L)),
                emptyList(),
            )
        every { printifyCatalogClient.listVariants(12L, 5L) } returns emptyList()

        val variantA = productService.createVariant(orgA.id, productA.id, 5L, 100L, "M / Navy", 2500L, ownerA)
        val variantB = productService.createVariant(orgB.id, productB.id, 5L, 100L, "M / Navy", 2500L, ownerB)

        // Each org's variant carries its own distinct Printify product id and the
        // shared shop id — never the other org's identifiers.
        assertEquals("printify_product_A", variantA.printifyProductId)
        assertEquals("printify_product_B", variantB.printifyProductId)
        assertNotEquals(variantA.printifyProductId, variantB.printifyProductId)
        assertEquals(variantA.printifyShopId, variantB.printifyShopId, "both orgs share the one Rally26-controlled Printify shop")

        // A duplicate printify_product_id (simulating a hypothetical Printify id
        // reuse anomaly) must be rejected by the DB, not silently accepted.
        productService.updateStatus(orgA.id, productA.id, ProductStatus.ACTIVE, ownerA)
        val productC = createReadyPrintifyProduct(orgA.id, storeA.id, "Team Tee", ownerA)
        val expectedTitleC = "[${orgA.slug}/${storeA.slug}] Team Tee"
        every { printifyProductClient.createProduct(expectedTitleC, 12L, 5L, listOf(101L), 2500L, any(), "front") } returns
            PrintifyProductResult(
                "printify_product_A",
                listOf(PrintifyProductVariantCost(101L, costMinor = 1200L, priceMinor = 2500L)),
                emptyList(),
            )
        assertFailsWith<DataIntegrityViolationException> {
            productService.createVariant(orgA.id, productC.id, 5L, 101L, "L / Navy", 2500L, ownerA)
        }
    }

    @Test
    fun `a Printify webhook resolved by our own minted order id never touches the other organization's fulfillment`() {
        val ownerA = registerUser("printify-iso-webhook-a")
        val orgA = organization(ownerA, "Riverside Soccer")
        val storeA = storeService.create(orgA.id, null, "Spring Store", "spring-store-${System.nanoTime()}", ownerA)
        storeService.updateStatus(orgA.id, storeA.id, StoreStatus.ACTIVE, ownerA)
        val orderA = confirmedPrintifyOrder(orgA, storeA, ownerA, "printify_order_A")

        val ownerB = registerUser("printify-iso-webhook-b")
        val orgB = organization(ownerB, "Lakeside Basketball")
        val storeB = storeService.create(orgB.id, null, "Court Store", "court-store-${System.nanoTime()}", ownerB)
        storeService.updateStatus(orgB.id, storeB.id, StoreStatus.ACTIVE, ownerB)
        val orderB = confirmedPrintifyOrder(orgB, storeB, ownerB, "printify_order_B")

        val fulfillmentBBefore = fulfillmentRepository.findByOrder(orderB.id)
        assertEquals(FulfillmentStatus.DRAFT_CREATED, fulfillmentBBefore?.status)

        val updated =
            fulfillmentOperationsService.applyProviderStatusUpdate(
                "printify_order_A",
                FulfillmentStatus.SHIPPED,
                "USPS",
                "1Z999",
                null,
                "Shipped (webhook).",
            )

        assertEquals(FulfillmentStatus.SHIPPED, updated?.status)
        assertEquals(FulfillmentStatus.SHIPPED, fulfillmentRepository.findByOrder(orderA.id)?.status)
        assertEquals(
            FulfillmentStatus.DRAFT_CREATED,
            fulfillmentRepository.findByOrder(orderB.id)?.status,
            "org B's fulfillment must be untouched by an update resolved through org A's own printify_order_id",
        )

        // An unrelated / never-created printify order id resolves to nothing.
        assertNull(
            fulfillmentOperationsService.applyProviderStatusUpdate(
                "printify_order_never_created",
                FulfillmentStatus.SHIPPED,
                null,
                null,
                null,
                "note",
            ),
        )
    }

    private fun confirmedPrintifyOrder(
        organization: Organization,
        store: com.rally26.store.domain.Store,
        owner: CurrentUser,
        printifyOrderId: String,
    ): com.rally26.order.domain.Order {
        val product = createReadyPrintifyProduct(organization.id, store.id, "Team Hoodie", owner)
        val expectedTitle = "[${organization.slug}/${store.slug}] Team Hoodie"
        every { printifyProductClient.createProduct(expectedTitle, 12L, 5L, listOf(100L), 2500L, any(), "front") } returns
            PrintifyProductResult(
                "printify_product_$printifyOrderId",
                listOf(PrintifyProductVariantCost(100L, costMinor = 1200L, priceMinor = 2500L)),
                emptyList(),
            )
        every { printifyCatalogClient.listVariants(12L, 5L) } returns emptyList()
        val variant = productService.createVariant(organization.id, product.id, 5L, 100L, "M / Navy", 2500L, owner)
        productService.updateStatus(organization.id, product.id, ProductStatus.ACTIVE, owner)

        val fixedSessionId = "cs_test_${System.nanoTime()}"
        every { stripeOrderCheckoutClient.createOrderCheckoutSession(any(), any(), any(), any()) } returns
            OrderCheckoutSession(fixedSessionId, "https://checkout.stripe.com/test")
        orderService.createCheckoutSession(
            store.slug,
            listOf(OrderLineItemRequest(variant.id, 1)),
            "Jane Doe",
            "jane@example.com",
            "https://app.local/success",
            "https://app.local/cancel",
        )
        every { printifyOrderClient.createDraftOrder(any(), any()) } returns PrintifyDraftOrder(printifyOrderId)

        val confirmed = orderService.confirmFromWebhook(fixedSessionId, "paid", null, "pi_test_${System.nanoTime()}")
        return confirmed!!
    }

    private fun createReadyPrintifyProduct(
        organizationId: UUID,
        storeId: UUID,
        name: String,
        owner: CurrentUser,
    ): com.rally26.store.domain.Product {
        val product = productService.create(organizationId, storeId, name, null, CatalogSource.PRINTIFY, null, 12L, "front", owner)
        assignReadyDesign(organizationId, product.id, owner)
        return product
    }

    /** Bypasses the real upload/S3-confirm dance (already covered by the media module's own integration tests), mirroring `StoreOrderIntegrationTest.assignReadyDesign`. */
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
        every { spacesClient.getObjectBytesCapped("organizations/$organizationId/media/$assetId/original.png", any()) } returns
            byteArrayOf(1, 2, 3)
        every { spacesClient.presignedGetUrl(any(), any()) } returns "https://signed.example.com/design.png"
        every { printifyImageClient.uploadImage(any(), any()) } returns
            PrintifyUploadedImage("printify_img_${UUID.randomUUID()}", "design.png")
    }

    private fun organization(
        owner: CurrentUser,
        name: String,
    ): Organization =
        organizationService.create(
            name,
            "${name.lowercase().replace(" ", "-")}-${System.nanoTime()}",
            OrganizationType.RECREATIONAL_LEAGUE,
            owner,
        )

    private fun registerUser(prefix: String): CurrentUser {
        val appUser = passwordAuthenticationService.register("$prefix-${System.nanoTime()}@example.com", "password1234", "Test User")
        return passwordAuthenticationService.toCurrentUser(appUser)
    }
}
