package com.rally26.identity.web

import com.rally26.common.web.CurrentUser
import com.rally26.identity.application.AvatarService
import com.rally26.identity.application.ResolvedAvatar
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class RequestAvatarUploadRequest(
    @field:NotBlank val contentType: String,
    @field:Min(1) val fileSizeBytes: Long,
)

data class AvatarUploadUrlResponse(
    val uploadUrl: String,
    val objectKey: String,
    val expiresAt: Instant,
)

data class ConfirmAvatarUploadRequest(
    @field:NotBlank val objectKey: String,
)

data class AvatarResponse(
    val avatarUrl: String?,
    val avatarSeed: String,
    val avatarStyle: String,
)

fun ResolvedAvatar.toResponse() = AvatarResponse(avatarUrl, avatarSeed, avatarStyle)

/** Personal, org-independent account avatar for the nav bar and Settings — see [AvatarService] for why this is separate from the org-scoped media pipeline. */
@RestController
@RequestMapping("/api/v1/me/avatar")
@Validated
class AvatarController(
    private val avatarService: AvatarService,
) {
    @PostMapping("/upload-url")
    fun requestUpload(
        @AuthenticationPrincipal currentUser: CurrentUser,
        @Validated @RequestBody request: RequestAvatarUploadRequest,
    ): AvatarUploadUrlResponse {
        val result = avatarService.requestUpload(currentUser, request.contentType, request.fileSizeBytes)
        return AvatarUploadUrlResponse(result.uploadUrl, result.objectKey, result.expiresAt)
    }

    @PostMapping("/confirm")
    fun confirmUpload(
        @AuthenticationPrincipal currentUser: CurrentUser,
        @Validated @RequestBody request: ConfirmAvatarUploadRequest,
    ): AvatarResponse = avatarService.confirmUpload(currentUser, request.objectKey).toResponse()

    @PostMapping("/randomize")
    fun randomize(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): AvatarResponse = avatarService.randomize(currentUser).toResponse()

    @DeleteMapping
    fun removePhoto(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): AvatarResponse = avatarService.removePhoto(currentUser).toResponse()
}
