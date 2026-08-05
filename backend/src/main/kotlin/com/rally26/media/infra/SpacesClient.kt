package com.rally26.media.infra

import com.rally26.config.SpacesProperties
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration
import java.time.Instant

/**
 * Thin seam over the AWS SDK so no other file in the media module imports SDK types
 * directly — storage provider stays swappable without touching business logic
 * (DESIGN-DOC.md section 11.1, ADR-012).
 */
data class PresignedUpload(
    val url: String,
    val expiresAt: Instant,
)

data class ObjectHead(
    val exists: Boolean,
    val contentLength: Long?,
)

@Component
class SpacesClient(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val spacesProperties: SpacesProperties,
) {
    fun presignedPutUrl(
        key: String,
        contentType: String,
        ttl: Duration = Duration.ofMinutes(15),
    ): PresignedUpload {
        val request =
            PutObjectRequest
                .builder()
                .bucket(spacesProperties.bucket)
                .key(key)
                .contentType(contentType)
                .build()
        val presigned =
            s3Presigner.presignPutObject(
                PutObjectPresignRequest
                    .builder()
                    .signatureDuration(ttl)
                    .putObjectRequest(request)
                    .build(),
            )
        return PresignedUpload(presigned.url().toString(), Instant.now().plus(ttl))
    }

    fun presignedGetUrl(
        key: String,
        ttl: Duration = Duration.ofMinutes(15),
    ): String {
        val request =
            GetObjectRequest
                .builder()
                .bucket(spacesProperties.bucket)
                .key(key)
                .build()
        val presigned =
            s3Presigner.presignGetObject(
                GetObjectPresignRequest
                    .builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(request)
                    .build(),
            )
        return presigned.url().toString()
    }

    fun headObject(key: String): ObjectHead =
        try {
            val response =
                s3Client.headObject(
                    HeadObjectRequest
                        .builder()
                        .bucket(spacesProperties.bucket)
                        .key(key)
                        .build(),
                )
            ObjectHead(exists = true, contentLength = response.contentLength())
        } catch (e: NoSuchKeyException) {
            ObjectHead(exists = false, contentLength = null)
        }

    /**
     * Reads up to [maxBytes] + 1 bytes so callers can detect (and reject) an
     * over-limit object without risking an unbounded read into JVM heap.
     */
    fun getObjectBytesCapped(
        key: String,
        maxBytes: Long,
    ): ByteArray {
        val request =
            GetObjectRequest
                .builder()
                .bucket(spacesProperties.bucket)
                .key(key)
                .range("bytes=0-$maxBytes")
                .build()
        s3Client.getObject(request).use { stream -> return stream.readAllBytes() }
    }

    /** Test/manual-verification helper — not used by the upload flow (browser uploads directly). */
    fun putObject(
        key: String,
        contentType: String,
        bytes: ByteArray,
    ) {
        s3Client.putObject(
            PutObjectRequest
                .builder()
                .bucket(spacesProperties.bucket)
                .key(key)
                .contentType(contentType)
                .build(),
            RequestBody.fromBytes(bytes),
        )
    }
}
