package com.rally26.store.web

import com.rally26.common.web.CurrentUser
import com.rally26.store.application.MarkupRuleService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Swag Shop markup rule engine (Phase 23, DESIGN-DOC.md section 13/14.1) — org default plus optional per-apparel-type overrides. */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/markup-rules")
class MarkupRuleController(
    private val markupRuleService: MarkupRuleService,
) {
    @GetMapping
    fun list(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<OrganizationMarkupRuleResponse> = markupRuleService.listRules(organizationId, currentUser).map { it.toResponse() }

    @PostMapping
    fun upsert(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: UpsertMarkupRuleRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<OrganizationMarkupRuleResponse> {
        val rule =
            markupRuleService.upsertRule(organizationId, request.printifyBlueprintId, request.markupType, request.markupValue, currentUser)
        return ResponseEntity.status(HttpStatus.CREATED).body(rule.toResponse())
    }

    @DeleteMapping("/{ruleId}")
    fun delete(
        @PathVariable organizationId: UUID,
        @PathVariable ruleId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Void> {
        markupRuleService.deleteRule(organizationId, ruleId, currentUser)
        return ResponseEntity.noContent().build()
    }
}
