package com.leaguelift.store.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ServiceUnavailableException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.integration.printify.application.EligiblePrintProvider
import com.leaguelift.integration.printify.application.VendorSelectionService
import com.leaguelift.integration.printify.infra.PrintifyBlueprint
import com.leaguelift.integration.printify.infra.PrintifyCatalogClient
import com.leaguelift.integration.printify.infra.PrintifyCatalogVariant
import com.leaguelift.integration.printify.infra.PrintifyImageClient
import com.leaguelift.integration.printify.infra.PrintifyProductClient
import com.leaguelift.media.application.MediaAssignmentService
import com.leaguelift.media.application.MediaReadService
import com.leaguelift.media.domain.MediaAssignment
import com.leaguelift.media.domain.MediaEntityType
import com.leaguelift.media.domain.MediaUsageSlot
import com.leaguelift.media.domain.Visibility
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.store.domain.Product
import com.leaguelift.store.domain.ProductStatus
import com.leaguelift.store.domain.ProductVariant
import com.leaguelift.store.persistence.ProductRepository
import com.leaguelift.store.persistence.ProductVariantRepository
import com.leaguelift.store.persistence.StoreRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClientException
import java.util.UUID

private val log = LoggerFactory.getLogger(ProductService::class.java)
private const val PUBLIC_STORE_PRODUCT_LIMIT = 100

/**
 * Products are backed by a real Printify blueprint/print-provider/variant
 * (Phase 4 slice 1). A variant's real `cost_minor` is only knowable once
 * [PrintifyProductClient.createProduct] is called for it — see
 * `integration/printify/infra/PrintifyProductClient.kt` for why the catalog
 * alone can't answer this. Auto-design/personalization (logo placement, per-
 * buyer name/number) is explicitly out of scope — an admin uploads one
 * pre-made design via the existing media pipeline (`MediaUsageSlot.PRODUCT_DESIGN`).
 */
@Service
class ProductService(
	private val productRepository: ProductRepository,
	private val productVariantRepository: ProductVariantRepository,
	private val storeRepository: StoreRepository,
	private val membershipService: MembershipService,
	private val auditService: AuditService,
	private val mediaAssignmentService: MediaAssignmentService,
	private val mediaReadService: MediaReadService,
	private val printifyCatalogClient: PrintifyCatalogClient,
	private val printifyImageClient: PrintifyImageClient,
	private val printifyProductClient: PrintifyProductClient,
	private val vendorSelectionService: VendorSelectionService,
) {

	fun listBlueprints(organizationId: UUID, currentUser: CurrentUser): List<PrintifyBlueprint> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return withPrintifyErrorTranslation { printifyCatalogClient.listBlueprints() }
	}

	fun listUsPrintProviders(organizationId: UUID, blueprintId: Long, currentUser: CurrentUser): List<EligiblePrintProvider> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return vendorSelectionService.listUsPrintProviders(blueprintId)
	}

	fun listCatalogVariants(organizationId: UUID, blueprintId: Long, printProviderId: Long, currentUser: CurrentUser): List<PrintifyCatalogVariant> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return withPrintifyErrorTranslation { printifyCatalogClient.listVariants(blueprintId, printProviderId) }
	}

	fun listForStore(organizationId: UUID, storeId: UUID, currentUser: CurrentUser, offset: Int, limit: Int): List<Product> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		requireStore(organizationId, storeId)
		return productRepository.findByStore(storeId, offset, limit)
	}

	fun get(organizationId: UUID, productId: UUID, currentUser: CurrentUser): Product {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return findProduct(organizationId, productId)
	}

	/** Public/unauthenticated — only ACTIVE products, mirrors CampaignService.getPublic's ACTIVE-only gate. */
	fun listPublicProducts(storeId: UUID): List<Product> =
		productRepository.findByStore(storeId, 0, PUBLIC_STORE_PRODUCT_LIMIT).filter { it.status == ProductStatus.ACTIVE }

	fun listPublicVariants(productId: UUID): List<ProductVariant> = productVariantRepository.findActiveByProduct(productId)

	/** Null if no design has been assigned, or its assignment isn't PUBLIC-visibility (e.g. the product isn't ACTIVE yet). */
	fun getPublicDesignUrl(productId: UUID): String? {
		val assignment = mediaAssignmentService.getPublicAssignment(MediaEntityType.PRODUCT, productId, MediaUsageSlot.PRODUCT_DESIGN) ?: return null
		return mediaReadService.describe(assignment)?.url
	}

	fun listVariants(organizationId: UUID, productId: UUID, currentUser: CurrentUser): List<ProductVariant> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		findProduct(organizationId, productId)
		return productVariantRepository.findActiveByProduct(productId)
	}

	@Transactional
	fun create(
		organizationId: UUID,
		storeId: UUID,
		name: String,
		description: String?,
		printifyBlueprintId: Long,
		printifyPrintPosition: String,
		currentUser: CurrentUser,
	): Product {
		membershipService.requireManagerRole(organizationId, currentUser)
		requireStore(organizationId, storeId)
		val product = productRepository.insert(organizationId, storeId, name, description, printifyBlueprintId, printifyPrintPosition)
		auditService.record(currentUser.userId, organizationId, "product.created", "product", product.id)
		return product
	}

	@Transactional
	fun assignDesign(organizationId: UUID, productId: UUID, assetId: UUID, altText: String?, currentUser: CurrentUser): MediaAssignment {
		val product = findProduct(organizationId, productId)
		return mediaAssignmentService.assign(organizationId, MediaEntityType.PRODUCT, productId, MediaUsageSlot.PRODUCT_DESIGN, assetId, altText, currentUser) {
			if (product.status == ProductStatus.ACTIVE) Visibility.PUBLIC else Visibility.ORGANIZATION_PRIVATE
		}
	}

	/**
	 * Calls Printify once to learn this specific provider+variant's real cost
	 * (uploading the product's design to Printify's image library first, if this
	 * is the product's first variant) and snapshots it. No cross-provider price
	 * comparison happens here — see VendorSelectionService.
	 */
	@Transactional
	fun createVariant(
		organizationId: UUID,
		productId: UUID,
		printifyPrintProviderId: Long,
		printifyVariantId: Long,
		label: String,
		priceMinor: Long,
		currentUser: CurrentUser,
	): ProductVariant {
		membershipService.requireManagerRole(organizationId, currentUser)
		val product = findProduct(organizationId, productId)
		val printifyImageId = product.printifyImageId ?: uploadDesignToPrintify(organizationId, product, currentUser)

		val result = withPrintifyErrorTranslation {
			printifyProductClient.createProduct(
				title = product.name,
				blueprintId = product.printifyBlueprintId,
				printProviderId = printifyPrintProviderId,
				printifyVariantIds = listOf(printifyVariantId),
				requestedPriceMinor = priceMinor,
				printifyImageId = printifyImageId,
				printPosition = product.printifyPrintPosition,
			)
		}
		val variantCost = result.variantCosts.firstOrNull { it.printifyVariantId == printifyVariantId }
			?: throw ServiceUnavailableException("PRINTIFY_PROVIDER_UNAVAILABLE", "Printify did not return pricing for the selected variant.")

		val variant = productVariantRepository.insert(
			organizationId, productId, label, printifyPrintProviderId, printifyVariantId,
			currency = "USD", costMinor = variantCost.costMinor, priceMinor = variantCost.priceMinor,
		)
		auditService.record(currentUser.userId, organizationId, "product_variant.created", "product_variant", variant.id)
		return variant
	}

	@Transactional
	fun updateStatus(organizationId: UUID, productId: UUID, status: ProductStatus, currentUser: CurrentUser): Product {
		membershipService.requireManagerRole(organizationId, currentUser)
		findProduct(organizationId, productId)
		productRepository.updateStatus(productId, organizationId, status)
		auditService.record(currentUser.userId, organizationId, "product.status_updated", "product", productId)
		return findProduct(organizationId, productId)
	}

	private fun uploadDesignToPrintify(organizationId: UUID, product: Product, currentUser: CurrentUser): String {
		val designAssignment = mediaAssignmentService.listActive(organizationId, MediaEntityType.PRODUCT, product.id, currentUser)
			.firstOrNull { it.usageSlot == MediaUsageSlot.PRODUCT_DESIGN }
			?: throw ValidationException("Upload a design image for this product before creating a variant.")
		val descriptor = mediaReadService.describe(designAssignment)
			?: throw ValidationException("The product's design image could not be found.")
		val extension = descriptor.contentType?.substringAfter('/') ?: "png"
		val uploaded = withPrintifyErrorTranslation {
			printifyImageClient.uploadImage(fileName = "product-design.$extension", sourceUrl = descriptor.url)
		}
		productRepository.updatePrintifyImageId(product.id, organizationId, uploaded.id)
		return uploaded.id
	}

	private fun findProduct(organizationId: UUID, productId: UUID): Product =
		productRepository.findById(productId, organizationId)
			?: throw NotFoundException("PRODUCT_NOT_FOUND", "The product could not be found.")

	private fun requireStore(organizationId: UUID, storeId: UUID) {
		storeRepository.findById(storeId, organizationId)
			?: throw NotFoundException("STORE_NOT_FOUND", "The store could not be found.")
	}

	private fun <T> withPrintifyErrorTranslation(block: () -> T): T =
		try {
			block()
		} catch (e: RestClientException) {
			log.warn("Printify API call failed: {}", e.message, e)
			throw ServiceUnavailableException(
				"PRINTIFY_PROVIDER_UNAVAILABLE",
				"The apparel provider is not available right now. If this is local/staging, confirm PRINTIFY_API_TOKEN/PRINTIFY_SHOP_ID are set.",
			)
		}
}
