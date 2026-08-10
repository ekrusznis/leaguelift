package com.rally26.integration.quickbooks.application

import com.rally26.common.error.ServiceUnavailableException
import com.rally26.integration.quickbooks.domain.QuickBooksProviderOperationKind
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DisabledQuickBooksProviderWriteClientTest {
    private val identityPolicy = QuickBooksRequestIdentityPolicy()
    private val client = DisabledQuickBooksProviderWriteClient()

    @Test
    fun `phase 29 write transport always fails closed`() {
        val request =
            identityPolicy.plan(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "realm-123",
                "PROGRAM_FEE_PAYMENT",
                UUID.randomUUID().toString(),
                QuickBooksProviderOperationKind.CREATE,
                "Invoice",
                "{\"TotalAmt\":12500}",
                "R26-FEE-1",
            )

        assertFailsWith<ServiceUnavailableException> {
            client.executeWrite("not-used", "realm-123", request, "{\"TotalAmt\":12500}")
        }
        assertFailsWith<ServiceUnavailableException> {
            client.readBack("not-used", "realm-123", request)
        }
    }
}
