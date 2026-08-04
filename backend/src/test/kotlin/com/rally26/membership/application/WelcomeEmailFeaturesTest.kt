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
		assertEquals(expectedRowCount, Regex("<tr>").findAll(html).count())
		WelcomeEmailFeatures.featuresFor(MembershipRole.TEAM_ADMINISTRATOR).forEach { feature ->
			assertTrue(html.contains(feature))
		}
	}
}
