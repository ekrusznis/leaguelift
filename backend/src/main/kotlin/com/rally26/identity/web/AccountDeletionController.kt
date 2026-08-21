package com.rally26.identity.web

import com.rally26.common.web.CurrentUser
import com.rally26.identity.application.AccountDeletionService
import com.rally26.identity.domain.AccountDeletionRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class AccountDeletionRequestResponse(
    val id: UUID,
    val status: String,
    val requestedAt: Instant,
    val scheduledFor: Instant,
)

fun AccountDeletionRequest.toResponse() = AccountDeletionRequestResponse(id, status.name, requestedAt, scheduledFor)

/** Self-service "delete my account" — see [AccountDeletionService] for the 7-day grace period and Owner block. */
@RestController
@RequestMapping("/api/v1/me/deletion-request")
class AccountDeletionController(
    private val accountDeletionService: AccountDeletionService,
) {
    @PostMapping
    fun request(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<AccountDeletionRequestResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(accountDeletionService.request(currentUser).toResponse())

    @GetMapping
    fun pending(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): AccountDeletionRequestResponse? = accountDeletionService.findPending(currentUser)?.toResponse()

    @DeleteMapping
    fun cancel(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Void> {
        accountDeletionService.cancel(currentUser)
        return ResponseEntity.noContent().build()
    }
}
