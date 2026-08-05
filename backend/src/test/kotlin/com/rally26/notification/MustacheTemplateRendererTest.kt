package com.rally26.notification

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MustacheTemplateRendererTest {
    private val renderer = MustacheTemplateRenderer()

    @Test
    fun `renders the real support-case-created template with substituted values`() {
        val output =
            renderer.render(
                "mail-templates/support-case-created.mustache",
                mapOf(
                    "requesterName" to "Adult User",
                    "caseId" to "11111111-1111-1111-1111-111111111111",
                    "category" to "TECHNICAL PROBLEM",
                    "subject" to "Page would not load",
                    "description" to "The organization page remained blank after I signed in.",
                ),
            )

        assertTrue(output.contains("Hi Adult User,"))
        assertTrue(output.contains("Case: 11111111-1111-1111-1111-111111111111"))
        assertTrue(output.contains("Category: TECHNICAL PROBLEM"))
        assertTrue(output.contains("Subject: Page would not load"))
        assertTrue(output.contains("The organization page remained blank after I signed in."))
        // Confirms every placeholder actually resolved — a leftover "{{" would mean a
        // context key typo/mismatch between this test (and production callers) and the
        // template file.
        assertFalse(output.contains("{{"))
    }

    @Test
    fun `does not HTML-escape values, since this renders plain text`() {
        val output =
            renderer.render(
                "mail-templates/support-case-created.mustache",
                mapOf(
                    "requesterName" to "Adult User",
                    "caseId" to "id",
                    "category" to "OTHER",
                    "subject" to "Bob & Sons question",
                    "description" to "Is this < that or > this?",
                ),
            )

        assertTrue(output.contains("Bob & Sons question"))
        assertTrue(output.contains("Is this < that or > this?"))
    }
}
