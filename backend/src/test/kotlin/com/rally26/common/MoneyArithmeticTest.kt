package com.rally26.common

import com.rally26.fee.domain.FeeAdjustment
import com.rally26.fee.domain.FeeBalance
import com.rally26.fee.domain.FeePayment
import com.rally26.fundraising.domain.Campaign
import com.rally26.fundraising.domain.Contribution
import com.rally26.ledger.domain.LedgerEntry
import com.rally26.ledger.persistence.LedgerEntryRepository
import com.rally26.order.domain.Order
import com.rally26.order.domain.OrderItem
import com.rally26.sponsorship.domain.Sponsorship
import com.rally26.store.domain.ProductVariant
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * DESIGN-DOC.md section 18.1's "money arithmetic never uses floating point" and
 * "historical ledger entries cannot be edited" critical scenarios, enforced as
 * reflection-based regression checks rather than left to manual code review — a
 * future PR adding a `Double`/`Float` money field or a ledger-entry mutation method
 * would fail one of these tests immediately instead of surviving until someone
 * happens to notice.
 */
class MoneyArithmeticTest {
    private val moneyBearingClasses: List<KClass<*>> =
        listOf(
            LedgerEntry::class,
            FeeBalance::class,
            FeeAdjustment::class,
            FeePayment::class,
            Order::class,
            OrderItem::class,
            Contribution::class,
            Sponsorship::class,
            Campaign::class,
            ProductVariant::class,
        )

    @Test
    fun `no money domain class stores an amount as Double or Float`() {
        val offenders =
            moneyBearingClasses.flatMap { klass ->
                klass.memberProperties
                    .filter { it.returnType.classifier == Double::class || it.returnType.classifier == Float::class }
                    .map { "${klass.simpleName}.${it.name}" }
            }

        assertTrue(offenders.isEmpty(), "Money domain classes must use Long (minor-unit cents), never floating point: $offenders")
    }

    @Test
    fun `LedgerEntryRepository exposes no method that mutates an existing entry's core fields`() {
        // Append-only by construction (LedgerEntry's own doc comment): corrections are always a
        // new reversing row. markIncludedInTransfer is the one allowed exception — it only ever
        // sets the payout-eligibility linking column, never amount/direction/type.
        val allowedMutators = setOf("markIncludedInTransfer")
        val mutatingMethodNames =
            LedgerEntryRepository::class
                .members
                .filter { member -> Regex("update|delete|modify", RegexOption.IGNORE_CASE).containsMatchIn(member.name) }
                .map { it.name }
                .filterNot { it in allowedMutators }

        assertTrue(mutatingMethodNames.isEmpty(), "LedgerEntryRepository must stay append-only, found: $mutatingMethodNames")
    }
}
