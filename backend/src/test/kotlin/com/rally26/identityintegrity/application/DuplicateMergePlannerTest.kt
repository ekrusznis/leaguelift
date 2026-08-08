package com.rally26.identityintegrity.application

import com.rally26.identityintegrity.domain.DuplicateIdentityKind
import com.rally26.identityintegrity.domain.DuplicateIdentitySummary
import com.rally26.identityintegrity.domain.DuplicateOrganizationMembership
import com.rally26.identityintegrity.domain.IdentityRef
import com.rally26.identityintegrity.domain.MergePlanSeverity
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DuplicateMergePlannerTest {
    @Test
    fun `unlinked guardian shell can preview link to customer app user`() {
        val shell = identity(DuplicateIdentityKind.GUARDIAN_SHELL, email = "parent@example.com")
        val user = identity(DuplicateIdentityKind.APP_USER, email = "PARENT@example.com")
        val preview = DuplicateMergePlanner.plan(shell, user, emptyList())
        assertTrue(preview.canProceedToMutationSlice)
        assertTrue(preview.plan.any { it.code == "CREATE_GUARDIAN_RELATIONSHIP" })
    }

    @Test
    fun `platform administrator blocks user merge`() {
        val source = identity(DuplicateIdentityKind.APP_USER, platformAdmin = true)
        val target = identity(DuplicateIdentityKind.APP_USER)
        val preview = DuplicateMergePlanner.plan(source, target, emptyList())
        assertFalse(preview.canProceedToMutationSlice)
        assertTrue(preview.plan.any { it.code == "PLATFORM_ADMIN_ACCOUNT" && it.severity == MergePlanSeverity.BLOCKER })
    }

    @Test
    fun `conflicting membership role blocks privilege union`() {
        val orgId = UUID.randomUUID()
        val source =
            identity(DuplicateIdentityKind.APP_USER).copy(
                memberships = listOf(DuplicateOrganizationMembership(orgId, "North Club", "ADMINISTRATOR", "ACTIVE")),
            )
        val target =
            identity(DuplicateIdentityKind.APP_USER).copy(
                memberships = listOf(DuplicateOrganizationMembership(orgId, "North Club", "VIEWER", "ACTIVE")),
            )
        val preview = DuplicateMergePlanner.plan(source, target, emptyList())
        assertFalse(preview.canProceedToMutationSlice)
        assertTrue(preview.plan.any { it.code.startsWith("MEMBERSHIP_CONFLICT_") })
    }

    private fun identity(
        kind: DuplicateIdentityKind,
        email: String? = "same@example.com",
        platformAdmin: Boolean = false,
    ) = DuplicateIdentitySummary(
        ref = IdentityRef(kind, UUID.randomUUID()),
        displayName = "Test Person",
        email = email,
        phone = null,
        status = "ACTIVE",
        createdAt = Instant.parse("2026-08-08T12:00:00Z"),
        organizationId = null,
        organizationName = null,
        householdId = null,
        householdName = null,
        linkedUserId = null,
        platformAdministrator = platformAdmin,
        memberships = emptyList(),
        externalIds = emptyList(),
    )
}
