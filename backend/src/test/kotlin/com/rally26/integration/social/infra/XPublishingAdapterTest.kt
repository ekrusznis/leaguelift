package com.rally26.integration.social.infra

import com.rally26.integration.core.domain.IntegrationProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XPublishingAdapterTest {
    private val adapter = XPublishingAdapter()

    @Test
    fun `only supports X`() {
        assertTrue(adapter.supports(IntegrationProvider.X))
        assertFalse(adapter.supports(IntegrationProvider.FACEBOOK))
    }

    @Test
    fun `fitToTweetLength keeps a short caption and its URL unchanged`() {
        val url = "https://rally26.com/campaigns/12u-national"
        val caption = "Help our team!\n\nSupport the team:\n$url"

        val result = adapter.fitToTweetLength(caption, url)

        assertTrue(result.contains(url))
        assertTrue(result.length <= 280)
    }

    @Test
    fun `fitToTweetLength truncates a long caption but always keeps the full URL`() {
        val url = "https://rally26.com/campaigns/12u-national"
        val longBody = "Help ".repeat(200)
        val caption = "$longBody\n$url"

        val result = adapter.fitToTweetLength(caption, url)

        assertTrue(result.endsWith(url))
        assertTrue(result.contains("…"))
    }

    @Test
    fun `fitToTweetLength falls back to plain truncation when the caption never contained the URL`() {
        val result = adapter.fitToTweetLength("x".repeat(400), "https://rally26.com/campaigns/unused")

        assertTrue(result.length <= 280)
    }
}
