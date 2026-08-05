package com.rally26.support.web

import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageRequest
import com.rally26.common.web.PageResponse
import com.rally26.support.application.SupportArticleService
import com.rally26.support.application.SupportCaseService
import com.rally26.support.domain.SupportArticleStatus
import com.rally26.support.domain.SupportAudience
import com.rally26.support.domain.SupportCaseCategory
import com.rally26.support.domain.SupportCasePriority
import com.rally26.support.domain.SupportCaseStatus
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/public/help/articles")
class PublicHelpController(
    private val service: SupportArticleService,
) {
    @GetMapping fun list(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) category: String?,
    ) = service.listPublic(query, category).map { it.toResponse() }

    @GetMapping("/{slug}")
    fun get(
        @PathVariable slug: String,
    ) = service.getPublic(slug).toResponse()
}

@RestController
@RequestMapping("/api/v1/help/articles")
class AuthenticatedHelpController(
    private val service: SupportArticleService,
) {
    @GetMapping fun list(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) category: String?,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.listAuthenticated(currentUser, query, category).map { it.toResponse() }

    @GetMapping("/{slug}")
    fun get(
        @PathVariable slug: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.getAuthenticated(currentUser, slug).toResponse()
}

@RestController
@RequestMapping("/api/v1/platform/admin/help/articles")
class PlatformHelpController(
    private val service: SupportArticleService,
) {
    @GetMapping fun list(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) status: SupportArticleStatus?,
        @RequestParam(required = false) audience: SupportAudience?,
        @RequestParam(required = false) category: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<SupportArticleResponse> {
        val result = service.listPlatform(currentUser, query, status, audience, category, PageRequest(page, size))
        return PageResponse(result.items.map { it.toResponse() }, result.page, result.size, result.totalElements)
    }

    @PostMapping fun create(
        @Valid @RequestBody request: SaveSupportArticleRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service
        .create(
            currentUser,
            request.slug,
            request.title,
            request.summary,
            request.bodyMarkdown,
            request.category,
            request.audience,
            request.sortOrder,
        ).toResponse()

    @PutMapping("/{articleId}")
    fun update(
        @PathVariable articleId: UUID,
        @Valid @RequestBody request: SaveSupportArticleRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service
        .update(
            currentUser,
            articleId,
            request.slug,
            request.title,
            request.summary,
            request.bodyMarkdown,
            request.category,
            request.audience,
            request.sortOrder,
        ).toResponse()

    @PostMapping("/{articleId}/publish")
    fun publish(
        @PathVariable articleId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.publish(currentUser, articleId).toResponse()

    @PostMapping("/{articleId}/archive")
    fun archive(
        @PathVariable articleId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.archive(currentUser, articleId).toResponse()
}

@RestController
@RequestMapping("/api/v1/public/support-cases")
class PublicSupportCaseController(
    private val service: SupportCaseService,
) {
    @PostMapping fun create(
        @Valid @RequestBody request: PublicSupportCaseRequest,
    ) = service
        .createPublic(
            request.idempotencyKey,
            request.requesterName,
            request.requesterEmail,
            request.category,
            request.subject,
            request.description,
        ).toResponse()
}

@RestController
@RequestMapping("/api/v1/support-cases")
class AuthenticatedSupportCaseController(
    private val service: SupportCaseService,
) {
    @PostMapping fun create(
        @Valid @RequestBody request: AuthenticatedSupportCaseRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service
        .createAuthenticated(
            currentUser,
            request.idempotencyKey,
            request.organizationId,
            request.category,
            request.subject,
            request.description,
        ).toResponse()

    @GetMapping fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<SupportCaseResponse> {
        val result = service.listMine(currentUser, PageRequest(page, size))
        return PageResponse(result.items.map { it.toResponse() }, result.page, result.size, result.totalElements)
    }

    @GetMapping("/{caseId}")
    fun get(
        @PathVariable caseId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.getMine(currentUser, caseId).toResponse()
}

@RestController
@RequestMapping("/api/v1/platform/admin/support-cases")
class PlatformSupportCaseController(
    private val service: SupportCaseService,
) {
    @GetMapping fun list(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) status: SupportCaseStatus?,
        @RequestParam(required = false) priority: SupportCasePriority?,
        @RequestParam(required = false) category: SupportCaseCategory?,
        @RequestParam(required = false) organizationId: UUID?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<SupportCaseResponse> {
        val result = service.listPlatform(currentUser, query, status, priority, category, organizationId, PageRequest(page, size))
        return PageResponse(result.items.map { it.toResponse() }, result.page, result.size, result.totalElements)
    }

    @GetMapping("/{caseId}")
    fun get(
        @PathVariable caseId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.getPlatform(currentUser, caseId).toResponse()

    @PatchMapping("/{caseId}")
    fun update(
        @PathVariable caseId: UUID,
        @Valid @RequestBody request: UpdateSupportCaseRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service
        .updatePlatform(
            currentUser,
            caseId,
            request.status,
            request.priority,
            request.assignedPlatformUserId,
            request.resolution,
        ).toResponse()

    @PostMapping("/{caseId}/send-email")
    fun sendEmail(
        @PathVariable caseId: UUID,
        @Valid @RequestBody request: SendSupportCaseEmailRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.sendPlatformEmail(currentUser, caseId, request.subject, request.body).toResponse()
}
