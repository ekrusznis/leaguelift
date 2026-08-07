package com.rally26.timezone.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimezoneSuggestionServiceTest {
    private val service = TimezoneSuggestionService()

    @Test
    fun `suggests a US state's zone`() {
        assertEquals("America/Los_Angeles", service.suggest("US", "CA"))
        assertEquals("America/Chicago", service.suggest("US", "TX"))
        assertEquals("America/New_York", service.suggest("US", "NY"))
    }

    @Test
    fun `US lookup is case-insensitive and tolerates whitespace`() {
        assertEquals("America/Denver", service.suggest(" us ", " co "))
        assertEquals("America/Phoenix", service.suggest("USA", "az"))
    }

    @Test
    fun `suggests a Canadian province's zone`() {
        assertEquals("America/Vancouver", service.suggest("CA", "BC"))
        assertEquals("America/Toronto", service.suggest("Canada", "ON"))
    }

    @Test
    fun `suggests a single-zone country with no state needed`() {
        assertEquals("Europe/London", service.suggest("GB", null))
        assertEquals("Asia/Tokyo", service.suggest("JP", "any-ignored-value"))
    }

    @Test
    fun `returns null rather than guessing for an unrecognized country`() {
        assertNull(service.suggest("ZZ", "XX"))
    }

    @Test
    fun `returns null rather than guessing for an unrecognized US state`() {
        assertNull(service.suggest("US", "ZZ"))
    }

    @Test
    fun `returns null when country is missing or blank`() {
        assertNull(service.suggest(null, "CA"))
        assertNull(service.suggest("", "CA"))
        assertNull(service.suggest("   ", "CA"))
    }

    @Test
    fun `returns null for a US address with no state`() {
        assertNull(service.suggest("US", null))
    }
}
