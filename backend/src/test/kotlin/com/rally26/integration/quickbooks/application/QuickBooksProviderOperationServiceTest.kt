package com.rally26.integration.quickbooks.application

import com.rally26.common.error.ValidationException
import com.rally26.integration.quickbooks.domain.QuickBooksExportItem
import com.rally26.integration.quickbooks.domain.QuickBooksProviderOperationKind
import com.rally26.integration.quickbooks.domain.QuickBooksProviderOperationStatus
import com.rally26.integration.quickbooks.domain.QuickBooksRetryDisposition
import com.rally26.integration.quickbooks.persistence.QuickBooksRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class QuickBooksProviderOperationServiceTest {
    private val repository = mockk<QuickBooksRepository>()
    private val identityPolicy = QuickBooksRequestIdentityPolicy()
    private val service = QuickBooksProviderOperationService(repository, identityPolicy, QuickBooksFailurePolicy())
    private val batchId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val connectionId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()

    @Test
    fun `new provider operation persists write-disabled identity without executing provider`() {
        every { repository.findExportItemBySource(batchId, "PROGRAM_FEE_PAYMENT", sourceId) } returns null
        val expected = exportItem("{\"TotalAmt\":12500}")
        every { repository.insertPlannedExportItem(batchId, sourceId, any()) } returns expected

        val result = plan("{\"TotalAmt\":12500}")

        assertSame(expected, result)
        assertFalse(service.providerWritesEnabled())
        verify(exactly = 1) { repository.insertPlannedExportItem(batchId, sourceId, any()) }
    }

    @Test
    fun `same source cannot silently reuse idempotency slot with changed payload`() {
        every { repository.findExportItemBySource(batchId, "PROGRAM_FEE_PAYMENT", sourceId) } returns
            exportItem("{\"TotalAmt\":12500}")

        assertFailsWith<ValidationException> {
            plan("{\"TotalAmt\":13000}")
        }
        verify(exactly = 0) { repository.insertPlannedExportItem(any(), any(), any()) }
    }

    private fun plan(payload: String) =
        service.planWrite(
            batchId,
            organizationId,
            connectionId,
            "realm-123",
            "PROGRAM_FEE_PAYMENT",
            sourceId,
            QuickBooksProviderOperationKind.CREATE,
            "Invoice",
            payload,
            "R26-FEE-$sourceId",
        )

    private fun exportItem(payload: String): QuickBooksExportItem {
        val plan =
            identityPolicy.plan(
                organizationId,
                connectionId,
                "realm-123",
                "PROGRAM_FEE_PAYMENT",
                sourceId.toString(),
                QuickBooksProviderOperationKind.CREATE,
                "Invoice",
                payload,
                "R26-FEE-$sourceId",
            )
        val now = Instant.parse("2026-08-09T19:55:00Z")
        return QuickBooksExportItem(
            id = UUID.randomUUID(),
            batchId = batchId,
            sourceType = plan.sourceType,
            sourceId = sourceId,
            externalTransactionId = null,
            status = QuickBooksProviderOperationStatus.WRITE_DISABLED,
            payloadHash = plan.identity.payloadHash,
            providerEntityType = plan.providerEntityType,
            operationKind = plan.operationKind,
            operationKey = plan.identity.operationKey,
            intuitRequestId = plan.identity.intuitRequestId,
            attemptCount = 0,
            lastHttpStatus = null,
            lastFaultType = null,
            lastFaultCode = null,
            lastIntuitTid = null,
            retryDisposition = QuickBooksRetryDisposition.DO_NOT_RETRY,
            retryNotBefore = null,
            lastAttemptAt = null,
            errorCode = null,
            errorMessage = null,
            createdAt = now,
            updatedAt = now,
        )
    }
}
