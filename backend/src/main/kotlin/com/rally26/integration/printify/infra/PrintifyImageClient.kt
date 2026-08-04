package com.rally26.integration.printify.infra

import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

data class PrintifyUploadedImage(val id: String, val fileName: String)

private data class UploadImageRequestDto(val file_name: String, val url: String)
private data class UploadImageResponseDto(val id: String, val file_name: String)

/**
 * Printify's own image library (POST /v1/uploads/images.json) — accepts a
 * publicly-fetchable URL (our media pipeline's short-lived signed GET URL works
 * directly) and returns an id Printify's Products API can then reference. Order
 * submission does *not* need this — the simpler order-time `print_areas` format
 * accepts a raw image URL per position directly (see PrintifyOrderClient); this
 * client exists only for product creation's cost-discovery call
 * (PrintifyProductClient), whose print-area schema requires a Printify image id.
 */
@Component
class PrintifyImageClient(private val printifyRestClient: RestClient) {

	fun uploadImage(fileName: String, sourceUrl: String): PrintifyUploadedImage {
		val dto = printifyRestClient.post()
			.uri("/uploads/images.json")
			.body(UploadImageRequestDto(file_name = fileName, url = sourceUrl))
			.retrieve()
			.body(UploadImageResponseDto::class.java)
			?: error("Printify returned an empty response uploading image $fileName")
		return PrintifyUploadedImage(dto.id, dto.file_name)
	}
}
