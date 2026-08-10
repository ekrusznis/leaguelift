package com.rally26.integration.quickbooks.application

import com.rally26.common.error.ValidationException
import com.rally26.integration.quickbooks.contract.QuickBooksFaultResponse
import com.rally26.integration.quickbooks.domain.QuickBooksExportItem
import com.rally26.integration.quickbooks.domain.QuickBooksProviderFailure
import com.rally26.integration.quickbooks.domain.QuickBooksProviderOperationKind
import com.rally26.integration.quickbooks.domain.QuickBooksProviderRequestPlan
import com.rally26.integration.quickbooks.domain.QuickBooksRetryDecision
import com.rally26.integration.quickbooks.domain.QuickBooksTransportFailureKind
import com.rally26.integration.quickbooks.persistence.QuickBooksRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Durable planning seam for later QuickBooks writes. Planning is allowed; execution is not.
 * Existing operation identity is immutable: a caller cannot reuse the same batch/source slot
 * with a changed payload or provider request ID.
 */
@Service
class QuickBooksProviderOperationService(
    private val repository: QuickBooksRepository,
    private val requestIdentityPolicy: QuickBooksRequestIdentityPolicy,
    private val failurePolicy: QuickBooksFailurePolicy,
) {
    @Transactional
    fun planWrite(
        batchId: UUID,
        organizationId: UUID,
        connectionId: UUID,
        realmId: String,
        sourceType: String,
        sourceId: UUID,
        operationKind: QuickBooksProviderOperationKind,
        providerEntityType: String,
        canonicalPayload: String,
        readbackReference: String? = null,
    ): QuickBooksExportItem {
        val plan =
            requestIdentityPolicy.plan(
                organizationId,
                connectionId,
                realmId,
                sourceType,
                sourceId.toString(),
                operationKind,
                providerEntityType,
                canonicalPayload,
                readbackReference,
            )
        val existing = repository.findExportItemBySource(batchId, plan.sourceType, sourceId)
        if (existing != null) {
            ensureSameOperation(existing, plan)
            return existing
        }
        return repository.insertPlannedExportItem(batchId, sourceId, plan)
    }

    fun classifyFailure(
        operationKind: QuickBooksProviderOperationKind,
        httpStatus: Int?,
        faultResponse: QuickBooksFaultResponse?,
        intuitTid: String? = null,
        transportFailure: QuickBooksTransportFailureKind? = null,
    ): Pair<QuickBooksProviderFailure, QuickBooksRetryDecision> {
        val failure = failurePolicy.classify(httpStatus, faultResponse, intuitTid, transportFailure)
        return failure to failurePolicy.retryDecision(operationKind, failure)
    }

    fun providerWritesEnabled(): Boolean = false

    private fun ensureSameOperation(
        existing: QuickBooksExportItem,
        plan: QuickBooksProviderRequestPlan,
    ) {
        val same =
            existing.payloadHash == plan.identity.payloadHash &&
                existing.operationKey == plan.identity.operationKey &&
                existing.intuitRequestId == plan.identity.intuitRequestId &&
                existing.operationKind == plan.operationKind &&
                existing.providerEntityType == plan.providerEntityType
        if (!same) {
            throw ValidationException(
                "A QuickBooks provider operation is already planned for this Rally26 source with different " +
                    "request identity. Create a new reviewed operation instead of reusing an idempotency slot.",
            )
        }
    }
}
