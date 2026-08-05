package com.rally26.notification

import java.util.UUID

/**
 * The `AnalyticsProvider` seam DESIGN-DOC.md section 17 reserves for a future
 * usage-insights vendor (Phase 9, ADR-025). Deliberately deferred to a logging-only
 * implementation — the founder chose not to pick a vendor (PostHog/Segment/etc.) this
 * phase, mirroring how `EmailProvider` shipped with only `LoggingEmailProvider` for two
 * phases before Resend was wired in (ADR-019, ADR-022). Lives alongside `EmailProvider`/
 * `SmsProvider` since all three are cross-cutting "send something to an external
 * provider" seams, not feature-specific.
 */
interface AnalyticsProvider {
    fun track(event: AnalyticsEvent)
}

data class AnalyticsEvent(
    val name: String,
    val organizationId: UUID?,
    val userId: UUID?,
    /** Keep this small and non-sensitive — see `LoggingAnalyticsProvider`'s doc comment on what never belongs here. */
    val properties: Map<String, String> = emptyMap(),
)
