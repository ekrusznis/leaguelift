package com.rally26.notification

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(LoggingAnalyticsProvider::class.java)

/**
 * The only `AnalyticsProvider` implementation (Phase 9, ADR-025) — logs the event name
 * and IDs, never `properties` values wholesale (a future real vendor call site should
 * review each property for PII/sensitive content before forwarding it, the same
 * discipline DESIGN-DOC.md section 18.2 already applies to every other log statement;
 * logging them by default here would make that easy to skip). No `@ConditionalOnProperty`
 * toggle exists yet, unlike `EmailProvider`/`SmsProvider` — there is no second
 * implementation to switch to until a vendor is chosen, so a toggle would be dead
 * configuration.
 */
@Component
class LoggingAnalyticsProvider : AnalyticsProvider {
    override fun track(event: AnalyticsEvent) {
        log.debug(
            "Analytics event '{}' (org={}, user={}, {} propert{})",
            event.name,
            event.organizationId,
            event.userId,
            event.properties.size,
            if (event.properties.size ==
                1
            ) {
                "y"
            } else {
                "ies"
            },
        )
    }
}
