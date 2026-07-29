package com.leaguelift.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

/**
 * Builds the S3-compatible client/presigner used by the media pipeline (ADR-012).
 * Path-style access works identically against MinIO and DigitalOcean Spaces, so this
 * one setting never needs to differ per environment — only [SpacesProperties.endpoint]
 * and credentials change between local/test/staging/prod.
 */
@Configuration
class SpacesConfig(private val spacesProperties: SpacesProperties) {

	private fun credentialsProvider() = StaticCredentialsProvider.create(
		AwsBasicCredentials.create(spacesProperties.accessKey, spacesProperties.secretKey),
	)

	@Bean
	fun s3Client(): S3Client = S3Client.builder()
		.endpointOverride(URI.create(spacesProperties.endpoint))
		.region(Region.of(spacesProperties.region))
		.credentialsProvider(credentialsProvider())
		.forcePathStyle(true)
		.build()

	@Bean
	fun s3Presigner(): S3Presigner = S3Presigner.builder()
		.endpointOverride(URI.create(spacesProperties.endpoint))
		.region(Region.of(spacesProperties.region))
		.credentialsProvider(credentialsProvider())
		// S3Presigner.Builder has no forcePathStyle() shortcut (unlike S3Client.Builder) —
		// path-style must be set via serviceConfiguration instead.
		.serviceConfiguration(
			S3Configuration.builder()
				.pathStyleAccessEnabled(true)
				.build(),
		)
		.build()
}
