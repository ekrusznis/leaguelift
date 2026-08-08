package com.rally26.identityintegrity.application

import com.rally26.identityintegrity.domain.DuplicateIdentityKind
import com.rally26.identityintegrity.domain.DuplicateIdentitySummary
import com.rally26.identityintegrity.domain.DuplicateMatchEvidence
import com.rally26.identityintegrity.domain.DuplicateMatchType
import com.rally26.identityintegrity.domain.DuplicateOrganizationMembership
import com.rally26.identityintegrity.domain.DuplicateRoleAssignment
import com.rally26.identityintegrity.domain.IdentityDependency
import com.rally26.identityintegrity.domain.IdentityRef
import com.rally26.identityintegrity.domain.MergePlanSeverity
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DuplicateMergePlannerTest {
    @Test
    fun `unlinked guardian shell can preview link to customer app user`() {
        val orgId = UUID.randomUUID()
        val shell = identity(DuplicateIdentityKind.GUARDIAN_SHELL, email = "parent@example.com").copy(organizationId = orgId)
        val user = identity(DuplicateIdentityKind.APP_USER, email = "PARENT@example.com")

        val preview = DuplicateMergePlanner.plan(shell, user, emptyList())

        assertTrue(preview.canProceedToMutationSlice)
        assertEquals(orgId, preview.requiredSupportOrganizationId)
        assertTrue(preview.plan.any { it.code == "CREATE_GUARDIAN_RELATIONSHIP" })
        assertTrue(preview.sharedEvidence.any { it.matchType == DuplicateMatchType.EMAIL })
    }

    @Test
    fun `platform administrator blocks user merge`() {
        val orgId = UUID.randomUUID()
        val source = userWithMembership(orgId, platformAdmin = true)
        val target = identity(DuplicateIdentityKind.APP_USER)

        val preview = DuplicateMergePlanner.plan(source, target, emptyList())

        assertFalse(preview.canProceedToMutationSlice)
        assertTrue(preview.plan.any { it.code == "PLATFORM_ADMIN_ACCOUNT" && it.severity == MergePlanSeverity.BLOCKER })
    }

    @Test
    fun `conflicting membership role blocks privilege union`() {
        val orgId = UUID.randomUUID()
        val source = userWithMembership(orgId, role = "ADMINISTRATOR")
        val target = userWithMembership(orgId, role = "VIEWER")

        val preview = DuplicateMergePlanner.plan(source, target, emptyList())

        assertFalse(preview.canProceedToMutationSlice)
        assertTrue(preview.plan.any { it.code.startsWith("MEMBERSHIP_CONFLICT_") })
    }

    @Test
    fun `clean single organization app user merge can proceed`() {
        val orgId = UUID.randomUUID()
        val source = userWithMembership(orgId)
        val target = identity(DuplicateIdentityKind.APP_USER)

        val preview = DuplicateMergePlanner.plan(source, target, emptyList())

        assertTrue(preview.canProceedToMutationSlice)
        assertEquals(orgId, preview.requiredSupportOrganizationId)
        assertTrue(preview.previewHash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `multi organization app user merge blocks automatic mutation`() {
        val orgA = UUID.randomUUID()
        val orgB = UUID.randomUUID()
        val source =
            identity(DuplicateIdentityKind.APP_USER).copy(
                memberships =
                    listOf(
                        DuplicateOrganizationMembership(orgA, "North Club", "VIEWER", "ACTIVE"),
                        DuplicateOrganizationMembership(orgB, "South Club", "VIEWER", "ACTIVE"),
                    ),
            )

        val preview = DuplicateMergePlanner.plan(source, identity(DuplicateIdentityKind.APP_USER), emptyList())

        assertFalse(preview.canProceedToMutationSlice)
        assertTrue(preview.plan.any { it.code == "MULTI_ORGANIZATION_MERGE_REQUIRES_MANUAL_REVIEW" })
    }

    @Test
    fun `different active role on same resource blocks merge`() {
        val orgId = UUID.randomUUID()
        val teamId = UUID.randomUUID()
        val source =
            userWithMembership(orgId).copy(
                roleAssignments = listOf(DuplicateRoleAssignment(orgId, "TEAM", teamId, "COACH_READ")),
            )
        val target =
            identity(DuplicateIdentityKind.APP_USER).copy(
                roleAssignments = listOf(DuplicateRoleAssignment(orgId, "TEAM", teamId, "TEAM_MANAGER")),
            )

        val preview = DuplicateMergePlanner.plan(source, target, emptyList())

        assertFalse(preview.canProceedToMutationSlice)
        assertTrue(preview.plan.any { it.code.startsWith("ROLE_ASSIGNMENT_CONFLICT_") })
    }

    @Test
    fun `unknown live dependency blocks merge rather than guessing`() {
        val orgId = UUID.randomUUID()
        val source = userWithMembership(orgId)
        val dependency = IdentityDependency("future_identity_reference", "user_id", 1, historical = false)

        val preview = DuplicateMergePlanner.plan(source, identity(DuplicateIdentityKind.APP_USER), listOf(dependency))

        assertFalse(preview.canProceedToMutationSlice)
        assertTrue(preview.plan.any { it.code == "UNSUPPORTED_DEPENDENCY_future_identity_reference_user_id" })
    }

    @Test
    fun `missing shared evidence blocks direct mutation request`() {
        val orgId = UUID.randomUUID()
        val source = userWithMembership(orgId).copy(email = "source@example.com")
        val target = identity(DuplicateIdentityKind.APP_USER, email = "target@example.com")

        val preview = DuplicateMergePlanner.plan(source, target, emptyList(), sharedEvidence = emptyList())

        assertFalse(preview.canProceedToMutationSlice)
        assertTrue(preview.plan.any { it.code == "NO_SHARED_DUPLICATE_EVIDENCE" })
    }

    @Test
    fun `preview hash changes when shared evidence or dependency state changes`() {
        val orgId = UUID.randomUUID()
        val source = userWithMembership(orgId)
        val target = identity(DuplicateIdentityKind.APP_USER)
        val emailEvidence = listOf(DuplicateMatchEvidence(DuplicateMatchType.EMAIL, "same@example.com"))
        val phoneEvidence = emailEvidence + DuplicateMatchEvidence(DuplicateMatchType.PHONE, "5551234567")

        val first = DuplicateMergePlanner.plan(source, target, emptyList(), emailEvidence)
        val second = DuplicateMergePlanner.plan(source, target, emptyList(), phoneEvidence)
        val third =
            DuplicateMergePlanner.plan(
                source,
                target,
                listOf(IdentityDependency("audit_event", "actor_user_id", 2, historical = true)),
                emailEvidence,
            )

        assertNotEquals(first.previewHash, second.previewHash)
        assertNotEquals(first.previewHash, third.previewHash)
    }

    private fun userWithMembership(
        organizationId: UUID,
        role: String = "VIEWER",
        platformAdmin: Boolean = false,
    ) = identity(DuplicateIdentityKind.APP_USER, platformAdmin = platformAdmin).copy(
        memberships = listOf(DuplicateOrganizationMembership(organizationId, "North Club", role, "ACTIVE")),
    )

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
