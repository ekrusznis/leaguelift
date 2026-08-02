package com.leaguelift.offlinefinance.web

import com.leaguelift.offlinefinance.application.OfflineOrderLineItem
import com.leaguelift.offlinefinance.domain.OfflineFinancialRecord
import com.leaguelift.offlinefinance.domain.OfflinePaymentMethod
import com.leaguelift.order.domain.ShippingAddress
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateOfflineContributionRequest(
	@field:NotNull val campaignId: UUID,
	@field:Positive val amountMinor: Long,
	@field:Size(max = 120) val supporterName: String? = null,
	val isAnonymous: Boolean = false,
	@field:Email @field:Size(max = 254) val supporterEmail: String? = null,
	@field:NotNull val paymentMethod: OfflinePaymentMethod,
	@field:Size(max = 200) val paymentReference: String? = null,
	@field:NotNull val receivedAt: Instant,
	@field:Size(max = 4000) val internalNotes: String? = null,
	@field:NotBlank @field:Size(min = 8, max = 120) val idempotencyKey: String,
	val markVerified: Boolean = false,
	val sendAcknowledgement: Boolean = false,
)

data class CreateOfflineSponsorshipRequest(
	@field:NotNull val packageId: UUID,
	@field:NotBlank @field:Size(max = 120) val sponsorName: String,
	@field:Email @field:Size(max = 254) val sponsorContactEmail: String? = null,
	@field:Size(max = 40) val sponsorPhone: String? = null,
	@field:Size(max = 200) val sponsorCompanyName: String? = null,
	@field:NotNull val paymentMethod: OfflinePaymentMethod,
	@field:Size(max = 200) val paymentReference: String? = null,
	@field:NotNull val receivedAt: Instant,
	@field:Size(max = 4000) val internalNotes: String? = null,
	@field:NotBlank @field:Size(min = 8, max = 120) val idempotencyKey: String,
	val markVerified: Boolean = false,
	val sendAcknowledgement: Boolean = false,
)

data class OfflineOrderLineItemRequest(
	@field:NotNull val productVariantId: UUID,
	@field:Min(1) @field:Max(100) val quantity: Int,
) {
	fun toDomain() = OfflineOrderLineItem(productVariantId, quantity)
}

data class OfflineShippingAddressRequest(
	@field:Size(max = 120) val name: String? = null,
	@field:Size(max = 200) val line1: String? = null,
	@field:Size(max = 200) val line2: String? = null,
	@field:Size(max = 120) val city: String? = null,
	@field:Size(max = 120) val state: String? = null,
	@field:Size(max = 40) val postalCode: String? = null,
	@field:Size(max = 2) val country: String? = null,
) {
	fun toDomain() = ShippingAddress(name, line1, line2, city, state, postalCode, country)
}

data class CreateOfflineOrderRequest(
	@field:NotNull val storeId: UUID,
	@field:NotEmpty @field:Size(max = 50) @field:Valid val items: List<OfflineOrderLineItemRequest>,
	@field:Size(max = 120) val supporterName: String? = null,
	@field:Email @field:Size(max = 254) val supporterEmail: String? = null,
	@field:Valid val shippingAddress: OfflineShippingAddressRequest? = null,
	@field:NotNull val paymentMethod: OfflinePaymentMethod,
	@field:Size(max = 200) val paymentReference: String? = null,
	@field:NotNull val receivedAt: Instant,
	@field:Size(max = 4000) val internalNotes: String? = null,
	@field:NotBlank @field:Size(min = 8, max = 120) val idempotencyKey: String,
	val markVerified: Boolean = false,
	val sendAcknowledgement: Boolean = false,
)

data class OfflineFinancialRecordResponse(
	val id: UUID,
	val organizationId: UUID,
	val recordType: String,
	val recordId: UUID,
	val displayLabel: String,
	val paymentMethod: String,
	val verificationStatus: String,
	val amountMinor: Long,
	val currency: String,
	val payerName: String?,
	val payerEmail: String?,
	val paymentReference: String?,
	val receivedAt: Instant,
	val internalNotes: String?,
	val sendAcknowledgement: Boolean,
	val recordedByUserId: UUID,
	val verifiedByUserId: UUID?,
	val verifiedAt: Instant?,
	val reversedByUserId: UUID?,
	val reversedAt: Instant?,
	val reversalReason: String?,
	val createdAt: Instant,
	val updatedAt: Instant,
)

fun OfflineFinancialRecord.toResponse() = OfflineFinancialRecordResponse(
	id = id,
	organizationId = organizationId,
	recordType = recordType.name,
	recordId = recordId,
	displayLabel = displayLabel,
	paymentMethod = paymentMethod.name,
	verificationStatus = verificationStatus.name,
	amountMinor = amountMinor,
	currency = currency,
	payerName = payerName,
	payerEmail = payerEmail,
	paymentReference = paymentReference,
	receivedAt = receivedAt,
	internalNotes = internalNotes,
	sendAcknowledgement = sendAcknowledgement,
	recordedByUserId = recordedByUserId,
	verifiedByUserId = verifiedByUserId,
	verifiedAt = verifiedAt,
	reversedByUserId = reversedByUserId,
	reversedAt = reversedAt,
	reversalReason = reversalReason,
	createdAt = createdAt,
	updatedAt = updatedAt,
)
