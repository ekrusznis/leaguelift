package com.rally26.integration.social.infra

import com.rally26.common.error.ServiceUnavailableException
import com.rally26.common.error.ValidationException
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.social.application.SocialPublishRequest
import com.rally26.social.application.SocialPublishResult
import com.rally26.social.application.SocialPublishingAdapter
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

private val log = LoggerFactory.getLogger(FacebookPublishingAdapter::class.java)

private data class FacebookPostResponse(
    val id: String?,
)

/**
 * Real Meta Graph API Page post — `POST /{page-id}/feed` with `message`/`link`
 * (researched, not guessed, per developers.facebook.com/docs/pages-api/posts).
 * `externalAccountId` is the connected Facebook Page's id, already resolved during
 * OAuth connect (see [FacebookAuthorizationAdapter.exchangeCode]'s `/me/accounts`
 * lookup) — a connection with no page (never completed that lookup successfully)
 * can't publish, so this fails closed with a clear message rather than posting to
 * the wrong place.
 */
@Component
class FacebookPublishingAdapter : SocialPublishingAdapter {
    private val restClient = RestClient.create()

    override fun supports(provider: IntegrationProvider): Boolean = provider == IntegrationProvider.FACEBOOK

    override fun publish(request: SocialPublishRequest): SocialPublishResult {
        val pageId =
            request.externalAccountId
                ?: throw ValidationException("This Facebook connection has no linked Page to post to — reconnect Facebook.")
        val body = LinkedMultiValueMap<String, String>()
        body.add("message", request.caption)
        body.add("link", request.publicUrl)
        body.add("access_token", request.accessToken)
        val response =
            try {
                restClient
                    .post()
                    .uri("$META_GRAPH_API_BASE_URL/$pageId/feed")
                    .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                    .body(body)
                    .retrieve()
                    .body(FacebookPostResponse::class.java)
            } catch (ex: RestClientException) {
                log.warn("Facebook post failed: {}", ex.message)
                throw ServiceUnavailableException("FACEBOOK_PUBLISH_FAILED", "Facebook did not accept this post.")
            }
        val postId =
            response?.id ?: throw ServiceUnavailableException("FACEBOOK_PUBLISH_FAILED", "Facebook did not confirm the post.")
        // Graph API returns "{page-id}_{post-id}" — the post's own numeric id is
        // after the underscore; a real Page post's public URL follows this shape.
        val postUrl = "https://www.facebook.com/$postId"
        return SocialPublishResult(providerPostId = postId, providerPostUrl = postUrl)
    }
}
