package com.rally26.timezone.application

import org.springframework.stereotype.Service

/**
 * Phase 24 slice 24.5 (ADR-071): a static country/state -> canonical IANA timezone
 * heuristic, not a real geocoding lookup — no geocoding credential exists in this
 * codebase (same category of gap as the Printify token in ADR-070) and none is
 * provisioned by this slice. DESIGN-DOC.md only requires a *suggestion* the owner
 * must actively confirm, so an approximate, honestly-labeled heuristic satisfies the
 * acceptance criterion. Multi-zone US states/countries default to their most common
 * zone; returns null (never a guess) when the country/state pair isn't recognized.
 */
@Service
class TimezoneSuggestionService {
    fun suggest(
        country: String?,
        state: String?,
    ): String? {
        val normalizedCountry = country?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
        val normalizedState = state?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        return when (normalizedCountry) {
            "US", "USA", "UNITED STATES" -> normalizedState?.let { US_STATE_TIMEZONES[it] }
            "CA", "CANADA" -> normalizedState?.let { CANADA_PROVINCE_TIMEZONES[it] }
            else -> SINGLE_ZONE_COUNTRIES[normalizedCountry]
        }
    }

    companion object {
        /** Every genuinely multi-zone state gets its most-populous zone (e.g. FL, TX, TN, MI, KY, ID); AZ/HI have no DST and are unambiguous. */
        private val US_STATE_TIMEZONES =
            mapOf(
                "AL" to "America/Chicago",
                "AK" to "America/Anchorage",
                "AZ" to "America/Phoenix",
                "AR" to "America/Chicago",
                "CA" to "America/Los_Angeles",
                "CO" to "America/Denver",
                "CT" to "America/New_York",
                "DE" to "America/New_York",
                "DC" to "America/New_York",
                "FL" to "America/New_York",
                "GA" to "America/New_York",
                "HI" to "Pacific/Honolulu",
                "ID" to "America/Denver",
                "IL" to "America/Chicago",
                "IN" to "America/Indiana/Indianapolis",
                "IA" to "America/Chicago",
                "KS" to "America/Chicago",
                "KY" to "America/New_York",
                "LA" to "America/Chicago",
                "ME" to "America/New_York",
                "MD" to "America/New_York",
                "MA" to "America/New_York",
                "MI" to "America/Detroit",
                "MN" to "America/Chicago",
                "MS" to "America/Chicago",
                "MO" to "America/Chicago",
                "MT" to "America/Denver",
                "NE" to "America/Chicago",
                "NV" to "America/Los_Angeles",
                "NH" to "America/New_York",
                "NJ" to "America/New_York",
                "NM" to "America/Denver",
                "NY" to "America/New_York",
                "NC" to "America/New_York",
                "ND" to "America/Chicago",
                "OH" to "America/New_York",
                "OK" to "America/Chicago",
                "OR" to "America/Los_Angeles",
                "PA" to "America/New_York",
                "RI" to "America/New_York",
                "SC" to "America/New_York",
                "SD" to "America/Chicago",
                "TN" to "America/Chicago",
                "TX" to "America/Chicago",
                "UT" to "America/Denver",
                "VT" to "America/New_York",
                "VA" to "America/New_York",
                "WA" to "America/Los_Angeles",
                "WV" to "America/New_York",
                "WI" to "America/Chicago",
                "WY" to "America/Denver",
            )

        private val CANADA_PROVINCE_TIMEZONES =
            mapOf(
                "AB" to "America/Edmonton",
                "BC" to "America/Vancouver",
                "MB" to "America/Winnipeg",
                "NB" to "America/Moncton",
                "NL" to "America/St_Johns",
                "NS" to "America/Halifax",
                "ON" to "America/Toronto",
                "PE" to "America/Halifax",
                "QC" to "America/Toronto",
                "SK" to "America/Regina",
                "NT" to "America/Yellowknife",
                "NU" to "America/Iqaluit",
                "YT" to "America/Whitehorse",
            )

        /** Countries small/unified enough that no state/province lookup is needed; multi-zone countries (e.g. Australia, Russia) are deliberately omitted rather than defaulted to a single guess. */
        private val SINGLE_ZONE_COUNTRIES =
            mapOf(
                "MX" to "America/Mexico_City",
                "GB" to "Europe/London",
                "UK" to "Europe/London",
                "IE" to "Europe/Dublin",
                "FR" to "Europe/Paris",
                "DE" to "Europe/Berlin",
                "ES" to "Europe/Madrid",
                "IT" to "Europe/Rome",
                "NL" to "Europe/Amsterdam",
                "PT" to "Europe/Lisbon",
                "JP" to "Asia/Tokyo",
                "NZ" to "Pacific/Auckland",
            )
    }
}
