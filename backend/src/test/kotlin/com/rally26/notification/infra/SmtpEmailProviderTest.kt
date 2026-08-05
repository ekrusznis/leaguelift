package com.rally26.notification.infra

import com.rally26.config.SmtpMailProperties
import com.rally26.notification.EmailMessage
import kotlin.test.Test

class SmtpEmailProviderTest {
    /**
     * The default [SmtpMailProperties] (blank username, same "not configured" convention
     * as [com.rally26.config.ResendProperties.apiKey]) is what every environment without a
     * real Google Workspace app password gets — including local/test. `send()` must not
     * attempt a real connection or throw in that case, just log and no-op. A real-send
     * path against an actual SMTP server isn't covered here (would need a fake SMTP
     * dependency this codebase doesn't otherwise carry) — this only proves the
     * unconfigured fallback itself, which is the state every automated test runs in.
     */
    @Test
    fun `does not attempt to send or throw when SMTP is not configured`() {
        val provider = SmtpEmailProvider(SmtpMailProperties())

        provider.send(
            EmailMessage(
                to = "adult@example.com",
                subject = "Test",
                body = "Body",
            ),
        )
    }
}
