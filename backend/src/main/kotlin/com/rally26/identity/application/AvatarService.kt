package com.rally26.identity.application

import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.identity.domain.AppUser
import com.rally26.identity.persistence.AppUserRepository
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.media.domain.UploadLimits
import com.rally26.media.infra.SpacesClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Curated generated-avatar styles, kept in sync with the frontend's installed `@dicebear/collection` imports (`frontend/src/components/Avatar.tsx`) — a mix of "animals/creatures" and "animated people," not every style DiceBear ships. */
val ALLOWED_AVATAR_STYLES = listOf("adventurer", "bottts", "personas", "fun-emoji", "big-ears", "thumbs")
val DEFAULT_AVATAR_STYLE = ALLOWED_AVATAR_STYLES.first()

private val EXTENSION_BY_CONTENT_TYPE = mapOf("image/png" to "png", "image/jpeg" to "jpg", "image/webp" to "webp")
private val AVATAR_READ_TTL: Duration = Duration.ofHours(6)

data class ResolvedAvatar(
    val avatarUrl: String?,
    val avatarSeed: String,
    val avatarStyle: String,
)

data class AvatarUploadUrl(
    val uploadUrl: String,
    val objectKey: String,
    val expiresAt: Instant,
)

/**
 * A personal, org-independent account avatar (nav bar + Settings) — separate from the
 * organization-scoped media pipeline (`com.rally26.media`, V9) and the household-adult/
 * participant PROFILE_PHOTO slot (V31, ADR-045), since Platform Admin accounts and any
 * account browsing outside an organization context still need a nav-bar identity. Reuses
 * [UploadLimits]'s PROFILE_PHOTO content-type/size rules and [SpacesClient]'s presign/
 * head/get primitives — both already org-independent — rather than duplicating them.
 */
@Service
class AvatarService(
    private val appUserRepository: AppUserRepository,
    private val spacesClient: SpacesClient,
) {
    /**
     * Reads always go through a freshly-signed GET URL, never a public-read ACL — same
     * rule as the org media pipeline (DESIGN-DOC.md section 11.3, ADR-012). The TTL is
     * longer than that pipeline's usual 15 minutes: an avatar renders persistently in
     * nav chrome for an entire session rather than being viewed once, so a short TTL
     * would routinely go stale mid-session.
     */
    fun resolve(appUser: AppUser): ResolvedAvatar =
        ResolvedAvatar(
            avatarUrl = appUser.avatarObjectKey?.let { spacesClient.presignedGetUrl(it, AVATAR_READ_TTL) },
            avatarSeed = appUser.avatarSeed ?: appUser.id.toString(),
            avatarStyle = appUser.avatarStyle ?: DEFAULT_AVATAR_STYLE,
        )

    fun requestUpload(
        currentUser: CurrentUser,
        contentType: String,
        fileSizeBytes: Long,
    ): AvatarUploadUrl {
        if (!UploadLimits.isContentTypeAllowed(MediaUsageSlot.PROFILE_PHOTO, contentType)) {
            throw ValidationException(
                "This file type is not allowed for a profile photo. Allowed types: ${UploadLimits.allowedContentTypes(
                    MediaUsageSlot.PROFILE_PHOTO,
                ).joinToString()}.",
            )
        }
        val maxBytes = UploadLimits.maxBytes(MediaUsageSlot.PROFILE_PHOTO, contentType)
        if (fileSizeBytes > maxBytes) {
            throw ValidationException("The file is too large for a profile photo. Must be at most $maxBytes bytes.")
        }
        val extension = EXTENSION_BY_CONTENT_TYPE.getValue(contentType)
        val objectKey = "users/${currentUser.userId}/avatar/${UUID.randomUUID()}.$extension"
        val presigned = spacesClient.presignedPutUrl(objectKey, contentType, Duration.ofMinutes(15))
        return AvatarUploadUrl(presigned.url, objectKey, presigned.expiresAt)
    }

    @Transactional
    fun confirmUpload(
        currentUser: CurrentUser,
        objectKey: String,
    ): ResolvedAvatar {
        if (!objectKey.startsWith("users/${currentUser.userId}/avatar/")) {
            throw ForbiddenException("AVATAR_UPLOAD_ACCESS_DENIED", "You cannot confirm this upload.")
        }
        val head = spacesClient.headObject(objectKey)
        if (!head.exists) {
            throw ValidationException("No file was found at the upload location. Upload the file before confirming.")
        }
        // Every PROFILE_PHOTO content type shares one byte cap (see UploadLimits.maxBytes),
        // so any allowed content type is a valid argument here before the real type is known.
        val maxBytes = UploadLimits.maxBytes(MediaUsageSlot.PROFILE_PHOTO, "image/png")
        val bytes = spacesClient.getObjectBytesCapped(objectKey, maxBytes)
        if (bytes.size.toLong() > maxBytes) {
            throw ValidationException("The uploaded file is too large for a profile photo.")
        }
        UploadLimits.detectContentType(bytes)?.takeIf { UploadLimits.isContentTypeAllowed(MediaUsageSlot.PROFILE_PHOTO, it) }
            ?: throw ValidationException("The uploaded file is not a recognized image.")

        appUserRepository.updateAvatarObjectKey(currentUser.userId, objectKey)
        val appUser =
            appUserRepository.findById(currentUser.userId) ?: error("Authenticated user ${currentUser.userId} has no app_user record")
        return resolve(appUser)
    }

    @Transactional
    fun randomize(currentUser: CurrentUser): ResolvedAvatar {
        appUserRepository.updateAvatarChoice(currentUser.userId, UUID.randomUUID().toString(), ALLOWED_AVATAR_STYLES.random())
        val appUser =
            appUserRepository.findById(currentUser.userId) ?: error("Authenticated user ${currentUser.userId} has no app_user record")
        return resolve(appUser)
    }

    @Transactional
    fun removePhoto(currentUser: CurrentUser): ResolvedAvatar {
        appUserRepository.clearAvatarObjectKey(currentUser.userId)
        val appUser =
            appUserRepository.findById(currentUser.userId) ?: error("Authenticated user ${currentUser.userId} has no app_user record")
        return resolve(appUser)
    }
}
