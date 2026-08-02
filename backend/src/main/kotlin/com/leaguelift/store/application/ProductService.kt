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
import com.leaguelift.store.domain.CatalogSource
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
import java.util.Currency
import java.util.UUID

private val log = LoggerFactory.getLogger(ProductService::class.java)
private const val PUBLIC_STORE_PRODUCT_LIMIT = 100

/**
 * One catalog supports two honest sources. PRINTIFY preserves real provider IDs
 * and provider-returned cost snapshots. MANUAL stores no provider IDs and uses
 * administrator-entered vendor/cost/SKU details. Order items still snapshot both
 * price and cost, so later catalog edits never rewrite transaction history.
 */
@Service
class ProductService(
	private val productRepository: ProductRepository,
	private val productVariantRepository: ProductVariantRepository,
	private val storeRepository: StoreRepository,
	private val manualVendorService: ManualVendorService,
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

	fun countForStore(organizationId: UUID, storeId: UUID, currentUser: CurrentUser): Long {
		membershipService.requireActiveMembership(organizationId, currentUser)
		requireStore(organizationId, storeId)
		return productRepository.countByStore(storeId)
	}

	fun get(organizationId: UUID, productId: UUID, currentUser: CurrentUser): Product {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return findProduct(organizationId, productId)
	}

	fun hasAssignedDesign(productId: UUID): Boolean =
		mediaAssignmentService.getActiveAssignment(MediaEntityType.PRODUCT, productId, MediaUsageSlot.PRODUCT_DESIGN) != null

	/** Public/unauthenticated — only ACTIVE products. */
	fun listPublicProducts(storeId: UUID): List<Product> =
		productRepository.findByStore(storeId, 0, PUBLIC_STORE_PRODUCT_LIMIT).filter { it.status == ProductStatus.ACTIVE }

	fun listPublicVariants(productId: UUID): List<ProductVariant> = productVariantRepository.findActiveByProduct(productId)

	fun getPublicDesignUrl(productId: UUID): String? {
		val assignment = mediaAssignmentService.getPublicAssignment(MediaEntityType.PRODUCT, productId, MediaUsageSlot.PRODUCT_DESIGN) ?: return null
		return mediaReadService.describe(assignment)?.url
	}

	fun listVariants(organizationId: UUID, productId: UUID, currentUser: CurrentUser): List<ProductVariant> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		findProduct(organizationId, productId)
		return productVariantRepository.findByProduct(productId)
	}

	@Transactional
	fun create(
		organizationId: UUID,
		storeId: UUID,
		name: String,
		description: String?,
		catalogSource: CatalogSource,
		manualVendorId: UUID?,
		printifyBlueprintId: Long?,
		printifyPrintPosition: String,
		currentUser: CurrentUser,
	): Product {
		membershipService.requireManagerRole(organizationId, currentUser)
		requireStore(organizationId, storeId)
		when (catalogSource) {
			CatalogSource.PRINTIFY -> {
				if (printifyBlueprintId == null || printifyBlueprintId <= 0) throw ValidationException("Choose a Printify product type.")
				if (manualVendorId != null) throw ValidationException("A Printify product cannot reference a manual vendor.")
			}
			CatalogSource.MANUAL -> {
				if (printifyBlueprintId != null) throw ValidationException("Manual products cannot contain a Printify blueprint ID.")
				manualVendorId?.let { manualVendorService.requireActiveVendor(organizationId, it) }
			}
		}
		val product = productRepository.insert(
			organizationId = organizationId,
			storeId = storeId,
			name = name.trim(),
			description = description.normalized(),
			catalogSource = catalogSource,
			manualVendorId = manualVendorId,
			printifyBlueprintId = printifyBlueprintId,
			printifyPrintPosition = printifyPrintPosition.trim().ifEmpty { "front" },
		)
		auditService.record(currentUser.userId, organizationId, "product.created", "product", product.id)
		return product
	}

	@Transactional
	fun updateManualProduct(
		organizationId: UUID,
		productId: UUID,
		name: String,
		description: String?,
		manualVendorId: UUID?,
		currentUser: CurrentUser,
	): Product {
		membershipService.requireManagerRole(organizationId, currentUser)
		val product = findProduct(organizationId, productId)
		if (product.catalogSource != CatalogSource.MANUAL) throw ValidationException("Only manual products can be edited with this workflow.")
		manualVendorId?.let { manualVendorService.requireActiveVendor(organizationId, it) }
		productRepository.updateManualProduct(productId, organizationId, name.trim(), description.normalized(), manualVendorId)
		auditService.record(currentUser.userId, organizationId, "product.manual_updated", "product", productId)
		return findProduct(organizationId, productId)
	}

	@Transactional
	fun assignDesign(organizationId: UUID, productId: UUID, assetId: UUID, altText: String?, currentUser: CurrentUser): MediaAssignment {
		val product = findProduct(organizationId, productId)
		return mediaAssignmentService.assign(organizationId, MediaEntityType.PRODUCT, productId, MediaUsageSlot.PRODUCT_DESIGN, assetId, altText, currentUser) {
			if (product.status == ProductStatus.ACTIVE) Visibility.PUBLIC else Visibility.ORGANIZATION_PRIVATE
		}
	}

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
		if (product.catalogSource != CatalogSource.PRINTIFY) throw ValidationException("Use the manual-variant workflow for a manual product.")
		val blueprintId = product.printifyBlueprintId ?: error("PRINTIFY product ${product.id} has no blueprint ID")
		val printifyImageId = product.printifyImageId ?: uploadDesignToPrintify(organizationId, product, currentUser)

		val result = withPrintifyErrorTranslation {
			printifyProductClient.createProduct(
				title = product.name,
				blueprintId = blueprintId,
				printProviderId = printifyPrintProviderId,
				printifyVariantIds = listOf(printifyVariantId),
				requestedPriceMinor = priceMinor,
				printifyImageId = printifyImageId,
				printPosition = product.printifyPrintPosition,
			)
		}
		val variantCost = result.variantCosts.firstOrNull { it.printifyVariantId == printifyVariantId }
			?: throw ServiceUnavailableException("PRINTIFY_PROVIDER_UNAVAILABLE", "Printify did not return pricing for the selected variant.")

		val variant = productVariantRepository.insertPrintify(
			organizationId, productId, label.trim(), printifyPrintProviderId, printifyVariantId,
			currency = "USD", costMinor = variantCost.costMinor, priceMinor = variantCost.priceMinor,
		)
		auditService.record(currentUser.userId, organizationId, "product_variant.created", "product_variant", variant.id)
		return variant
	}

	@Transactional
	fun createManualVariant(
		organizationId: UUID,
		productId: UUID,
		label: String,
		sku: String?,
		size: String?,
		color: String?,
		currency: String,
		costMinor: Long,
		priceMinor: Long,
		currentUser: CurrentUser,
	): ProductVariant {
		membershipService.requireManagerRole(organizationId, currentUser)
		val product = findProduct(organizationId, productId)
		if (product.catalogSource != CatalogSource.MANUAL) throw ValidationException("Manual variants can only be added to manual products.")
		validateMoney(currency, costMinor, priceMinor)
		val variant = productVariantRepository.insertManual(
			organizationId, productId, label.trim(), sku.normalized(), size.normalized(), color.normalized(),
			currency.uppercase(), costMinor, priceMinor,
		)
		auditService.record(currentUser.userId, organizationId, "product_variant.manual_created", "product_variant", variant.id)
		return variant
	}

	@Transactional
	fun updateManualVariant(
		organizationId: UUID,
		productId: UUID,
		variantId: UUID,
		label: String,
		sku: String?,
		size: String?,
		color: String?,
		currency: String,
		costMinor: Long,
		priceMinor: Long,
		isActive: Boolean,
		currentUser: CurrentUser,
	): ProductVariant {
		membershipService.requireManagerRole(organizationId, currentUser)
		val product = findProduct(organizationId, productId)
		if (product.catalogSource != CatalogSource.MANUAL) throw ValidationException("Only manual variants can be edited with this workflow.")
		val variant = requireVariant(organizationId, productId, variantId)
		if (variant.catalogSource != CatalogSource.MANUAL) throw ValidationException("Only manual variants can be edited with this workflow.")
		validateMoney(currency, costMinor, priceMinor)
		productVariantRepository.updateManual(
			variantId, organizationId, label.trim(), sku.normalized(), size.normalized(), color.normalized(),
			currency.uppercase(), costMinor, priceMinor, isActive,
		)
		auditService.record(currentUser.userId, organizationId, "product_variant.manual_updated", "product_variant", variantId)
		return requireVariant(organizationId, productId, variantId)
	}

	@Transactional
	fun updateVariantActive(
		organizationId: UUID,
		productId: UUID,
		variantId: UUID,
		isActive: Boolean,
		currentUser: CurrentUser,
	): ProductVariant {
		membershipService.requireManagerRole(organizationId, currentUser)
		requireVariant(organizationId, productId, variantId)
		productVariantRepository.updateActive(variantId, organizationId, isActive)
		auditService.record(currentUser.userId, organizationId, "product_variant.status_updated", "product_variant", variantId)
		return requireVariant(organizationId, productId, variantId)
	}

	@Transactional
	fun updateStatus(organizationId: UUID, productId: UUID, status: ProductStatus, currentUser: CurrentUser): Product {
		membershipService.requireManagerRole(organizationId, currentUser)
		val product = findProduct(organizationId, productId)
		if (status == ProductStatus.ACTIVE) {
			if (!hasAssignedDesign(productId)) throw ValidationException("Assign a product image before activating this product.")
			if (productVariantRepository.findActiveByProduct(productId).isEmpty()) throw ValidationException("Add at least one active variant before activating this product.")
			if (product.catalogSource == CatalogSource.PRINTIFY && product.printifyImageId == null) {
				throw ValidationException("Create a Printify variant before activating this product.")
			}
		}
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

	private fun requireVariant(organizationId: UUID, productId: UUID, variantId: UUID): ProductVariant =
		productVariantRepository.findById(variantId, organizationId)?.takeIf { it.productId == productId }
			?: throw NotFoundException("PRODUCT_VARIANT_NOT_FOUND", "The product variant could not be found.")

	private fun requireStore(organizationId: UUID, storeId: UUID) {
		storeRepository.findById(storeId, organizationId)
			?: throw NotFoundException("STORE_NOT_FOUND", "The store could not be found.")
	}

	private fun validateMoney(currency: String, costMinor: Long, priceMinor: Long) {
		try {
			Currency.getInstance(currency.uppercase())
		} catch (_: IllegalArgumentException) {
			throw ValidationException("Use a valid three-letter currency code.")
		}
		if (costMinor < 0 || priceMinor < 0) throw ValidationException("Cost and price must be zero or greater.")
		if (priceMinor < costMinor) throw ValidationException("Price cannot be lower than the recorded vendor cost.")
	}

	private fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

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
