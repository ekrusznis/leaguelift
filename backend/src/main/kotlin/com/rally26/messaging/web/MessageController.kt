package com.rally26.messaging.web

import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageRequest
import com.rally26.common.web.PageResponse
import com.rally26.messaging.application.BroadcastMessageService
import com.rally26.messaging.application.ConversationMessageService
import com.rally26.messaging.domain.MessageScopeType
import com.rally26.messaging.domain.MessageThreadStatus
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/message-threads")
class MessageThreadController(
    private val broadcastService: BroadcastMessageService,
    private val conversationService: ConversationMessageService,
) {
    @GetMapping
    fun list(
        @PathVariable organizationId: UUID,
        @RequestParam(required = false) scopeType: MessageScopeType?,
        @RequestParam(required = false) scopeId: UUID?,
        @RequestParam(required = false) status: MessageThreadStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<MessageThreadResponse> {
        val result = broadcastService.listForManagement(organizationId, scopeType, scopeId, status, PageRequest(page, size), currentUser)
        return PageResponse(result.items.map { it.toResponse() }, result.page, result.size, result.totalElements)
    }

    @PostMapping
    fun create(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: CreateMessageThreadRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<MessageThreadResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            broadcastService
                .createThread(
                    organizationId,
                    request.scopeType,
                    request.scopeId,
                    request.idempotencyKey,
                    request.title,
                    request.audience,
                    request.emailEnabled,
                    request.smsEnabled,
                    currentUser,
                ).toResponse(),
        )

    @GetMapping("/contacts")
    fun contacts(
        @PathVariable organizationId: UUID,
        @RequestParam teamId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<ConversationContactResponse> = conversationService.listContacts(organizationId, teamId, currentUser).map { it.toResponse() }

    @PostMapping("/conversations")
    fun createConversation(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: CreateConversationRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<MessageThreadResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            conversationService
                .createConversation(
                    organizationId = organizationId,
                    teamId = request.teamId,
                    idempotencyKey = request.idempotencyKey,
                    title = request.title,
                    targetUserIds = request.targetUserIds,
                    emailEnabled = request.emailEnabled,
                    smsEnabled = request.smsEnabled,
                    initialMessageIdempotencyKey = request.initialMessageIdempotencyKey,
                    initialMessage = request.initialMessage,
                    currentUser = currentUser,
                ).toResponse(),
        )

    @GetMapping("/{threadId}")
    fun get(
        @PathVariable organizationId: UUID,
        @PathVariable threadId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = broadcastService.getThread(organizationId, threadId, currentUser).toResponse()

    @GetMapping("/{threadId}/messages")
    fun listMessages(
        @PathVariable organizationId: UUID,
        @PathVariable threadId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<BroadcastMessageResponse> {
        val result = broadcastService.listMessagesForManagement(organizationId, threadId, PageRequest(page, size), currentUser)
        return PageResponse(result.items.map { it.toResponse() }, result.page, result.size, result.totalElements)
    }

    @PostMapping("/{threadId}/messages")
    fun send(
        @PathVariable organizationId: UUID,
        @PathVariable threadId: UUID,
        @Valid @RequestBody request: SendBroadcastMessageRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<BroadcastMessageResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            broadcastService.sendMessage(organizationId, threadId, request.idempotencyKey, request.body, currentUser).toResponse(),
        )

    @PostMapping("/{threadId}/archive")
    fun archive(
        @PathVariable organizationId: UUID,
        @PathVariable threadId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = broadcastService.archiveThread(organizationId, threadId, currentUser).toResponse()
}

@RestController
@RequestMapping("/api/v1/me")
class MyMessageController(
    private val broadcastService: BroadcastMessageService,
    private val conversationService: ConversationMessageService,
) {
    @GetMapping("/message-threads")
    fun listThreads(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<MyMessageThreadResponse> {
        val result = broadcastService.listMine(currentUser, PageRequest(page, size))
        return PageResponse(result.items.map { it.toResponse() }, result.page, result.size, result.totalElements)
    }

    @GetMapping("/message-threads/{threadId}/members")
    fun listMembers(
        @PathVariable threadId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<MessageThreadMemberResponse> = conversationService.listMembers(threadId, currentUser).map { it.toResponse() }

    @GetMapping("/message-threads/{threadId}/messages")
    fun listMessages(
        @PathVariable threadId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<MyBroadcastMessageResponse> {
        val result = broadcastService.listMyMessages(currentUser, threadId, PageRequest(page, size))
        return PageResponse(result.items.map { it.toResponse() }, result.page, result.size, result.totalElements)
    }

    @PostMapping("/message-threads/{threadId}/messages")
    fun reply(
        @PathVariable threadId: UUID,
        @Valid @RequestBody request: SendBroadcastMessageRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<BroadcastMessageResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            conversationService.reply(threadId, request.idempotencyKey, request.body, currentUser).toResponse(),
        )

    @PostMapping("/messages/{messageId}/read")
    fun markRead(
        @PathVariable messageId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Void> {
        broadcastService.markRead(currentUser, messageId)
        return ResponseEntity.noContent().build()
    }
}
