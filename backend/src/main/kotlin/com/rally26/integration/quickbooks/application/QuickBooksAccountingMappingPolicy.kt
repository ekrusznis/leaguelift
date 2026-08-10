package com.rally26.integration.quickbooks.application

import com.rally26.integration.quickbooks.domain.QuickBooksAccount
import com.rally26.integration.quickbooks.domain.QuickBooksAccountMapping
import com.rally26.integration.quickbooks.domain.QuickBooksMappingCompatibility
import com.rally26.integration.quickbooks.domain.QuickBooksMappingDefinition
import com.rally26.integration.quickbooks.domain.QuickBooksMappingEvaluation
import com.rally26.integration.quickbooks.domain.QuickBooksMappingOption
import com.rally26.integration.quickbooks.domain.QuickBooksMappingOptions
import com.rally26.integration.quickbooks.domain.QuickBooksMappingType
import com.rally26.integration.quickbooks.domain.QuickBooksMappingValidation
import com.rally26.integration.quickbooks.domain.QuickBooksMappingValidationStatus
import org.springframework.stereotype.Component

@Component
class QuickBooksAccountingMappingPolicy {
    fun definitions(): List<QuickBooksMappingDefinition> = QuickBooksMappingType.entries.map(::definition)

    fun definition(mappingType: QuickBooksMappingType): QuickBooksMappingDefinition = DEFINITIONS.getValue(mappingType)

    fun evaluate(
        mappingType: QuickBooksMappingType,
        account: QuickBooksAccount,
    ): QuickBooksMappingEvaluation {
        if (!account.active) {
            return QuickBooksMappingEvaluation(
                QuickBooksMappingCompatibility.BLOCKED,
                selectable = false,
                reason = "Inactive QuickBooks accounts cannot be selected.",
            )
        }

        val definition = definition(mappingType)
        val accountType = normalize(account.accountType)
        return when {
            definition.recommendedAccountTypes.any { normalize(it) == accountType } ->
                QuickBooksMappingEvaluation(
                    QuickBooksMappingCompatibility.RECOMMENDED,
                    selectable = true,
                    reason = "${account.accountType} is a recommended account type for ${definition.label}.",
                )
            definition.warningAccountTypes.any { normalize(it) == accountType } ->
                QuickBooksMappingEvaluation(
                    QuickBooksMappingCompatibility.ALLOWED_WITH_WARNING,
                    selectable = true,
                    reason =
                        "${account.accountType} can be used for ${definition.label}, " +
                            "but the owner should confirm the accounting treatment.",
                )
            else ->
                QuickBooksMappingEvaluation(
                    QuickBooksMappingCompatibility.BLOCKED,
                    selectable = false,
                    reason = "${account.accountType} is not compatible with ${definition.label}.",
                )
        }
    }

    fun options(
        mappingType: QuickBooksMappingType,
        accounts: List<QuickBooksAccount>,
    ): QuickBooksMappingOptions {
        val definition = definition(mappingType)
        val evaluations = accounts.associateWith { evaluate(mappingType, it) }
        val suggestedAccountId = selectSuggestion(mappingType, evaluations)
        val options =
            accounts
                .sortedWith(
                    compareBy<QuickBooksAccount> { compatibilityRank(evaluations.getValue(it).compatibility) }
                        .thenByDescending { recommendationScore(mappingType, it) }
                        .thenBy { it.fullyQualifiedName ?: it.name },
                ).map { account ->
                    val evaluation = evaluations.getValue(account)
                    QuickBooksMappingOption(
                        account = account,
                        compatibility = evaluation.compatibility,
                        selectable = evaluation.selectable,
                        suggested = account.id == suggestedAccountId,
                        reason = evaluation.reason,
                    )
                }
        return QuickBooksMappingOptions(definition, suggestedAccountId, options)
    }

    fun validateMappings(
        mappings: List<QuickBooksAccountMapping>,
        accounts: List<QuickBooksAccount>,
    ): List<QuickBooksMappingValidation> {
        val mappingsByType = mappings.associateBy { it.mappingType }
        val accountsById = accounts.associateBy { it.id }
        return QuickBooksMappingType.entries.map { mappingType ->
            val mapping = mappingsByType[mappingType]
            if (mapping == null) {
                return@map QuickBooksMappingValidation(
                    mappingType,
                    null,
                    null,
                    QuickBooksMappingValidationStatus.MISSING,
                    "${definition(mappingType).label} has not been mapped.",
                )
            }
            val account = accountsById[mapping.externalAccountId]
            if (account == null) {
                return@map QuickBooksMappingValidation(
                    mappingType,
                    mapping,
                    null,
                    QuickBooksMappingValidationStatus.ACCOUNT_NOT_FOUND,
                    "The mapped QuickBooks account is no longer present in the refreshed chart of accounts.",
                )
            }
            if (!account.active) {
                return@map QuickBooksMappingValidation(
                    mappingType,
                    mapping,
                    account,
                    QuickBooksMappingValidationStatus.INACTIVE,
                    "The mapped QuickBooks account is inactive and must be replaced.",
                )
            }
            val evaluation = evaluate(mappingType, account)
            when (evaluation.compatibility) {
                QuickBooksMappingCompatibility.RECOMMENDED ->
                    QuickBooksMappingValidation(
                        mappingType,
                        mapping,
                        account,
                        QuickBooksMappingValidationStatus.VALID,
                        "The mapping is valid.",
                    )
                QuickBooksMappingCompatibility.ALLOWED_WITH_WARNING ->
                    if (mapping.warningAcknowledged) {
                        QuickBooksMappingValidation(
                            mappingType,
                            mapping,
                            account,
                            QuickBooksMappingValidationStatus.VALID_WITH_WARNING,
                            "The mapping is valid with the owner's accounting warning acknowledgement.",
                        )
                    } else {
                        QuickBooksMappingValidation(
                            mappingType,
                            mapping,
                            account,
                            QuickBooksMappingValidationStatus.NEEDS_REVIEW,
                            "The account type is now nonstandard for this role and needs owner review.",
                        )
                    }
                QuickBooksMappingCompatibility.BLOCKED ->
                    QuickBooksMappingValidation(
                        mappingType,
                        mapping,
                        account,
                        QuickBooksMappingValidationStatus.INCOMPATIBLE,
                        evaluation.reason,
                    )
            }
        }
    }

    private fun selectSuggestion(
        mappingType: QuickBooksMappingType,
        evaluations: Map<QuickBooksAccount, QuickBooksMappingEvaluation>,
    ): String? {
        val recommended =
            evaluations
                .filterValues { it.compatibility == QuickBooksMappingCompatibility.RECOMMENDED && it.selectable }
                .keys
                .toList()
        if (recommended.size == 1) return recommended.single().id
        if (recommended.isEmpty()) return null

        val scored = recommended.map { it to recommendationScore(mappingType, it) }
        val bestScore = scored.maxOf { it.second }
        if (bestScore <= 0) return null
        val best = scored.filter { it.second == bestScore }
        return best.singleOrNull()?.first?.id
    }

    private fun recommendationScore(
        mappingType: QuickBooksMappingType,
        account: QuickBooksAccount,
    ): Int {
        val haystack =
            listOfNotNull(account.name, account.fullyQualifiedName, account.accountSubType)
                .joinToString(" ")
                .lowercase()
        return KEYWORDS.getValue(mappingType).count { haystack.contains(it) }
    }

    private fun compatibilityRank(compatibility: QuickBooksMappingCompatibility): Int =
        when (compatibility) {
            QuickBooksMappingCompatibility.RECOMMENDED -> 0
            QuickBooksMappingCompatibility.ALLOWED_WITH_WARNING -> 1
            QuickBooksMappingCompatibility.BLOCKED -> 2
        }

    private fun normalize(value: String): String = value.trim().lowercase()

    private companion object {
        val DEFINITIONS =
            mapOf(
                QuickBooksMappingType.PROGRAM_FEE_INCOME to
                    QuickBooksMappingDefinition(
                        QuickBooksMappingType.PROGRAM_FEE_INCOME,
                        "Program fee income",
                        "Income recognized from registration, dues, team, clinic, tournament, or other program fees.",
                        setOf("Income", "Other Income"),
                        emptySet(),
                    ),
                QuickBooksMappingType.SALES_INCOME to
                    QuickBooksMappingDefinition(
                        QuickBooksMappingType.SALES_INCOME,
                        "Merchandise / shop income",
                        "Income recognized from Rally26 merchandise and Swag Shop sales.",
                        setOf("Income", "Other Income"),
                        emptySet(),
                    ),
                QuickBooksMappingType.CONTRIBUTION_INCOME to
                    QuickBooksMappingDefinition(
                        QuickBooksMappingType.CONTRIBUTION_INCOME,
                        "Contribution income",
                        "Income used for fundraising contributions and donations.",
                        setOf("Income", "Other Income"),
                        emptySet(),
                    ),
                QuickBooksMappingType.SPONSORSHIP_INCOME to
                    QuickBooksMappingDefinition(
                        QuickBooksMappingType.SPONSORSHIP_INCOME,
                        "Sponsorship income",
                        "Income used for sponsorship receipts.",
                        setOf("Income", "Other Income"),
                        emptySet(),
                    ),
                QuickBooksMappingType.REFUNDS to
                    QuickBooksMappingDefinition(
                        QuickBooksMappingType.REFUNDS,
                        "Refunds and corrections",
                        "Contra-revenue or expense treatment selected by the organization's accountant.",
                        setOf("Income"),
                        setOf("Other Income", "Expense", "Other Expense"),
                    ),
                QuickBooksMappingType.FEES_RECEIVABLE to
                    QuickBooksMappingDefinition(
                        QuickBooksMappingType.FEES_RECEIVABLE,
                        "Program fees receivable",
                        "Receivable used for assessed program fees before collection under accrual accounting.",
                        setOf("Accounts Receivable"),
                        setOf("Other Current Asset"),
                    ),
                QuickBooksMappingType.BANK_CLEARING to
                    QuickBooksMappingDefinition(
                        QuickBooksMappingType.BANK_CLEARING,
                        "Payment / bank clearing",
                        "Asset account used to represent collected funds before or during settlement.",
                        setOf("Bank"),
                        setOf("Other Current Asset"),
                    ),
                QuickBooksMappingType.PAYOUT_CLEARING to
                    QuickBooksMappingDefinition(
                        QuickBooksMappingType.PAYOUT_CLEARING,
                        "Payout clearing",
                        "Account used to reconcile settlement and payout movements.",
                        setOf("Other Current Asset"),
                        setOf("Bank", "Other Current Liability"),
                    ),
            )

        val KEYWORDS =
            mapOf(
                QuickBooksMappingType.PROGRAM_FEE_INCOME to listOf("registration", "program", "fee", "dues", "tournament", "clinic"),
                QuickBooksMappingType.SALES_INCOME to listOf("merchandise", "apparel", "shop", "sales"),
                QuickBooksMappingType.CONTRIBUTION_INCOME to listOf("contribution", "donation", "fundraising", "fundraiser"),
                QuickBooksMappingType.SPONSORSHIP_INCOME to listOf("sponsor", "sponsorship"),
                QuickBooksMappingType.REFUNDS to listOf("refund", "allowance", "discount", "return"),
                QuickBooksMappingType.FEES_RECEIVABLE to listOf("receivable", "program fee", "registration"),
                QuickBooksMappingType.BANK_CLEARING to listOf("clearing", "stripe", "payment", "undeposited", "checking"),
                QuickBooksMappingType.PAYOUT_CLEARING to listOf("payout", "settlement", "clearing"),
            )
    }
}
