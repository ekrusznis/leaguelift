package com.rally26.messaging.persistence

import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.messaging.domain.MessageAudience
import com.rally26.messaging.domain.MessageSafetyReportReason
import com.rally26.messaging.domain.MessageSafetyReportStatus
import com.rally26.messaging.domain.MessageSafetyReportTarget
import com.rally26.messaging.domain.MessageScopeType
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.OrganizationType
import com.rally26.testsupport.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Repro/fix test for LAUNCH-READINESS.md LR-023: reviewing a message safety report to
 * a non-terminal status (`IN_REVIEW` — what the coach "Start review" button does) 500'd
 * on every attempt. Root cause: `MessageSafetyRepository.updateReportStatus()`'s
 * `resolved_at = case when :resolved then :now else null end` left both branches of the
 * CASE untyped; Postgres's extended query protocol resolved the whole expression as
 * `text` rather than `timestamptz`, so the column assignment failed with
 * `ERROR: column "resolved_at" is of type timestamp with time zone but expression is of
 * type text` — a deterministic Postgres wire-protocol error, same class of bug as
 * LR-022, only a real-Postgres test (not a mocked repository) can catch it.
 */
class MessageSafetyRepositoryIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var passwordAuthenticationService: PasswordAuthenticationService

    @Autowired
    lateinit var organizationService: OrganizationService

    @Autowired
    lateinit var messageRepository: MessageRepository

    @Autowired
    lateinit var messageSafetyRepository: MessageSafetyRepository

    @Test
    fun `reviewing a report to a non-terminal status (IN_REVIEW) succeeds and leaves resolved_at null`() {
        val appUser = passwordAuthenticationService.register("msg-safety-${System.nanoTime()}@example.com", "password1234", "Test Owner")
        val currentUser = passwordAuthenticationService.toCurrentUser(appUser)
        val organization =
            organizationService.create(
                "Message Safety Test Org",
                "message-safety-org-${System.nanoTime()}",
                OrganizationType.RECREATIONAL_LEAGUE,
                currentUser,
            )

        val thread =
            messageRepository.insertThread(
                organization.id,
                MessageScopeType.ORGANIZATION,
                organization.id,
                "idem-${System.nanoTime()}",
                "Test broadcast",
                MessageAudience.ALL,
                emailEnabled = true,
                smsEnabled = false,
                createdByUserId = currentUser.userId,
            )
        val message =
            messageRepository.insertMessage(
                organization.id,
                thread.id,
                currentUser.userId,
                "idem-msg-${System.nanoTime()}",
                "Hello team",
                Instant.now(),
            )
        val report =
            messageSafetyRepository.insertReport(
                MessageSafetyReportTarget(organization.id, thread.id, message.id, MessageScopeType.ORGANIZATION, organization.id),
                currentUser.userId,
                MessageSafetyReportReason.OTHER,
                "QA regression test",
                Instant.now(),
            )

        val updated =
            messageSafetyRepository.updateReportStatus(
                report.id,
                organization.id,
                MessageSafetyReportStatus.IN_REVIEW,
                currentUser.userId,
                null,
                Instant.now(),
            )

        assertEquals(1, updated, "expected the update to affect exactly one row, not throw")
        val reloaded = messageSafetyRepository.findReportById(report.id, organization.id)
        assertEquals(MessageSafetyReportStatus.IN_REVIEW, reloaded?.status)
        assertNull(reloaded?.resolvedAt, "IN_REVIEW is non-terminal, resolved_at must stay null")
    }
}
