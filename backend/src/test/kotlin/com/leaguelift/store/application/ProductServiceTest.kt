package com.leaguelift.store.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.ValidationException
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
import com.leaguelift.common.web.CurrentUser
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
	private val membershipService = mockk<MembershipService>()
	private val auditService = mockk<AuditService>()
	private val mediaAssignmentService = mockk<MediaAssignmentService>()
	private val mediaReadService = mockk<MediaReadService>()
	private val printifyCatalogClient = mockk<PrintifyCatalogClient>()
	private val printifyImageClient = mockk<PrintifyImageClient>()
	private val printifyProductClient = mockk<PrintifyProductClient>()
	private val vendorSelectionService = mockk<VendorSelectionService>()
	private val service = ProductService(
		productRepository, productVariantRepository, storeRepository, membershipService, auditService,
		mediaAssignmentService, mediaReadService, printifyCatalogClient, printifyImageClient, printifyProductClient, vendorSelectionService,
	)

	private val orgId = UUID.randomUUID()
	private val currentUser = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")

	@Test
	fun `createVariant throws ValidationException when no design has been assigned yet`() {
		val product = product(printifyImageId = null)
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { productRepository.findById(product.id, orgId) } returns product
		every { mediaAssignmentService.listActive(orgId, MediaEntityType.PRODUCT, product.id, currentUser) } returns emptyList()

		assertFailsWith<ValidationException> {
			service.createVariant(orgId, product.id, 5L, 100L, "M / Navy", 2500L, currentUser)
		}
	}

	@Test
	fun `createVariant uploads the design to Printify on first use and snapshots the real returned cost`() {
		val product = product(printifyImageId = null)
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
		every { productVariantRepository.insert(orgId, product.id, "M / Navy", 5L, 100L, "USD", 1200L, 2500L) } returns productVariant(product.id, 1200L, 2500L)
		every { auditService.record(any(), any(), any(), any(), any()) } just runs

		service.createVariant(orgId, product.id, 5L, 100L, "M / Navy", 2500L, currentUser)

		verify(exactly = 1) { printifyImageClient.uploadImage(any(), any()) }
		verify(exactly = 1) { productRepository.updatePrintifyImageId(product.id, orgId, "printify_img_1") }
		verify(exactly = 1) { productVariantRepository.insert(orgId, product.id, "M / Navy", 5L, 100L, "USD", 1200L, 2500L) }
	}

	@Test
	fun `createVariant reuses an already-uploaded printify image id without re-uploading`() {
		val product = product(printifyImageId = "printify_img_existing")
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { productRepository.findById(product.id, orgId) } returns product
		every {
			printifyProductClient.createProduct("Team Hoodie", 12L, 5L, listOf(100L), 2500L, "printify_img_existing", "front")
		} returns PrintifyProductResult("printify_product_1", listOf(PrintifyProductVariantCost(100L, costMinor = 1300L, priceMinor = 2500L)))
		every { productVariantRepository.insert(orgId, product.id, "M / Navy", 5L, 100L, "USD", 1300L, 2500L) } returns productVariant(product.id, 1300L, 2500L)
		every { auditService.record(any(), any(), any(), any(), any()) } just runs

		service.createVariant(orgId, product.id, 5L, 100L, "M / Navy", 2500L, currentUser)

		verify(exactly = 0) { printifyImageClient.uploadImage(any(), any()) }
	}

	private fun product(printifyImageId: String?) = Product(
		id = UUID.randomUUID(), organizationId = orgId, storeId = UUID.randomUUID(), name = "Team Hoodie", description = null,
		printifyBlueprintId = 12L, printifyImageId = printifyImageId, printifyPrintPosition = "front",
		status = ProductStatus.DRAFT, createdAt = Instant.now(), updatedAt = Instant.now(),
	)

	private fun productVariant(productId: UUID, costMinor: Long, priceMinor: Long) = ProductVariant(
		id = UUID.randomUUID(), organizationId = orgId, productId = productId, label = "M / Navy",
		printifyPrintProviderId = 5L, printifyVariantId = 100L, currency = "USD", costMinor = costMinor, priceMinor = priceMinor,
		isActive = true, createdAt = Instant.now(), updatedAt = Instant.now(),
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
