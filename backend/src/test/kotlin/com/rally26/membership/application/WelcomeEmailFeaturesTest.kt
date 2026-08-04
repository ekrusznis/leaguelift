package com.rally26.membership.application

import com.rally26.membership.domain.MembershipRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WelcomeEmailFeaturesTest {

	@Test
	fun `every role has at least one feature`() {
		for (role in MembershipRole.entries) {
			assertTrue(WelcomeEmailFeatures.featuresFor(role).isNotEmpty(), "role $role has no features")
		}
	}

	@Test
	fun `owner and administrator see the same full feature set`() {
		assertEquals(WelcomeEmailFeatures.featuresFor(MembershipRole.OWNER), WelcomeEmailFeatures.featuresFor(MembershipRole.ADMINISTRATOR))
	}

	@Test
	fun `viewer does not see manager-only features`() {
		val viewerFeatures = WelcomeEmailFeatures.featuresFor(MembershipRole.VIEWER)
		assertTrue(viewerFeatures.none { it.contains("Sponsorship") })
		assertTrue(viewerFeatures.none { it.contains("Fundraising") })
	}

	@Test
	fun `featuresHtml renders one row per feature and escapes markup characters`() {
		val html = WelcomeEmailFeatures.featuresHtml(MembershipRole.TEAM_ADMINISTRATOR)

		val expectedRowCount = WelcomeEmailFeatures.featuresFor(MembershipRole.TEAM_ADMINISTRATOR).size
		// Each row nests a second `<tr>` inside its own presentation `<table>` (the
		// checkmark-badge + label layout, matching the real Resend template's row
		// markup) alongside the outer row `<tr>` — a literal count of "<tr>" is 2x the
		// row count, not 1x. Count the once-per-row checkmark glyph instead.
		assertEquals(expectedRowCount, Regex("&#10003;").findAll(html).count())
		WelcomeEmailFeatures.featuresFor(MembershipRole.TEAM_ADMINISTRATOR).forEach { feature ->
			// featureRowHtml HTML-escapes &/</> before embedding the label, so a feature
			// containing one of those characters (e.g. "Team schedules & events") never
			// appears in the output verbatim — check for the escaped form it actually
			// renders as, the same way a browser or email client would read it back.
			val escaped = feature.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
			assertTrue(html.contains(escaped), "expected html to contain escaped feature \"$escaped\"")
		}
	}

	@Test
	fun `featuresHtml escapes markup characters instead of injecting them raw`() {
		// TEAM_ADMINISTRATOR_FEATURES' "Team schedules & events" is the one real feature
		// string with a character that needs escaping — assert directly that the raw
		// ampersand never lands unescaped in the output (which would both break the
		// HTML and, worse, be an injection vector if a feature label ever became
		// dynamic/user-influenced).
		val html = WelcomeEmailFeatures.featuresHtml(MembershipRole.TEAM_ADMINISTRATOR)
		assertTrue(html.contains("Team schedules &amp; events"))
		assertTrue(!html.contains("Team schedules & events"))
	}
}
