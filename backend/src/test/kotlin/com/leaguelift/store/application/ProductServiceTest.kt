package com.leaguelift.store.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.integration.printify.application.VendorSelectionService
import com.leaguelift.integration.printify.infra.PrintifyCatalogClient
import com.leaguelift.integration.printify.infra.PrintifyImageClient
import com.leaguelift.integration.printify.infra.PrintifyProductClient
import com.leaguelift.integration.printify.infra.PrintifyProductResult
import com.leaguelift.integration.printify.infra.PrintifyProductVariantCost
import com.leaguelift.integration.printify.infra.PrintifyUploadedImage
import com.leaguelift.media.application.MediaAssignmentService
import com.leaguelift.media.application.MediaDescriptor
import com.leaguelift.media.application.MediaReadService
import com.leaguelift.media.domain.MediaAssignment
import com.leaguelift.media.domain.MediaEntityType
import com.leaguelift.media.domain.MediaUsageSlot
import com.leaguelift.media.domain.PublicationStatus
import com.leaguelift.media.domain.Visibility
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.membership.domain.MembershipStatus
import com.leaguelift.membership.domain.OrganizationMembership
import com.leaguelift.store.domain.CatalogSource
import com.leaguelift.store.domain.Product
import com.leaguelift.store.domain.ProductStatus
import com.leaguelift.store.domain.ProductVariant
import com.leaguelift.store.persistence.ProductRepository
import com.leaguelift.store.persistence.ProductVariantRepository
import com.leaguelift.store.persistence.StoreRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProductServiceTest {
	private val productRepository = mockk<ProductRepository>()
	private val productVariantRepository = mockk<ProductVariantRepository>()
	private val storeRepository = mockk<StoreRepository>()
	private val manualVendorService = mockk<ManualVendorService>()
	private val membershipService = mockk<MembershipService>()
	private val auditService = mockk<AuditService>()
	private val mediaAssignmentService = mockk<MediaAssignmentService>()
	private val mediaReadService = mockk<MediaReadService>()
	private val printifyCatalogClient = mockk<PrintifyCatalogClient>()
	private val printifyImageClient = mockk<PrintifyImageClient>()
	private val printifyProductClient = mockk<PrintifyProductClient>()
	private val vendorSelectionService = mockk<VendorSelectionService>()
	private val service = ProductService(
		productRepository, productVariantRepository, storeRepository, manualVendorService, membershipService,
		auditService, mediaAssignmentService, mediaReadService, printifyCatalogClient, printifyImageClient,
		printifyProductClient, vendorSelectionService,
	)

	private val orgId = UUID.randomUUID()
	private val currentUser = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")

	@Test
	fun `createVariant rejects a manual product`() {
		val product = product(CatalogSource.MANUAL, printifyImageId = null)
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { productRepository.findById(product.id, orgId) } returns product

		assertFailsWith<ValidationException> {
			service.createVariant(orgId, product.id, 5L, 100L, "M / Navy", 2500L, currentUser)
		}
	}

	@Test
	fun `createVariant throws ValidationException when no design has been assigned yet`() {
		val product = product(CatalogSource.PRINTIFY, printifyImageId = null)
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { productRepository.findById(product.id, orgId) } returns product
		every { mediaAssignmentService.listActive(orgId, MediaEntityType.PRODUCT, product.id, currentUser) } returns emptyList()

		assertFailsWith<ValidationException> {
			service.createVariant(orgId, product.id, 5L, 100L, "M / Navy", 2500L, currentUser)
		}
	}

	@Test
	fun `createVariant uploads the design to Printify on first use and snapshots the returned cost`() {
		val product = product(CatalogSource.PRINTIFY, printifyImageId = null)
		val assignment = mediaAssignment(product.id)
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { productRepository.findById(product.id, orgId) } returns product
		every { mediaAssignmentService.listActive(orgId, MediaEntityType.PRODUCT, product.id, currentUser) } returns listOf(assignment)
		every { mediaReadService.describe(assignment) } returns MediaDescriptor(assignment, "https://signed.example.com/design.png", "image/png", 1024, 500, 500)
		every { printifyImageClient.uploadImage("product-design.png", "https://signed.example.com/design.png") } returns PrintifyUploadedImage("printify_img_1", "product-design.png")
		every { productRepository.updatePrintifyImageId(product.id, orgId, "printify_img_1") } returns 1
		every {
			printifyProductClient.createProduct("Team Hoodie", 12L, 5L, listOf(100L), 2500L, "printify_img_1", "front")
		} returns PrintifyProductResult("printify_product_1", listOf(PrintifyProductVariantCost(100L, costMinor = 1200L, priceMinor = 2500L)))
		every { productVariantRepository.insertPrintify(orgId, product.id, "M / Navy", 5L, 100L, "USD", 1200L, 2500L) } returns productVariant(product.id, CatalogSource.PRINTIFY, 1200L, 2500L)
		every { auditService.record(any(), any(), any(), any(), any()) } just runs

		service.createVariant(orgId, product.id, 5L, 100L, "M / Navy", 2500L, currentUser)

		verify(exactly = 1) { printifyImageClient.uploadImage(any(), any()) }
		verify(exactly = 1) { productRepository.updatePrintifyImageId(product.id, orgId, "printify_img_1") }
		verify(exactly = 1) { productVariantRepository.insertPrintify(orgId, product.id, "M / Navy", 5L, 100L, "USD", 1200L, 2500L) }
	}

	@Test
	fun `createManualVariant records entered cost without calling Printify`() {
		val product = product(CatalogSource.MANUAL, printifyImageId = null)
		val created = productVariant(product.id, CatalogSource.MANUAL, 900L, 1800L)
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { productRepository.findById(product.id, orgId) } returns product
		every { productVariantRepository.insertManual(orgId, product.id, "Youth M", "Y-M", "M", "Navy", "USD", 900L, 1800L) } returns created
		every { auditService.record(any(), any(), any(), any(), any()) } just runs

		val result = service.createManualVariant(orgId, product.id, "Youth M", "Y-M", "M", "Navy", "usd", 900L, 1800L, currentUser)

		assertEquals(CatalogSource.MANUAL, result.catalogSource)
		verify(exactly = 0) { printifyProductClient.createProduct(any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `createManualVariant rejects a price below vendor cost`() {
		val product = product(CatalogSource.MANUAL, printifyImageId = null)
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { productRepository.findById(product.id, orgId) } returns product

		assertFailsWith<ValidationException> {
			service.createManualVariant(orgId, product.id, "Youth M", null, null, null, "USD", 1800L, 900L, currentUser)
		}
	}

	@Test
	fun `listPublicProducts returns only ACTIVE products`() {
		val storeId = UUID.randomUUID()
		val active = product(CatalogSource.PRINTIFY, "img_1").copy(storeId = storeId, status = ProductStatus.ACTIVE)
		val draft = product(CatalogSource.MANUAL, null).copy(storeId = storeId, status = ProductStatus.DRAFT)
		val archived = product(CatalogSource.PRINTIFY, "img_2").copy(storeId = storeId, status = ProductStatus.ARCHIVED)
		every { productRepository.findByStore(storeId, 0, 100) } returns listOf(active, draft, archived)

		assertEquals(listOf(active.id), service.listPublicProducts(storeId).map { it.id })
	}

	@Test
	fun `getPublicDesignUrl returns null without a public assignment`() {
		val product = product(CatalogSource.MANUAL, null)
		every { mediaAssignmentService.getPublicAssignment(MediaEntityType.PRODUCT, product.id, MediaUsageSlot.PRODUCT_DESIGN) } returns null

		assertEquals(null, service.getPublicDesignUrl(product.id))
		verify(exactly = 0) { mediaReadService.describe(any()) }
	}

	@Test
	fun `getPublicDesignUrl resolves a public assignment`() {
		val product = product(CatalogSource.PRINTIFY, "img_1")
		val assignment = mediaAssignment(product.id).copy(visibility = Visibility.PUBLIC, publicationStatus = PublicationStatus.APPROVED)
		every { mediaAssignmentService.getPublicAssignment(MediaEntityType.PRODUCT, product.id, MediaUsageSlot.PRODUCT_DESIGN) } returns assignment
		every { mediaReadService.describe(assignment) } returns MediaDescriptor(assignment, "https://signed.example.com/design.png", "image/png", 1024, 500, 500)

		assertEquals("https://signed.example.com/design.png", service.getPublicDesignUrl(product.id))
	}

	private fun product(source: CatalogSource, printifyImageId: String?) = Product(
		id = UUID.randomUUID(), organizationId = orgId, storeId = UUID.randomUUID(), name = "Team Hoodie", description = null,
		catalogSource = source, manualVendorId = null, manualVendorName = null,
		printifyBlueprintId = if (source == CatalogSource.PRINTIFY) 12L else null,
		printifyImageId = printifyImageId, printifyPrintPosition = "front",
		status = ProductStatus.DRAFT, createdAt = Instant.now(), updatedAt = Instant.now(),
	)

	private fun productVariant(productId: UUID, source: CatalogSource, costMinor: Long, priceMinor: Long) = ProductVariant(
		id = UUID.randomUUID(), organizationId = orgId, productId = productId, catalogSource = source, label = "M / Navy",
		sku = if (source == CatalogSource.MANUAL) "M-NAVY" else null, size = "M", color = "Navy",
		printifyPrintProviderId = if (source == CatalogSource.PRINTIFY) 5L else null,
		printifyVariantId = if (source == CatalogSource.PRINTIFY) 100L else null,
		currency = "USD", costMinor = costMinor, priceMinor = priceMinor, isActive = true,
		createdAt = Instant.now(), updatedAt = Instant.now(),
	)

	private fun mediaAssignment(productId: UUID) = MediaAssignment(
		id = UUID.randomUUID(), organizationId = orgId, assetId = UUID.randomUUID(), entityType = MediaEntityType.PRODUCT,
		entityId = productId, usageSlot = MediaUsageSlot.PRODUCT_DESIGN, publicationStatus = PublicationStatus.PRIVATE,
		visibility = Visibility.ORGANIZATION_PRIVATE, altText = null, createdAt = Instant.now(), updatedAt = Instant.now(),
	)

	private fun managerMembership() = OrganizationMembership(
		id = UUID.randomUUID(), organizationId = orgId, userId = currentUser.userId, role = MembershipRole.ADMINISTRATOR,
		status = MembershipStatus.ACTIVE, createdAt = Instant.now(), updatedAt = Instant.now(),
	)
}
