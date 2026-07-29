package com.leaguelift.store.web

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.common.web.PageResponse
import com.leaguelift.media.application.MediaReadService
import com.leaguelift.media.web.MediaAssignmentResponse
import com.leaguelift.media.web.toResponse
import com.leaguelift.store.application.ProductService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}")
class ProductController(
	private val productService: ProductService,
	private val mediaReadService: MediaReadService,
) {

	@GetMapping("/printify/blueprints")
	fun listBlueprints(
		@PathVariable organizationId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): List<PrintifyBlueprintResponse> =
		productService.listBlueprints(organizationId, currentUser).map { it.toResponse() }

	@GetMapping("/printify/blueprints/{blueprintId}/print-providers")
	fun listUsPrintProviders(
		@PathVariable organizationId: UUID,
		@PathVariable blueprintId: Long,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): List<EligiblePrintProviderResponse> =
		productService.listUsPrintProviders(organizationId, blueprintId, currentUser).map { it.toResponse() }

	@GetMapping("/printify/blueprints/{blueprintId}/print-providers/{printProviderId}/variants")
	fun listCatalogVariants(
		@PathVariable organizationId: UUID,
		@PathVariable blueprintId: Long,
		@PathVariable printProviderId: Long,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): List<PrintifyCatalogVariantResponse> =
		productService.listCatalogVariants(organizationId, blueprintId, printProviderId, currentUser).map { it.toResponse() }

	@GetMapping("/stores/{storeId}/products")
	fun listForStore(
		@PathVariable organizationId: UUID,
		@PathVariable storeId: UUID,
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): PageResponse<ProductResponse> {
		val offset = page * size
		val items = productService.listForStore(organizationId, storeId, currentUser, offset, size).map { it.toResponse() }
		return PageResponse(items, page, size, items.size.toLong())
	}

	@PostMapping("/stores/{storeId}/products")
	fun create(
		@PathVariable organizationId: UUID,
		@PathVariable storeId: UUID,
		@Valid @RequestBody request: CreateProductRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): ResponseEntity<ProductResponse> {
		val product = productService.create(
			organizationId, storeId, request.name, request.description,
			request.printifyBlueprintId, request.printifyPrintPosition, currentUser,
		)
		return ResponseEntity.status(HttpStatus.CREATED).body(product.toResponse())
	}

	@GetMapping("/products/{productId}")
	fun get(
		@PathVariable organizationId: UUID,
		@PathVariable productId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): ProductResponse = productService.get(organizationId, productId, currentUser).toResponse()

	@PatchMapping("/products/{productId}/status")
	fun updateStatus(
		@PathVariable organizationId: UUID,
		@PathVariable productId: UUID,
		@Valid @RequestBody request: UpdateProductStatusRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): ProductResponse = productService.updateStatus(organizationId, productId, request.status, currentUser).toResponse()

	@PutMapping("/products/{productId}/design")
	fun assignDesign(
		@PathVariable organizationId: UUID,
		@PathVariable productId: UUID,
		@Valid @RequestBody request: AssignProductDesignRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): MediaAssignmentResponse {
		val assignment = productService.assignDesign(organizationId, productId, request.assetId, request.altText, currentUser)
		val descriptor = mediaReadService.describe(assignment)
			?: error("Newly assigned design asset ${assignment.assetId} must exist.")
		return descriptor.toResponse()
	}

	@GetMapping("/products/{productId}/variants")
	fun listVariants(
		@PathVariable organizationId: UUID,
		@PathVariable productId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): List<ProductVariantResponse> = productService.listVariants(organizationId, productId, currentUser).map { it.toResponse() }

	@PostMapping("/products/{productId}/variants")
	fun createVariant(
		@PathVariable organizationId: UUID,
		@PathVariable productId: UUID,
		@Valid @RequestBody request: CreateProductVariantRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): ResponseEntity<ProductVariantResponse> {
		val variant = productService.createVariant(
			organizationId, productId, request.printifyPrintProviderId, request.printifyVariantId,
			request.label, request.priceMinor, currentUser,
		)
		return ResponseEntity.status(HttpStatus.CREATED).body(variant.toResponse())
	}
}
