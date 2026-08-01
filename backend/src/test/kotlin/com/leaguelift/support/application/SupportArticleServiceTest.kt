package com.leaguelift.support.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.leaguelift.audit.application.AuditService
import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.authorization.domain.AuthorizationContext
import com.leaguelift.authorization.domain.ContextType
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.support.domain.SupportArticle
import com.leaguelift.support.domain.SupportArticleStatus
import com.leaguelift.support.domain.SupportAudience
import com.leaguelift.support.persistence.SupportArticleRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class SupportArticleServiceTest {
	private val repository = mockk<SupportArticleRepository>()
	private val authorizationService = mockk<AuthorizationService>()
	private val auditService = mockk<AuditService>()
	private val service = SupportArticleService(
		repository,
		authorizationService,
		auditService,
		jacksonObjectMapper(),
		Clock.fixed(Instant.parse("2026-08-01T13:30:00Z"), ZoneOffset.UTC),
	)
	private val user = CurrentUser(UUID.randomUUID(), "coach@example.com", "Coach User")

	@Test
	fun `public catalog can only request public articles`() {
		every { repository.listPublished(setOf(SupportAudience.PUBLIC), null, null) } returns listOf(article(SupportAudience.PUBLIC))

		val result = service.listPublic(null, null)

		assertEquals(1, result.size)
		verify(exactly = 1) { repository.listPublished(setOf(SupportAudience.PUBLIC), null, null) }
	}

	@Test
	fun `authenticated catalog combines public and role audiences`() {
		every { authorizationService.listContexts(user) } returns listOf(
			AuthorizationContext(
				contextType = ContextType.TEAM,
				resourceId = UUID.randomUUID(),
				organizationId = UUID.randomUUID(),
				label = "U14 Blue",
				role = "TEAM_MANAGER",
				capabilities = emptySet(),
			),
			AuthorizationContext(
				contextType = ContextType.HOUSEHOLD,
				resourceId = UUID.randomUUID(),
				organizationId = UUID.randomUUID(),
				label = "Morgan Household",
				role = "GUARDIAN",
				capabilities = emptySet(),
			),
		)
		val expected = setOf(SupportAudience.PUBLIC, SupportAudience.COACH, SupportAudience.GUARDIAN)
		every { repository.listPublished(expected, null, null) } returns listOf(article(SupportAudience.COACH))

		val result = service.listAuthenticated(user, null, null)

		assertEquals(SupportAudience.COACH, result.single().audience)
		verify(exactly = 1) { repository.listPublished(expected, null, null) }
	}

	private fun article(audience: SupportAudience) = SupportArticle(
		id = UUID.randomUUID(),
		slug = "getting-started",
		title = "Getting started",
		summary = "A practical introduction to the LeagueLift workspace.",
		bodyMarkdown = "## Start here\n\nUse the organization workspace to begin setup.",
		category = "Getting Started",
		audience = audience,
		status = SupportArticleStatus.PUBLISHED,
		sortOrder = 10,
		publishedAt = Instant.parse("2026-08-01T13:30:00Z"),
		createdBy = null,
		updatedBy = null,
		createdAt = Instant.parse("2026-08-01T13:30:00Z"),
		updatedAt = Instant.parse("2026-08-01T13:30:00Z"),
	)
}
