package com.rally26.store.web

import com.rally26.store.domain.MarkupType
import com.rally26.store.domain.OrganizationMarkupRule
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class OrganizationMarkupRuleResponse(
    val id: UUID,
    /** Null means the org-wide default rule. */
    val printifyBlueprintId: Long?,
    val markupType: String,
    val markupValue: Int,
    val updatedAt: Instant,
)

fun OrganizationMarkupRule.toResponse() = OrganizationMarkupRuleResponse(id, printifyBlueprintId, markupType.name, markupValue, updatedAt)

data class UpsertMarkupRuleRequest(
    /** Null means the org-wide default rule; a real blueprint id means a per-apparel-type override. */
    val printifyBlueprintId: Long? = null,
    @field:NotNull val markupType: MarkupType,
    @field:NotNull @field:Min(0) val markupValue: Int,
)
