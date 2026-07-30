package com.leaguelift.document.web

import com.leaguelift.document.domain.DocumentAcknowledgment
import com.leaguelift.media.application.MediaDescriptor
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class AssignDocumentRequest(
	val assetId: UUID,
	@field:Size(max = 255)
	val title: String? = null,
)

data class DocumentResponse(
	val id: UUID,
	val assetId: UUID,
	val url: String,
	val title: String?,
	val contentType: String?,
	val byteSizeBytes: Long?,
	val updatedAt: Instant,
)

fun MediaDescriptor.toDocumentResponse() = DocumentResponse(
	id = assignment.id,
	assetId = assignment.assetId,
	url = url,
	title = assignment.altText,
	contentType = contentType,
	byteSizeBytes = byteSizeBytes,
	updatedAt = assignment.updatedAt,
)

data class DocumentListResponse(val items: List<DocumentResponse>)

data class DocumentAcknowledgmentResponse(
	val id: UUID,
	val householdAdultId: UUID,
	val acknowledgedByUserId: UUID,
	val acknowledgedAt: Instant,
)

fun DocumentAcknowledgment.toResponse() = DocumentAcknowledgmentResponse(
	id = id,
	householdAdultId = householdAdultId,
	acknowledgedByUserId = acknowledgedByUserId,
	acknowledgedAt = acknowledgedAt,
)

data class DocumentAcknowledgmentListResponse(val items: List<DocumentAcknowledgmentResponse>)
