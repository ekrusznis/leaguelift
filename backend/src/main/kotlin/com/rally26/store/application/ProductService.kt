package com.rally26.store.application

import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ServiceUnavailableException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.integration.printify.application.EligiblePrintProvider
import com.rally26.integration.printify.application.VendorSelectionService
import com.rally26.integration.printify.infra.PrintifyBlueprint
import com.rally26.integration.printify.infra.PrintifyCatalogClient
import com.rally26.integration.printify.infra.PrintifyCatalogVariant
import com.rally26.integration.printify.infra.PrintifyImageClient
import com.rally26.integration.printify.infra.PrintifyProductClient
import com.rally26.media.application.MediaAssignmentService
import com.rally26.media.application.MediaReadService
import com.rally26.media.domain.MediaAssignment
import com.rally26.media.domain.MediaEntityType
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.media.domain.Visibility
import com.rally26.media.persistence.MediaAssetRepository
import com.rally26.membership.application.MembershipService
import com.rally26.store.domain.CatalogSource
import com.rally26.store.domain.Product
import com.rally26.store.domain.ProductStatus
import com.rally26.store.domain.ProductVariant
import com.rally26.store.domain.Store
import com.rally26.store.persistence.ProductRepository
import com.rally26.store.persistence.ProductVariantRepository
import com.rally26.store.persistence.StoreRepository
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
    private val authorizationService: AuthorizationService,
    private val auditService: AuditService,
    private val mediaAssignmentService: MediaAssignmentService,
    private val mediaReadService: MediaReadService,
    private val mediaAssetRepository: MediaAssetRepository,
    private val printifyCatalogClient: PrintifyCatalogClient,
    private val printifyImageClient: PrintifyImageClient,
    private val printifyProductClient: PrintifyProductClient,
    private val vendorSelectionService: VendorSelectionService,
) {
    fun listBlueprints(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): List<PrintifyBlueprint> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return withPrintifyErrorTranslation { printifyCatalogClient.listBlueprints() }
    }

    fun listUsPrintProviders(
        organizationId: UUID,
        blueprintId: Long,
        currentUser: CurrentUser,
    ): List<EligiblePrintProvider> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return vendorSelectionService.listUsPrintProviders(blueprintId)
    }

    fun listCatalogVariants(
        organizationId: UUID,
        blueprintId: Long,
        printProviderId: Long,
        currentUser: CurrentUser,
    ): List<PrintifyCatalogVariant> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return withPrintifyErrorTranslation { printifyCatalogClient.listVariants(blueprintId, printProviderId) }
    }

    fun listForStore(
        organizationId: UUID,
        storeId: UUID,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<Product> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        requireStore(organizationId, storeId)
        return productRepository.findByStore(storeId, offset, limit)
    }

    fun countForStore(
        organizationId: UUID,
        storeId: UUID,
        currentUser: CurrentUser,
    ): Long {
        membershipService.requireActiveMembership(organizationId, currentUser)
        requireStore(organizationId, storeId)
        return productRepository.countByStore(storeId)
    }

    fun get(
        organizationId: UUID,
        productId: UUID,
        currentUser: CurrentUser,
    ): Product {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return findProduct(organizationId, productId)
    }

    fun hasAssignedDesign(productId: UUID): Boolean =
        mediaAssignmentService.getActiveAssignment(MediaEntityType.PRODUCT, productId, MediaUsageSlot.PRODUCT_DESIGN) != null

    /** Public/unauthenticated — only ACTIVE products. */
    fun listPublicProducts(storeId: UUID): List<Product> =
        productRepository.findByStore(storeId, 0, PUBLIC_STORE_PRODUCT_LIMIT).filter { it.status == ProductStatus.ACTIVE }

    fun listPublicVariants(productId: UUID): List<ProductVariant> = productVariantRepository.findActiveByProduct(productId)

    /**
     * Swag Shop (DESIGN-DOC.md section 13) buyer picker: every ACTIVE Printify-backed
     * product/variant across every ACTIVE store in the org, in one call. Deliberately
     * no org-membership/capability gate beyond being signed in — a pure guardian or
     * coach (household/team relationship only, no organization_membership row) must
     * be able to browse this, and the same data is already fully public via
     * GET /public/stores/{slug}, so an authenticated-only gate reveals nothing new.
     */
    fun listSwagShopApparelTypes(organizationId: UUID): List<Triple<Store, Product, List<ProductVariant>>> {
        val activeStores = storeRepository.findAll(organizationId, 0, 500).filter { it.status == com.rally26.store.domain.StoreStatus.ACTIVE }
        return activeStores.flatMap { store ->
            listPublicProducts(store.id)
                .filter { it.catalogSource == CatalogSource.PRINTIFY }
                .map { product -> Triple(store, product, productVariantRepository.findActiveByProduct(product.id)) }
        }
    }

    fun getPublicDesignUrl(productId: UUID): String? {
        val assignment =
            mediaAssignmentService.getPublicAssignment(MediaEntityType.PRODUCT, productId, MediaUsageSlot.PRODUCT_DESIGN) ?: return null
        return mediaReadService.describe(assignment)?.url
    }

    fun listVariants(
        organizationId: UUID,
        productId: UUID,
        currentUser: CurrentUser,
    ): List<ProductVariant> {
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
        val store = requireStore(organizationId, storeId)
        requireStoreScopedManageAccess(organizationId, store.teamId, currentUser)
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
        val product =
            productRepository.insert(
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
        val product = requireProductManageAccess(organizationId, productId, currentUser)
        if (product.catalogSource !=
            CatalogSource.MANUAL
        ) {
            throw ValidationException("Only manual products can be edited with this workflow.")
        }
        manualVendorId?.let { manualVendorService.requireActiveVendor(organizationId, it) }
        productRepository.updateManualProduct(productId, organizationId, name.trim(), description.normalized(), manualVendorId)
        auditService.record(currentUser.userId, organizationId, "product.manual_updated", "product", productId)
        return findProduct(organizationId, productId)
    }

    @Transactional
    fun assignDesign(
        organizationId: UUID,
        productId: UUID,
        assetId: UUID,
        altText: String?,
        currentUser: CurrentUser,
    ): MediaAssignment {
        val product = findProduct(organizationId, productId)
        return mediaAssignmentService.assign(
            organizationId,
            MediaEntityType.PRODUCT,
            productId,
            MediaUsageSlot.PRODUCT_DESIGN,
            assetId,
            altText,
            currentUser,
        ) {
            if (product.status == ProductStatus.ACTIVE) Visibility.PUBLIC else Visibility.ORGANIZATION_PRIVATE
        }
    }

    /**
     * Swag Shop (DESIGN-DOC.md section 13): freezes the owning team's current logo
     * onto the product for order-time compositing (SwagDesignCompositor). A copy
     * reference, not a new upload — deliberately bypasses the generic
     * mediaAssignmentService.assign()/org-manager-only path so a coach who can
     * manage their own team's store can complete Swag Shop setup without an org
     * manager's involvement, matching requireStoreScopedManageAccess above.
     */
    @Transactional
    fun useTeamLogo(
        organizationId: UUID,
        productId: UUID,
        currentUser: CurrentUser,
    ): Product {
        val product = requireProductManageAccess(organizationId, productId, currentUser)
        val store = requireStore(organizationId, product.storeId)
        val teamId = store.teamId ?: throw ValidationException("Only a team-scoped store's products can use the team logo.")
        val logoAssignment =
            mediaAssignmentService.getActiveAssignment(MediaEntityType.TEAM, teamId, MediaUsageSlot.LOGO)
                ?: throw ValidationException("Upload a team logo before enabling the Swag Shop for this apparel type.")
        val logoAsset =
            mediaAssetRepository.findById(logoAssignment.assetId, organizationId)
                ?: throw ValidationException("The team's logo image could not be found.")
        val contentType = logoAsset.detectedContentType ?: logoAsset.declaredContentType
        if (contentType.contains("svg")) {
            throw ValidationException("Upload a PNG/JPEG team logo to enable the Swag Shop — SVG logos aren't supported for print yet.")
        }
        productRepository.updateSwagLogoMediaAssetId(productId, organizationId, logoAssignment.assetId)
        auditService.record(currentUser.userId, organizationId, "product.swag_logo_assigned", "product", productId)
        return findProduct(organizationId, productId)
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
        val product = requireProductManageAccess(organizationId, productId, currentUser)
        if (product.catalogSource !=
            CatalogSource.PRINTIFY
        ) {
            throw ValidationException("Use the manual-variant workflow for a manual product.")
        }
        val blueprintId = product.printifyBlueprintId ?: error("PRINTIFY product ${product.id} has no blueprint ID")
        val printifyImageId = product.printifyImageId ?: uploadDesignToPrintify(organizationId, product, currentUser)

        val result =
            withPrintifyErrorTranslation {
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
        val variantCost =
            result.variantCosts.firstOrNull { it.printifyVariantId == printifyVariantId }
                ?: throw ServiceUnavailableException(
                    "PRINTIFY_PROVIDER_UNAVAILABLE",
                    "Printify did not return pricing for the selected variant.",
                )

        // Swag Shop (DESIGN-DOC.md section 13): captured once here, at setup time, so
        // order-time compositing never needs an extra live Printify call inside the
        // payment-confirmation transaction (see OrderService.createInitialFulfillment).
        val placeholder =
            withPrintifyErrorTranslation { printifyCatalogClient.listVariants(blueprintId, printifyPrintProviderId) }
                .firstOrNull { it.id == printifyVariantId }
                ?.placeholders
                ?.firstOrNull { it.position == product.printifyPrintPosition }

        val variant =
            productVariantRepository.insertPrintify(
                organizationId,
                productId,
                label.trim(),
                printifyPrintProviderId,
                printifyVariantId,
                currency = "USD",
                costMinor = variantCost.costMinor,
                priceMinor = variantCost.priceMinor,
                printAreaWidthPx = placeholder?.width,
                printAreaHeightPx = placeholder?.height,
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
        val product = requireProductManageAccess(organizationId, productId, currentUser)
        if (product.catalogSource !=
            CatalogSource.MANUAL
        ) {
            throw ValidationException("Manual variants can only be added to manual products.")
        }
        validateMoney(currency, costMinor, priceMinor)
        val variant =
            productVariantRepository.insertManual(
                organizationId,
                productId,
                label.trim(),
                sku.normalized(),
                size.normalized(),
                color.normalized(),
                currency.uppercase(),
                costMinor,
                priceMinor,
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
        val product = requireProductManageAccess(organizationId, productId, currentUser)
        if (product.catalogSource !=
            CatalogSource.MANUAL
        ) {
            throw ValidationException("Only manual variants can be edited with this workflow.")
        }
        val variant = requireVariant(organizationId, productId, variantId)
        if (variant.catalogSource !=
            CatalogSource.MANUAL
        ) {
            throw ValidationException("Only manual variants can be edited with this workflow.")
        }
        validateMoney(currency, costMinor, priceMinor)
        productVariantRepository.updateManual(
            variantId,
            organizationId,
            label.trim(),
            sku.normalized(),
            size.normalized(),
            color.normalized(),
            currency.uppercase(),
            costMinor,
            priceMinor,
            isActive,
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
        requireProductManageAccess(organizationId, productId, currentUser)
        requireVariant(organizationId, productId, variantId)
        productVariantRepository.updateActive(variantId, organizationId, isActive)
        auditService.record(currentUser.userId, organizationId, "product_variant.status_updated", "product_variant", variantId)
        return requireVariant(organizationId, productId, variantId)
    }

    @Transactional
    fun updateStatus(
        organizationId: UUID,
        productId: UUID,
        status: ProductStatus,
        currentUser: CurrentUser,
    ): Product {
        val product = requireProductManageAccess(organizationId, productId, currentUser)
        if (status == ProductStatus.ACTIVE) {
            if (!hasAssignedDesign(productId)) throw ValidationException("Assign a product image before activating this product.")
            if (productVariantRepository
                    .findActiveByProduct(
                        productId,
                    ).isEmpty()
            ) {
                throw ValidationException("Add at least one active variant before activating this product.")
            }
            if (product.catalogSource == CatalogSource.PRINTIFY && product.printifyImageId == null) {
                throw ValidationException("Create a Printify variant before activating this product.")
            }
        }
        productRepository.updateStatus(productId, organizationId, status)
        auditService.record(currentUser.userId, organizationId, "product.status_updated", "product", productId)
        return findProduct(organizationId, productId)
    }

    /**
     * Platform Admin Swag Shop support triage (DESIGN-DOC.md section 13): cleans up
     * a stuck/broken draft product. Only a DRAFT product with no order history can be
     * deleted — a product that ever shipped a real order keeps its row for the same
     * reason order/ledger history is never rewritten elsewhere in this codebase; an
     * org admin/coach should archive an unwanted ACTIVE product instead.
     */
    @Transactional
    fun delete(
        organizationId: UUID,
        productId: UUID,
        currentUser: CurrentUser,
    ) {
        val product = requireProductManageAccess(organizationId, productId, currentUser)
        if (product.status != ProductStatus.DRAFT) {
            throw ValidationException("Only a draft product can be deleted. Archive it instead.")
        }
        if (productRepository.hasOrderHistory(productId)) {
            throw ValidationException("This product has order history and can't be deleted — archive it instead.")
        }
        productVariantRepository.deleteByProduct(productId)
        productRepository.delete(productId, organizationId)
        auditService.record(currentUser.userId, organizationId, "product.deleted", "product", productId)
    }

    private fun uploadDesignToPrintify(
        organizationId: UUID,
        product: Product,
        currentUser: CurrentUser,
    ): String {
        val designAssignment =
            mediaAssignmentService
                .listActive(organizationId, MediaEntityType.PRODUCT, product.id, currentUser)
                .firstOrNull { it.usageSlot == MediaUsageSlot.PRODUCT_DESIGN }
                ?: throw ValidationException("Upload a design image for this product before creating a variant.")
        val descriptor =
            mediaReadService.describe(designAssignment)
                ?: throw ValidationException("The product's design image could not be found.")
        val extension = descriptor.contentType?.substringAfter('/') ?: "png"
        val uploaded =
            withPrintifyErrorTranslation {
                printifyImageClient.uploadImage(fileName = "product-design.$extension", sourceUrl = descriptor.url)
            }
        productRepository.updatePrintifyImageId(product.id, organizationId, uploaded.id)
        return uploaded.id
    }

    private fun findProduct(
        organizationId: UUID,
        productId: UUID,
    ): Product =
        productRepository.findById(productId, organizationId)
            ?: throw NotFoundException("PRODUCT_NOT_FOUND", "The product could not be found.")

    private fun requireVariant(
        organizationId: UUID,
        productId: UUID,
        variantId: UUID,
    ): ProductVariant =
        productVariantRepository.findById(variantId, organizationId)?.takeIf { it.productId == productId }
            ?: throw NotFoundException("PRODUCT_VARIANT_NOT_FOUND", "The product variant could not be found.")

    private fun requireStore(
        organizationId: UUID,
        storeId: UUID,
    ): Store =
        storeRepository.findById(storeId, organizationId)
            ?: throw NotFoundException("STORE_NOT_FOUND", "The store could not be found.")

    /**
     * A team-scoped store's products/variants can be managed by a coach holding
     * TEAM_STORE_MANAGE, not just an org owner/administrator — mirrors
     * StoreService.requireStoreManageAccess exactly (AuthorizationService's
     * team-capability check already inherits org owner/admin access, ADR-020).
     */
    private fun requireStoreScopedManageAccess(
        organizationId: UUID,
        teamId: UUID?,
        currentUser: CurrentUser,
    ) {
        if (teamId != null) {
            authorizationService.requireTeamCapability(organizationId, teamId, currentUser, Capabilities.TEAM_STORE_MANAGE)
        } else {
            membershipService.requireManagerRole(organizationId, currentUser)
        }
    }

    /** Resolves the product's owning store to determine team-scoped vs org-wide manage access, then returns the product. */
    private fun requireProductManageAccess(
        organizationId: UUID,
        productId: UUID,
        currentUser: CurrentUser,
    ): Product {
        val product = findProduct(organizationId, productId)
        val store = requireStore(organizationId, product.storeId)
        requireStoreScopedManageAccess(organizationId, store.teamId, currentUser)
        return product
    }

    private fun validateMoney(
        currency: String,
        costMinor: Long,
        priceMinor: Long,
    ) {
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
