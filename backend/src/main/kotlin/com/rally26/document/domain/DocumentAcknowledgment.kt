package com.rally26.document.domain

import java.time.Instant
import java.util.UUID

/**
 * One guardian's record of having acknowledged/signed one household document
 * (DESIGN-DOC.md section 13, Phase 7 completion). [householdAdultId] is the
 * authoritative record of *who* signed (consistent with `guardian_relationship`
 * treating `household_adult` as the real person, `app_user` as just their login);
 * [acknowledgedByUserId] is kept alongside for audit (which login session performed
 * it).
 */
data class DocumentAcknowledgment(
	val id: UUID,
	val organizationId: UUID,
	val mediaAssignmentId: UUID,
	val householdId: UUID,
	val householdAdultId: UUID,
	val acknowledgedByUserId: UUID,
	val acknowledgedAt: Instant,
	val createdAt: Instant,
)
