package com.rally26.foundingorg.domain

import java.time.Instant
import java.util.UUID

enum class FoundingPilotStatus { UNREDEEMED, RESERVED, ACTIVE, CONVERTED, EXPIRED }

data class FoundingOrgPromoCode(
    val id: UUID,
    val code: String,
    val reservedByUserId: UUID?,
    val reservedAt: Instant?,
    val organizationId: UUID?,
    val redeemedAt: Instant?,
    val pilotEndsAt: Instant?,
    val pilotStatus: FoundingPilotStatus,
    val nextReminderIndex: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** The plan a founding-promo pilot always grants — hardcoded, not caller-configurable. */
const val FOUNDING_PROMO_PLAN_CODE = "FOUNDING_CLUB"

/** Pilot length (founder-directed, 2026-08-20): 3 months from activation, not registration. */
const val FOUNDING_PILOT_DAYS = 90L

/**
 * Ordered (day offset from activation, outbox event type) reminder schedule. Kept as code,
 * not data, since it never varies per-pilot — mirrors how [FOUNDING_PILOT_DAYS] is also a
 * constant rather than a per-row column. `founding_org_promo_code.next_reminder_index`
 * indexes into this list.
 */
val FOUNDING_PILOT_REMINDER_SCHEDULE: List<Pair<Long, String>> =
    listOf(
        30L to "founding_org.checkin_due",
        60L to "founding_org.checkin_due",
        63L to "founding_org.expiration_warning",
        70L to "founding_org.expiration_warning",
        77L to "founding_org.expiration_warning",
        84L to "founding_org.expiration_warning",
        89L to "founding_org.expiration_warning",
    )
