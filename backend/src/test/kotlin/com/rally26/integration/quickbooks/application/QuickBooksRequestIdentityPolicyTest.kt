package com.rally26.integration.quickbooks.application

import com.rally26.common.error.ValidationException
import com.rally26.integration.quickbooks.domain.QuickBooksProviderOperationKind
import com.rally26.integration.quickbooks.domain.QuickBooksReadbackStrategy
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class QuickBooksRequestIdentityPolicyTest {
    private val policy = QuickBooksRequestIdentityPolicy()
    private val organizationId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val connectionId = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun `same business operation and canonical payload produce the same request identity`() {
        val first = plan("{\"TotalAmt\":12500}")
        val second = plan("{\"TotalAmt\":12500}")

        assertEquals(first.identity, second.identity)
        assertEquals(64, first.identity.operationKey.length)
        assertEquals(64, first.identity.payloadHash.length)
        assertEquals(50, first.identity.intuitRequestId.length)
        assertFalse(first.providerWritesEnabled)
    }

    @Test
    fun `payload changes keep business operation key but create a new Intuit request id`() {
        val first = plan("{\"TotalAmt\":12500}")
        val corrected = plan("{\"TotalAmt\":13000}")

        assertEquals(first.identity.operationKey, corrected.identity.operationKey)
        assertNotEquals(first.identity.payloadHash, corrected.identity.payloadHash)
        assertNotEquals(first.identity.intuitRequestId, corrected.identity.intuitRequestId)
    }

    @Test
    fun `create plans query by stable reference while updates read the provider entity`() {
        val create = plan("{\"TotalAmt\":12500}", QuickBooksProviderOperationKind.CREATE)
        val update = plan("{\"Id\":\"123\",\"SyncToken\":\"4\"}", QuickBooksProviderOperationKind.UPDATE)

        assertEquals(QuickBooksReadbackStrategy.QUERY_BY_STABLE_REFERENCE, create.readbackStrategy)
        assertEquals(QuickBooksReadbackStrategy.READ_BY_ENTITY_ID, update.readbackStrategy)
    }

    @Test
    fun `blank accounting identity fields fail closed`() {
        assertFailsWith<ValidationException> {
            policy.plan(
                organizationId,
                connectionId,
                "realm-1",
                " ",
                "source-1",
                QuickBooksProviderOperationKind.CREATE,
                "Invoice",
                "{}",
            )
        }
    }

    private fun plan(
        payload: String,
        operationKind: QuickBooksProviderOperationKind = QuickBooksProviderOperationKind.CREATE,
    ) = policy.plan(
        organizationId,
        connectionId,
        "realm-123",
        "PROGRAM_FEE_PAYMENT",
        "33333333-3333-3333-3333-333333333333",
        operationKind,
        "Invoice",
        payload,
        "R26-FEE-33333333",
    )
}
