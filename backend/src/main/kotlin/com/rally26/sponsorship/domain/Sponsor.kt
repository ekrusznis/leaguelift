package com.rally26.sponsorship.domain

import java.time.Instant
import java.util.UUID

/**
 * A sponsor record — deliberately minimal (name + contact email) per this slice's scope
 * (DESIGN-DOC.md section 14.1 Phase 6 slice 1): no sponsor-contact CRM, no separate
 * manual-entry admin flow. A row is created inline by
 * `sponsorship/application/SponsorshipService.createCheckoutSession` at the moment a
 * public visitor purchases a sponsorship package — mirrors how `Contribution` captures
 * `supporterName`/`supporterEmail` inline rather than through a separate donor-CRM
 * entity. The one thing that *does* require Sponsor to be its own table (rather than
 * inline fields on [com.rally26.sponsorship.domain.Sponsorship], the way Contribution
 * does it) is the logo: a stable `id` is needed to hang a `SPONSOR`/`SPONSOR_LOGO` media
 * assignment off of.
 */
data class Sponsor(
	val id: UUID,
	val organizationId: UUID,
	val name: String,
	val contactEmail: String?,
	/** CRM widening (Phase 6 remainder, ADR-019) — a small, bounded field set an org admin would actually want; not a full multi-contact-per-sponsor CRM. Defaulted to null so pre-existing test call sites keep compiling. */
	val phone: String? = null,
	val companyName: String? = null,
	val notes: String? = null,
	val createdAt: Instant,
	val updatedAt: Instant,
)
