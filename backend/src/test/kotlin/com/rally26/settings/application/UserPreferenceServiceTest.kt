package com.rally26.settings.application

import com.rally26.settings.domain.AppearancePreference
import com.rally26.settings.domain.MediaVisibilityDefault
import com.rally26.settings.domain.UserPreference
import com.rally26.settings.persistence.UserPreferenceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class UserPreferenceServiceTest {
    private val repository = mockk<UserPreferenceRepository>()
    private val service = UserPreferenceService(repository)

    @Test
    fun `missing row uses system appearance and Private media default without writing a default row`() {
        val userId = UUID.randomUUID()
        every { repository.findByUserId(userId) } returns null

        val preferences = service.get(userId)

        assertEquals(AppearancePreference.SYSTEM, preferences.appearance)
        assertEquals(MediaVisibilityDefault.PRIVATE, preferences.defaultMediaVisibility)
        verify(exactly = 1) { repository.findByUserId(userId) }
    }

    @Test
    fun `default media visibility update returns persisted typed preference`() {
        val userId = UUID.randomUUID()
        val now = Instant.parse("2026-08-20T20:00:00Z")
        every {
            repository.upsertDefaultMediaVisibility(userId, MediaVisibilityDefault.PUBLIC)
        } returns
            UserPreference(
                userId = userId,
                appearance = AppearancePreference.SYSTEM,
                defaultMediaVisibility = MediaVisibilityDefault.PUBLIC,
                createdAt = now,
                updatedAt = now,
            )

        val preferences = service.updateDefaultMediaVisibility(userId, MediaVisibilityDefault.PUBLIC)

        assertEquals(MediaVisibilityDefault.PUBLIC, preferences.defaultMediaVisibility)
        verify(exactly = 1) { repository.upsertDefaultMediaVisibility(userId, MediaVisibilityDefault.PUBLIC) }
    }

    @Test
    fun `defaultMediaVisibility convenience accessor reads through get`() {
        val userId = UUID.randomUUID()
        every { repository.findByUserId(userId) } returns null

        assertEquals(MediaVisibilityDefault.PRIVATE, service.defaultMediaVisibility(userId))
    }

    @Test
    fun `appearance update returns persisted typed preference`() {
        val userId = UUID.randomUUID()
        val now = Instant.parse("2026-08-08T20:00:00Z")
        every {
            repository.upsertAppearance(userId, AppearancePreference.DARK)
        } returns
            UserPreference(
                userId = userId,
                appearance = AppearancePreference.DARK,
                createdAt = now,
                updatedAt = now,
            )

        val preferences = service.updateAppearance(userId, AppearancePreference.DARK)

        assertEquals(AppearancePreference.DARK, preferences.appearance)
        verify(exactly = 1) { repository.upsertAppearance(userId, AppearancePreference.DARK) }
    }
}
