package com.rally26.membership.application

import com.rally26.membership.domain.MembershipRole

/**
 * Role-scoped content for the welcome email (Phase 8 slice 4). Resend template
 * variables are scalar strings — there is no `{{#each}}` loop support — so instead of
 * passing a list, [featuresHtml] pre-renders the whole feature-row block server-side
 * into a single HTML string, injected into the template via an unescaped
 * `{{{FEATURES_HTML}}}` variable. Each row's markup must stay in sync with the
 * `welcome-email` Resend template's expected row shape (a `<tr>` with an orange
 * checkmark badge and a label).
 *
 * Deliberately role-based only, not plan/subscription-based — there is no server-side
 * subscription-tier concept yet (see PRICING_TIERS on the frontend, which is
 * marketing-content-only today), so there's nothing real to key a plan-aware feature
 * list off of.
 */
object WelcomeEmailFeatures {

	private val OWNER_AND_ADMINISTRATOR_FEATURES = listOf(
		"Fundraising campaigns",
		"Team apparel stores",
		"Fee collection & payment plans",
		"Family credits",
		"Sponsorship management",
		"Reports & insights",
	)

	private val TEAM_ADMINISTRATOR_FEATURES = listOf(
		"Team schedules & events",
		"Team communications",
		"Team apparel stores",
	)

	private val TOURNAMENT_ADMINISTRATOR_FEATURES = listOf(
		"Tournament schedules & events",
		"Tournament communications",
	)

	private val VIEWER_FEATURES = listOf(
		"Pay fees for your household",
		"Manage your own profile",
		"View schedules & announcements",
	)

	fun featuresFor(role: MembershipRole): List<String> = when (role) {
		MembershipRole.OWNER, MembershipRole.ADMINISTRATOR -> OWNER_AND_ADMINISTRATOR_FEATURES
		MembershipRole.TEAM_ADMINISTRATOR -> TEAM_ADMINISTRATOR_FEATURES
		MembershipRole.TOURNAMENT_ADMINISTRATOR -> TOURNAMENT_ADMINISTRATOR_FEATURES
		MembershipRole.VIEWER -> VIEWER_FEATURES
	}

	fun featuresHtml(role: MembershipRole): String =
		featuresFor(role).joinToString(separator = "") { feature -> featureRowHtml(feature) }

	private fun featureRowHtml(feature: String): String {
		val escaped = feature
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
		return "<tr><td style=\"padding:10px 0; border-bottom:1px solid #F0F3F8;\">" +
			"<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\"><tr>" +
			"<td style=\"width:20px; vertical-align:top; padding-top:2px;\">" +
			"<div style=\"width:16px;height:16px;border-radius:50%;background-color:#FFF1E8;text-align:center;line-height:16px;\">" +
			"<span style=\"color:#F2600C;font-size:11px;font-weight:800;\">&#10003;</span>" +
			"</div></td>" +
			"<td style=\"font-family:'Inter', Arial, sans-serif; font-size:14px; color:#0B1F33; padding-left:10px;\">$escaped</td>" +
			"</tr></table></td></tr>"
	}
}
