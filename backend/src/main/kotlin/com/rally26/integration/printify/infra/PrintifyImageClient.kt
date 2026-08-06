package com.rally26.integration.printify.infra

import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

data class PrintifyUploadedImage(
    val id: String,
    val fileName: String,
)

private data class UploadImageContentsRequestDto(
    val file_name: String,
    val contents: String,
)

private data class UploadImageResponseDto(
    val id: String,
    val file_name: String,
)

/**
 * Printify's own image library (POST /v1/uploads/images.json) — returns an id
 * Printify's Products API can then reference. Order submission does *not* need
 * this — the simpler order-time `print_areas` format accepts a raw image URL per
 * position directly (see PrintifyOrderClient); this client exists only for
 * product creation's cost-discovery call (PrintifyProductClient), whose
 * print-area schema requires a Printify image id.
 *
 * Uploads by base64-encoded `contents` rather than Printify's alternative
 * fetch-a-`url` form: `url` requires Printify's servers to reach our storage
 * over the public internet, which a local/self-hosted MinIO or a short-lived
 * signed URL may not satisfy. `contents` has no such requirement — the backend
 * already holds the bytes locally (it can always reach its own storage) and
 * simply forwards them, working identically in every environment.
 */
@Component
class PrintifyImageClient(
    private val printifyRestClient: RestClient,
) {
    fun uploadImage(
        fileName: String,
        contentsBase64: String,
    ): PrintifyUploadedImage {
        val dto =
            printifyRestClient
                .post()
                .uri("/uploads/images.json")
                .body(UploadImageContentsRequestDto(file_name = fileName, contents = contentsBase64))
                .retrieve()
                .body(UploadImageResponseDto::class.java)
                ?: error("Printify returned an empty response uploading image $fileName")
        return PrintifyUploadedImage(dto.id, dto.file_name)
    }
}
