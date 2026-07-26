package com.leaguelift.organization.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrganizationSlugTest {

	@Test
	fun `valid slugs are accepted`() {
		assertTrue(isValidSlug("riverside-soccer"))
		assertTrue(isValidSlug("a1"))
		assertTrue(isValidSlug("club123"))
	}

	@Test
	fun `invalid slugs are rejected`() {
		assertFalse(isValidSlug("Riverside-Soccer")) // uppercase
		assertFalse(isValidSlug("-leading-hyphen"))
		assertFalse(isValidSlug("trailing-hyphen-"))
		assertFalse(isValidSlug("has space"))
		assertFalse(isValidSlug(""))
	}

	@Test
	fun `slugify normalizes an organization name`() {
		assertEquals("riverside-youth-soccer", slugify("Riverside Youth Soccer!"))
		assertEquals("fc-united", slugify("  FC  United  "))
	}
}
