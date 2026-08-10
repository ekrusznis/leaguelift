package com.rally26.integration.quickbooks.application

import com.rally26.integration.quickbooks.domain.QuickBooksFinancialSourceType
import com.rally26.integration.quickbooks.domain.QuickBooksMappingType
import com.rally26.integration.quickbooks.domain.QuickBooksPostingIntentDefinition
import com.rally26.integration.quickbooks.domain.QuickBooksPostingIntentInput
import com.rally26.integration.quickbooks.domain.QuickBooksPostingIntentValidation
import com.rally26.integration.quickbooks.domain.QuickBooksPostingLegDefinition
import com.rally26.integration.quickbooks.domain.QuickBooksPostingSide
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class QuickBooksPostingIntentPolicy {
    fun definitions(): List<QuickBooksPostingIntentDefinition> = QuickBooksFinancialSourceType.entries.map(::definition)

    fun definition(sourceType: QuickBooksFinancialSourceType): QuickBooksPostingIntentDefinition = DEFINITIONS.getValue(sourceType)

    fun validate(
        input: QuickBooksPostingIntentInput,
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): QuickBooksPostingIntentValidation {
        val diagnostics = mutableListOf<String>()
        if (periodEnd.isBefore(periodStart)) diagnostics += "Preview period end must be on or after period start."
        if (input.sourceReference.isBlank()) diagnostics += "Source reference is required."
        if (input.sourceReference.length > 200) diagnostics += "Source reference must not exceed 200 characters."
        if (input.amountMinor <= 0) {
            diagnostics += "Posting intent amount must be positive; debit/credit direction is defined by the posting intent."
        }
        if (!CURRENCY.matches(input.currency)) diagnostics += "Currency must be a three-letter uppercase code."
        if (input.occurredOn.isBefore(periodStart) || input.occurredOn.isAfter(periodEnd)) {
            diagnostics += "Posting intent date must fall inside the preview period."
        }
        return QuickBooksPostingIntentValidation(
            valid = diagnostics.isEmpty(),
            diagnostics = diagnostics,
            requiredMappings = definition(input.sourceType).legs.map { it.mappingType }.toSet(),
        )
    }

    private companion object {
        val CURRENCY = Regex("^[A-Z]{3}$")
        val DEFINITIONS =
            mapOf(
                QuickBooksFinancialSourceType.PROGRAM_FEE_ASSESSMENT to
                    QuickBooksPostingIntentDefinition(
                        QuickBooksFinancialSourceType.PROGRAM_FEE_ASSESSMENT,
                        "Recognize an assessed program fee under accrual accounting.",
                        listOf(
                            QuickBooksPostingLegDefinition(QuickBooksPostingSide.DEBIT, QuickBooksMappingType.FEES_RECEIVABLE),
                            QuickBooksPostingLegDefinition(QuickBooksPostingSide.CREDIT, QuickBooksMappingType.PROGRAM_FEE_INCOME),
                        ),
                    ),
                QuickBooksFinancialSourceType.PROGRAM_FEE_PAYMENT to
                    QuickBooksPostingIntentDefinition(
                        QuickBooksFinancialSourceType.PROGRAM_FEE_PAYMENT,
                        "Record collection of a previously assessed program fee.",
                        listOf(
                            QuickBooksPostingLegDefinition(QuickBooksPostingSide.DEBIT, QuickBooksMappingType.BANK_CLEARING),
                            QuickBooksPostingLegDefinition(QuickBooksPostingSide.CREDIT, QuickBooksMappingType.FEES_RECEIVABLE),
                        ),
                    ),
                QuickBooksFinancialSourceType.MERCHANDISE_SALE to
                    QuickBooksPostingIntentDefinition(
                        QuickBooksFinancialSourceType.MERCHANDISE_SALE,
                        "Recognize Rally26 merchandise or Swag Shop revenue.",
                        listOf(
                            QuickBooksPostingLegDefinition(QuickBooksPostingSide.DEBIT, QuickBooksMappingType.BANK_CLEARING),
                            QuickBooksPostingLegDefinition(QuickBooksPostingSide.CREDIT, QuickBooksMappingType.SALES_INCOME),
                        ),
                    ),
                QuickBooksFinancialSourceType.CONTRIBUTION to
                    QuickBooksPostingIntentDefinition(
                        QuickBooksFinancialSourceType.CONTRIBUTION,
                        "Recognize a confirmed fundraising contribution.",
                        listOf(
                            QuickBooksPostingLegDefinition(QuickBooksPostingSide.DEBIT, QuickBooksMappingType.BANK_CLEARING),
                            QuickBooksPostingLegDefinition(QuickBooksPostingSide.CREDIT, QuickBooksMappingType.CONTRIBUTION_INCOME),
                        ),
                    ),
                QuickBooksFinancialSourceType.SPONSORSHIP to
                    QuickBooksPostingIntentDefinition(
                        QuickBooksFinancialSourceType.SPONSORSHIP,
                        "Recognize a confirmed sponsorship receipt.",
                        listOf(
                            QuickBooksPostingLegDefinition(QuickBooksPostingSide.DEBIT, QuickBooksMappingType.BANK_CLEARING),
                            QuickBooksPostingLegDefinition(QuickBooksPostingSide.CREDIT, QuickBooksMappingType.SPONSORSHIP_INCOME),
                        ),
                    ),
                QuickBooksFinancialSourceType.REFUND_OR_CORRECTION to
                    QuickBooksPostingIntentDefinition(
                        QuickBooksFinancialSourceType.REFUND_OR_CORRECTION,
                        "Represent a refund or approved financial correction without guessing its original revenue category.",
                        listOf(
                            QuickBooksPostingLegDefinition(QuickBooksPostingSide.DEBIT, QuickBooksMappingType.REFUNDS),
                            QuickBooksPostingLegDefinition(QuickBooksPostingSide.CREDIT, QuickBooksMappingType.BANK_CLEARING),
                        ),
                    ),
                QuickBooksFinancialSourceType.PAYOUT_SETTLEMENT to
                    QuickBooksPostingIntentDefinition(
                        QuickBooksFinancialSourceType.PAYOUT_SETTLEMENT,
                        "Move settled funds between payout clearing and the owner's selected bank/clearing account.",
                        listOf(
                            QuickBooksPostingLegDefinition(QuickBooksPostingSide.DEBIT, QuickBooksMappingType.BANK_CLEARING),
                            QuickBooksPostingLegDefinition(QuickBooksPostingSide.CREDIT, QuickBooksMappingType.PAYOUT_CLEARING),
                        ),
                    ),
            )
    }
}
