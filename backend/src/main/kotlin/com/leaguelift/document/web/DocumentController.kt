package com.leaguelift.document.web

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.document.application.DocumentService
import com.leaguelift.media.application.MediaReadService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Household/org document storage (DESIGN-DOC.md section 13, Phase 7 completion).
 * Uploading the underlying file still goes through the existing generic
 * `POST /organizations/{organizationId}/media/uploads` (+ `/confirm`) with
 * `usageSlot: "DOCUMENT"` — this controller only covers assigning an already-uploaded
 * asset to an organization/household and the acknowledgment ("signature") workflow.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}")
class DocumentController(
	private val documentService: DocumentService,
	private val mediaReadService: MediaReadService,
) {

	@GetMapping("/documents")
	fun listOrganizationDocuments(@PathVariable organizationId: UUID, @AuthenticationPrincipal currentUser: CurrentUser): DocumentListResponse {
		val assignments = documentService.listOrganizationDocuments(organizationId, currentUser)
		return DocumentListResponse(mediaReadService.describeAll(assignments).map { it.toDocumentResponse() })
	}

	@PostMapping("/documents")
	@ResponseStatus(HttpStatus.CREATED)
	fun assignOrganizationDocument(
		@PathVariable organizationId: UUID,
		@Valid @RequestBody request: AssignDocumentRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): DocumentResponse {
		val assignment = documentService.assignToOrganization(organizationId, request.assetId, request.title, currentUser)
		val descriptor = mediaReadService.describe(assignment) ?: error("Newly assigned document ${assignment.id} must exist.")
		return descriptor.toDocumentResponse()
	}

	@PostMapping("/documents/broadcast")
	@ResponseStatus(HttpStatus.CREATED)
	fun broadcastDocumentToAllHouseholds(
		@PathVariable organizationId: UUID,
		@Valid @RequestBody request: AssignDocumentRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): DocumentListResponse {
		val assignments = documentService.assignToAllHouseholds(organizationId, request.assetId, request.title, currentUser)
		return DocumentListResponse(mediaReadService.describeAll(assignments).map { it.toDocumentResponse() })
	}

	@DeleteMapping("/documents/{assignmentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun removeDocument(
		@PathVariable organizationId: UUID,
		@PathVariable assignmentId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	) = documentService.removeDocument(organizationId, assignmentId, currentUser)

	@GetMapping("/documents/{assignmentId}/acknowledgments")
	fun listAcknowledgments(
		@PathVariable organizationId: UUID,
		@PathVariable assignmentId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): DocumentAcknowledgmentListResponse =
		DocumentAcknowledgmentListResponse(documentService.listAcknowledgments(organizationId, assignmentId, currentUser).map { it.toResponse() })

	@GetMapping("/households/{householdId}/documents")
	fun listHouseholdDocuments(
		@PathVariable organizationId: UUID,
		@PathVariable householdId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): DocumentListResponse {
		val assignments = documentService.listHouseholdDocuments(organizationId, householdId, currentUser)
		return DocumentListResponse(mediaReadService.describeAll(assignments).map { it.toDocumentResponse() })
	}

	@PostMapping("/households/{householdId}/documents")
	@ResponseStatus(HttpStatus.CREATED)
	fun assignHouseholdDocument(
		@PathVariable organizationId: UUID,
		@PathVariable householdId: UUID,
		@Valid @RequestBody request: AssignDocumentRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): DocumentResponse {
		val assignment = documentService.assignToHousehold(organizationId, householdId, request.assetId, request.title, currentUser)
		val descriptor = mediaReadService.describe(assignment) ?: error("Newly assigned document ${assignment.id} must exist.")
		return descriptor.toDocumentResponse()
	}

	@PostMapping("/households/{householdId}/documents/{assignmentId}/acknowledge")
	fun acknowledgeDocument(
		@PathVariable organizationId: UUID,
		@PathVariable householdId: UUID,
		@PathVariable assignmentId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): DocumentAcknowledgmentResponse =
		documentService.acknowledge(organizationId, householdId, assignmentId, currentUser).toResponse()
}
