package com.rally26.integration.social.infra

import com.rally26.common.error.ValidationException
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.social.application.SocialPublishRequest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FacebookPublishingAdapterTest {
    private val adapter = FacebookPublishingAdapter()

    @Test
    fun `only supports FACEBOOK`() {
        assertTrue(adapter.supports(IntegrationProvider.FACEBOOK))
        assertFalse(adapter.supports(IntegrationProvider.X))
    }

    @Test
    fun `publish fails closed when the connection has no linked Page to post to`() {
        assertFailsWith<ValidationException> {
            adapter.publish(
                SocialPublishRequest(
                    provider = IntegrationProvider.FACEBOOK,
                    accessToken = "token-abc",
                    externalAccountId = null,
                    caption = "Help our team!",
                    publicUrl = "https://rally26.com/campaigns/12u-national",
                ),
            )
        }
    }
}
