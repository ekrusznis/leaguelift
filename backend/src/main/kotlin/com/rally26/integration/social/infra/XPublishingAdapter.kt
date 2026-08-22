package com.rally26.integration.social.infra

import com.rally26.common.error.ServiceUnavailableException
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.social.application.SocialPublishRequest
import com.rally26.social.application.SocialPublishResult
import com.rally26.social.application.SocialPublishingAdapter
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

private val log = LoggerFactory.getLogger(XPublishingAdapter::class.java)

/**
 * X counts any URL in a tweet's text as a fixed 23 characters (t.co shortening)
 * regardless of its real length — documented at
 * docs.x.com/resources/fundamentals/counting-characters — replicated approximately
 * here rather than exactly (X's real weighting is more nuanced for edge cases like
 * emoji/CJK text; this is a safe, conservative approximation, not a byte-for-byte
 * reimplementation of their counting spec).
 */
private const val X_MAX_TWEET_LENGTH = 280
private const val X_TCO_URL_LENGTH = 23

private data class XTweetRequest(
    val text: String,
)

private data class XTweetResponse(
    val data: XTweetData?,
)

private data class XTweetData(
    val id: String?,
)

@Component
class XPublishingAdapter : SocialPublishingAdapter {
    private val restClient = RestClient.create()

    override fun supports(provider: IntegrationProvider): Boolean = provider == IntegrationProvider.X

    override fun publish(request: SocialPublishRequest): SocialPublishResult {
        val text = fitToTweetLength(request.caption, request.publicUrl)
        val response =
            try {
                restClient
                    .post()
                    .uri("$X_API_BASE_URL/tweets")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${request.accessToken}")
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(XTweetRequest(text))
                    .retrieve()
                    .body(XTweetResponse::class.java)
            } catch (ex: RestClientException) {
                log.warn("X post failed: {}", ex.message)
                throw ServiceUnavailableException("X_PUBLISH_FAILED", "X did not accept this post.")
            }
        val tweetId =
            response?.data?.id ?: throw ServiceUnavailableException("X_PUBLISH_FAILED", "X did not confirm the post.")
        return SocialPublishResult(providerPostId = tweetId, providerPostUrl = "https://x.com/i/web/status/$tweetId")
    }

    /** Keeps the public URL intact at the end, truncating only the caption text ahead of it — see [X_TCO_URL_LENGTH]'s doc comment for why this is approximate, not exact. */
    internal fun fitToTweetLength(
        caption: String,
        publicUrl: String,
    ): String {
        if (!caption.contains(publicUrl)) return caption.take(X_MAX_TWEET_LENGTH)
        val bodyText = caption.substringBefore(publicUrl).trimEnd()
        val effectiveUrlLength = X_TCO_URL_LENGTH + 1 // +1 for the newline separating body from link
        val available = X_MAX_TWEET_LENGTH - effectiveUrlLength
        if (bodyText.length <= available) return "$bodyText\n$publicUrl"
        val truncated = bodyText.take((available - 1).coerceAtLeast(0)).trimEnd() + "…"
        return "$truncated\n$publicUrl"
    }
}
