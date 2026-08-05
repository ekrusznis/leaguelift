package com.rally26.offlinefinance.domain

import java.time.Instant
import java.util.UUID

enum class OfflineFinancialRecordType { CONTRIBUTION, SPONSORSHIP, ORDER }

enum class OfflinePaymentMethod { CASH, CHECK, ACH, EXTERNAL_CARD, VENMO, ZELLE, OTHER }

enum class OfflineVerificationStatus { PENDING_VERIFICATION, VERIFIED, REVERSED }

data class OfflineFinancialRecord(
    val id: UUID,
    val organizationId: UUID,
    val recordType: OfflineFinancialRecordType,
    val recordId: UUID,
    val displayLabel: String,
    val paymentMethod: OfflinePaymentMethod,
    val verificationStatus: OfflineVerificationStatus,
    val amountMinor: Long,
    val currency: String,
    val payerName: String?,
    val payerEmail: String?,
    val paymentReference: String?,
    val receivedAt: Instant,
    val internalNotes: String?,
    val idempotencyKey: String,
    val duplicateFingerprint: String,
    val sendAcknowledgement: Boolean,
    val recordedByUserId: UUID,
    val verifiedByUserId: UUID?,
    val verifiedAt: Instant?,
    val reversedByUserId: UUID? = null,
    val reversedAt: Instant? = null,
    val reversalReason: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
