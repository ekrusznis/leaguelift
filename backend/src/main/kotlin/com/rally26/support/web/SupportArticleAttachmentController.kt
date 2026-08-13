package com.rally26.support.web

import com.rally26.common.web.CurrentUser
import com.rally26.media.application.MediaDescriptor
import com.rally26.media.application.MediaReadService
import com.rally26.media.application.MediaUploadService
import com.rally26.media.domain.MediaEntityType
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.media.web.RequestUploadResponse
import com.rally26.media.web.toConfirmResponse
import com.rally26.media.web.toResponse
import com.rally26.support.application.SupportArticleAttachmentService
import com.rally26.support.domain.PlatformOrganization
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class RequestArticleAttachmentUploadRequest(
    @field:NotBlank @field:Size(min = 1, max = 255) val fileName: String,
    @field:NotBlank val contentType: String,
    @field:Min(1) val fileSizeBytes: Long,
)

data class AddArticleAttachmentRequest(
    val assetId: UUID,
)

/**
 * Distinct from the generic [com.rally26.media.web.MediaAssignmentResponse] (which has
 * no `id` — every other consumer of that DTO targets a single-active-slot assignment by
 * usage slot alone). Article attachments are a gallery, same as household media's own
 * [com.rally26.media.web.HouseholdMediaResponse] precedent, so the frontend needs the
 * assignment's own id both to target a delete and to build the `attachment:<id>`
 * markdown embed token it inserts into the body.
 */
data class ArticleAttachmentResponse(
    val id: UUID,
    val assetId: UUID,
    val url: String,
    val contentType: String?,
    val byteSizeBytes: Long?,
    val widthPx: Int?,
    val heightPx: Int?,
    val createdAt: Instant,
)

fun MediaDescriptor.toArticleAttachmentResponse() =
    ArticleAttachmentResponse(
        id = assignment.id,
        assetId = assignment.assetId,
        url = url,
        contentType = contentType,
        byteSizeBytes = byteSizeBytes,
        widthPx = widthPx,
        heightPx = heightPx,
        createdAt = assignment.createdAt,
    )

/**
 * Help Center article attachments (Track 4, 2026-08-13). A separate, platform-scoped
 * upload flow rather than reusing the generic per-organization media endpoints under
 * `/organizations/{organizationId}/media` — an article has no real owning organization
 * to put in that URL (see [PlatformOrganization]), so this owns the full request/
 * confirm/add/remove lifecycle for this one usage slot instead of just wrapping the
 * generic endpoints the way household media does.
 */
@RestController
@RequestMapping("/api/v1/platform/admin/help/articles/{articleId}/attachments")
class SupportArticleAttachmentController(
    private val mediaUploadService: MediaUploadService,
    private val attachmentService: SupportArticleAttachmentService,
    private val mediaReadService: MediaReadService,
) {
    @PostMapping("/uploads")
    fun requestUpload(
        @PathVariable articleId: UUID,
        @Valid @RequestBody request: RequestArticleAttachmentUploadRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<RequestUploadResponse> {
        val result =
            mediaUploadService.requestUpload(
                PlatformOrganization.ID,
                MediaUsageSlot.ARTICLE_ATTACHMENT,
                request.fileName,
                request.contentType,
                request.fileSizeBytes,
                currentUser,
                MediaEntityType.SUPPORT_ARTICLE,
                articleId,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(result.toResponse())
    }

    @PostMapping("/uploads/{assetId}/confirm")
    fun confirmUpload(
        @PathVariable articleId: UUID,
        @PathVariable assetId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = mediaUploadService.confirmUpload(PlatformOrganization.ID, assetId, currentUser).asset.toConfirmResponse()

    @GetMapping
    fun list(
        @PathVariable articleId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = ArticleAttachmentListResponse(
        mediaReadService.describeAll(attachmentService.list(currentUser, articleId)).map { it.toArticleAttachmentResponse() },
    )

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun add(
        @PathVariable articleId: UUID,
        @Valid @RequestBody request: AddArticleAttachmentRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ArticleAttachmentResponse {
        val assignment = attachmentService.add(currentUser, articleId, request.assetId)
        val descriptor = mediaReadService.describe(assignment) ?: error("Newly added attachment ${assignment.id} must exist.")
        return descriptor.toArticleAttachmentResponse()
    }

    @DeleteMapping("/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(
        @PathVariable articleId: UUID,
        @PathVariable assignmentId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = attachmentService.remove(currentUser, articleId, assignmentId)
}

data class ArticleAttachmentListResponse(
    val items: List<ArticleAttachmentResponse>,
)
