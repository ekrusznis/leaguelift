package com.rally26.support.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ConflictException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageRequest
import com.rally26.common.web.PageResponse
import com.rally26.membership.application.MembershipService
import com.rally26.outbox.persistence.OutboxEventRepository
import com.rally26.support.domain.SupportCase
import com.rally26.support.domain.SupportCaseCategory
import com.rally26.support.domain.SupportCasePriority
import com.rally26.support.domain.SupportCaseStatus
import com.rally26.support.persistence.SupportCaseRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class SupportCaseService(
    private val repository: SupportCaseRepository,
    private val membershipService: MembershipService,
    private val authorizationService: AuthorizationService,
    private val outboxRepository: OutboxEventRepository,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    @Transactional
    fun createPublic(
        idempotencyKey: String,
        requesterName: String,
        requesterEmail: String,
        category: SupportCaseCategory,
        subject: String,
        description: String,
    ): SupportCase {
        val input = normalize(idempotencyKey, requesterName, requesterEmail, subject, description)
        existingFor(input.key, null, input.email)?.let { return it }
        return create(input, null, null, category)
    }

    @Transactional
    fun createAuthenticated(
        currentUser: CurrentUser,
        idempotencyKey: String,
        organizationId: UUID?,
        category: SupportCaseCategory,
        subject: String,
        description: String,
    ): SupportCase {
        if (organizationId != null) membershipService.requireActiveMembership(organizationId, currentUser)
        val input = normalize(idempotencyKey, currentUser.displayName, currentUser.email, subject, description)
        existingFor(input.key, currentUser.userId, input.email)?.let { return it }
        return create(input, currentUser.userId, organizationId, category)
    }

    fun listMine(
        currentUser: CurrentUser,
        page: PageRequest,
    ): PageResponse<SupportCase> =
        PageResponse(
            repository.listForRequester(currentUser.userId, page),
            page.page,
            page.size,
            repository.countForRequester(currentUser.userId),
        )

    fun getMine(
        currentUser: CurrentUser,
        caseId: UUID,
    ): SupportCase =
        repository.findForRequester(caseId, currentUser.userId)
            ?: throw NotFoundException("SUPPORT_CASE_NOT_FOUND", "The support case could not be found.")

    fun listPlatform(
        currentUser: CurrentUser,
        query: String?,
        status: SupportCaseStatus?,
        priority: SupportCasePriority?,
        category: SupportCaseCategory?,
        organizationId: UUID?,
        page: PageRequest,
    ): PageResponse<SupportCase> {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_SUPPORT_CASE_MANAGE)
        val normalizedQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedQuery != null && normalizedQuery.length > 120) throw ValidationException("Search must not exceed 120 characters.")
        return PageResponse(
            repository.listPlatform(normalizedQuery, status, priority, category, organizationId, page),
            page.page,
            page.size,
            repository.countPlatform(normalizedQuery, status, priority, category, organizationId),
        )
    }

    fun getPlatform(
        currentUser: CurrentUser,
        caseId: UUID,
    ): SupportCase {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_SUPPORT_CASE_MANAGE)
        return repository.findById(caseId) ?: throw NotFoundException("SUPPORT_CASE_NOT_FOUND", "The support case could not be found.")
    }

    @Transactional
    fun updatePlatform(
        currentUser: CurrentUser,
        caseId: UUID,
        status: SupportCaseStatus,
        priority: SupportCasePriority,
        assignedPlatformUserId: UUID?,
        resolution: String?,
    ): SupportCase {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_SUPPORT_CASE_MANAGE)
        val existing =
            repository.findById(caseId) ?: throw NotFoundException("SUPPORT_CASE_NOT_FOUND", "The support case could not be found.")
        validateTransition(existing.status, status)
        if (assignedPlatformUserId != null && !repository.isActivePlatformAdmin(assignedPlatformUserId)) {
            throw ValidationException("The assigned user must hold an active Platform Admin grant.")
        }
        val normalizedResolution = resolution?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedResolution != null &&
            normalizedResolution.length !in 3..4000
        ) {
            throw ValidationException("Resolution must be between 3 and 4,000 characters.")
        }
        if (status in setOf(SupportCaseStatus.RESOLVED, SupportCaseStatus.CLOSED) && normalizedResolution == null) {
            throw ValidationException("A resolution note is required before resolving or closing a support case.")
        }
        val closedAt =
            if (status in
                setOf(SupportCaseStatus.RESOLVED, SupportCaseStatus.CLOSED)
            ) {
                existing.closedAt ?: Instant.now(clock)
            } else {
                null
            }
        repository.updatePlatform(caseId, status, priority, assignedPlatformUserId, normalizedResolution, closedAt)
        val updated = repository.findById(caseId)!!
        // Only a real status transition is worth notifying the requester about — priority
        // or assignment-only edits are internal triage details, not something a customer
        // should get an email for. Unlike support.case.created (SMTP, ADR-059), this goes
        // through the primary EmailProvider/Resend since it's an automated status notice,
        // not the requester's own correspondence — see SupportCaseStatusChangedEmailHandler.
        if (existing.status != updated.status) {
            outboxRepository.insert(
                aggregateType = "SUPPORT_CASE",
                aggregateId = updated.id,
                organizationId = updated.organizationId,
                eventType = "support.case.status_changed",
                payloadJson = objectMapper.writeValueAsString(mapOf("caseId" to updated.id.toString())),
            )
        }
        auditService.record(
            currentUser.userId,
            updated.organizationId,
            "support_case.updated",
            "SUPPORT_CASE",
            updated.id,
            objectMapper.writeValueAsString(
                mapOf(
                    "priorStatus" to existing.status.name,
                    "status" to updated.status.name,
                    "priority" to updated.priority.name,
                ),
            ),
        )
        return updated
    }

    /**
     * A one-way, ad-hoc email to the requester, independent of a status change --
     * distinct from [updatePlatform]'s automatic status-notice email
     * (SupportCaseStatusChangedEmailHandler). Deliberately does not create a reply
     * thread or capture inbound replies (Phase 17 explicitly keeps support cases
     * "one-way... no replies, chat threads" -- this is a platform admin *sending* a
     * message, not a two-way conversation UI).
     */
    @Transactional
    fun sendPlatformEmail(
        currentUser: CurrentUser,
        caseId: UUID,
        subject: String,
        body: String,
    ): SupportCase {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_SUPPORT_CASE_MANAGE)
        val existing =
            repository.findById(caseId) ?: throw NotFoundException("SUPPORT_CASE_NOT_FOUND", "The support case could not be found.")
        val normalizedSubject = subject.trim()
        if (normalizedSubject.length !in 3..200) throw ValidationException("Subject must be between 3 and 200 characters.")
        val normalizedBody = body.trim()
        if (normalizedBody.length !in 3..5000) throw ValidationException("Message must be between 3 and 5,000 characters.")
        outboxRepository.insert(
            aggregateType = "SUPPORT_CASE",
            aggregateId = existing.id,
            organizationId = existing.organizationId,
            eventType = "support.case.admin_email",
            payloadJson =
                objectMapper.writeValueAsString(
                    mapOf(
                        "caseId" to existing.id.toString(),
                        "subject" to normalizedSubject,
                        "body" to normalizedBody,
                    ),
                ),
        )
        auditService.record(
            currentUser.userId,
            existing.organizationId,
            "support_case.admin_email_sent",
            "SUPPORT_CASE",
            existing.id,
            objectMapper.writeValueAsString(mapOf("subject" to normalizedSubject)),
        )
        return existing
    }

    private fun create(
        input: Input,
        requesterUserId: UUID?,
        organizationId: UUID?,
        category: SupportCaseCategory,
    ): SupportCase {
        val created =
            try {
                repository.insert(
                    input.key,
                    organizationId,
                    requesterUserId,
                    input.name,
                    input.email,
                    category,
                    input.subject,
                    input.description,
                )
            } catch (_: DuplicateKeyException) {
                return existingFor(input.key, requesterUserId, input.email)
                    ?: throw ConflictException("SUPPORT_CASE_IDEMPOTENCY_CONFLICT", "This idempotency key is already in use.")
            }
        outboxRepository.insert(
            aggregateType = "SUPPORT_CASE",
            aggregateId = created.id,
            organizationId = created.organizationId,
            eventType = "support.case.created",
            payloadJson = objectMapper.writeValueAsString(mapOf("caseId" to created.id.toString())),
        )
        auditService.record(
            requesterUserId,
            organizationId,
            "support_case.created",
            "SUPPORT_CASE",
            created.id,
            objectMapper.writeValueAsString(mapOf("category" to category.name, "authenticated" to (requesterUserId != null))),
        )
        return created
    }

    private fun existingFor(
        key: String,
        requesterUserId: UUID?,
        requesterEmail: String,
    ): SupportCase? {
        val existing = repository.findByIdempotencyKey(key) ?: return null
        val sameRequester =
            if (requesterUserId != null) {
                existing.requesterUserId == requesterUserId
            } else {
                existing.requesterUserId == null && existing.requesterEmail.equals(requesterEmail, ignoreCase = true)
            }
        if (!sameRequester) throw ConflictException("SUPPORT_CASE_IDEMPOTENCY_CONFLICT", "This idempotency key is already in use.")
        return existing
    }

    private fun normalize(
        key: String,
        name: String,
        email: String,
        subject: String,
        description: String,
    ): Input {
        val normalizedKey = key.trim()
        if (normalizedKey.length !in 8..120 ||
            !normalizedKey.matches(Regex("^[A-Za-z0-9._:-]+$"))
        ) {
            throw ValidationException("Idempotency key must be 8-120 letters, numbers, dots, underscores, colons, or hyphens.")
        }
        val normalizedName = name.trim()
        if (normalizedName.length !in 2..120) throw ValidationException("Requester name must be between 2 and 120 characters.")
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.length !in 3..320 ||
            !normalizedEmail.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
        ) {
            throw ValidationException("Requester email must be valid.")
        }
        val normalizedSubject = subject.trim()
        if (normalizedSubject.length !in 5..200) throw ValidationException("Subject must be between 5 and 200 characters.")
        val normalizedDescription = description.trim()
        if (normalizedDescription.length !in 20..5000) throw ValidationException("Description must be between 20 and 5,000 characters.")
        return Input(normalizedKey, normalizedName, normalizedEmail, normalizedSubject, normalizedDescription)
    }

    private fun validateTransition(
        from: SupportCaseStatus,
        to: SupportCaseStatus,
    ) {
        if (from == to) return
        val allowed =
            when (from) {
                SupportCaseStatus.OPEN ->
                    setOf(
                        SupportCaseStatus.IN_PROGRESS,
                        SupportCaseStatus.WAITING_ON_CUSTOMER,
                        SupportCaseStatus.RESOLVED,
                        SupportCaseStatus.CLOSED,
                    )
                SupportCaseStatus.IN_PROGRESS ->
                    setOf(
                        SupportCaseStatus.OPEN,
                        SupportCaseStatus.WAITING_ON_CUSTOMER,
                        SupportCaseStatus.RESOLVED,
                        SupportCaseStatus.CLOSED,
                    )
                SupportCaseStatus.WAITING_ON_CUSTOMER ->
                    setOf(
                        SupportCaseStatus.IN_PROGRESS,
                        SupportCaseStatus.RESOLVED,
                        SupportCaseStatus.CLOSED,
                    )
                SupportCaseStatus.RESOLVED -> setOf(SupportCaseStatus.IN_PROGRESS, SupportCaseStatus.CLOSED)
                SupportCaseStatus.CLOSED -> emptySet()
            }
        if (to !in
            allowed
        ) {
            throw ConflictException("SUPPORT_CASE_INVALID_TRANSITION", "A support case cannot move from ${from.name} to ${to.name}.")
        }
    }

    private data class Input(
        val key: String,
        val name: String,
        val email: String,
        val subject: String,
        val description: String,
    )
}
