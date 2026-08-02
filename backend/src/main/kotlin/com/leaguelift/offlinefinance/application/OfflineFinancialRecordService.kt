package com.leaguelift.offlinefinance.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.ConflictException
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.finance.domain.PaymentSource
import com.leaguelift.fundraising.domain.CampaignStatus
import com.leaguelift.fundraising.domain.ContributionLimits
import com.leaguelift.fundraising.domain.ContributionStatus
import com.leaguelift.fundraising.persistence.CampaignRepository
import com.leaguelift.fundraising.persistence.ContributionRepository
import com.leaguelift.ledger.application.LedgerService
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.offlinefinance.domain.OfflineFinancialRecord
import com.leaguelift.offlinefinance.domain.OfflineFinancialRecordType
import com.leaguelift.offlinefinance.domain.OfflinePaymentMethod
import com.leaguelift.offlinefinance.domain.OfflineVerificationStatus
import com.leaguelift.offlinefinance.persistence.OfflineFinancialRecordRepository
import com.leaguelift.order.domain.FulfillmentSource
import com.leaguelift.order.domain.FulfillmentStatus
import com.leaguelift.order.domain.OrderStatus
import com.leaguelift.order.domain.ShippingAddress
import com.leaguelift.order.persistence.FulfillmentHistoryRepository
import com.leaguelift.order.persistence.FulfillmentRepository
import com.leaguelift.order.persistence.OrderItemRepository
import com.leaguelift.order.persistence.OrderRepository
import com.leaguelift.outbox.application.OutboxWriter
import com.leaguelift.sponsorship.domain.SponsorshipStatus
import com.leaguelift.sponsorship.domain.effectiveMaxQuantity
import com.leaguelift.sponsorship.persistence.SponsorRepository
import com.leaguelift.sponsorship.persistence.SponsorshipPackageRepository
import com.leaguelift.sponsorship.persistence.SponsorshipRepository
import com.leaguelift.store.domain.CatalogSource
import com.leaguelift.store.domain.ProductStatus
import com.leaguelift.store.domain.StoreStatus
import com.leaguelift.store.persistence.ProductRepository
import com.leaguelift.store.persistence.ProductVariantRepository
import com.leaguelift.store.persistence.StoreRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.UUID

private val MAX_FUTURE_SKEW: Duration = Duration.ofMinutes(5)
private const val MAX_ORDER_LINES = 50
private const val MAX_ORDER_QUANTITY = 100

data class OfflineOrderLineItem(val productVariantId: UUID, val quantity: Int)

data class OfflineFinancialAcknowledgementPayload(
	val recipientEmail: String,
	val payerName: String?,
	val recordType: String,
	val displayLabel: String,
	val amountMinor: Long,
	val currency: String,
	val paymentMethod: String,
	val paymentReference: String?,
	val receivedAt: Instant,
)

@Service
class OfflineFinancialRecordService(
	private val repository: OfflineFinancialRecordRepository,
	private val campaignRepository: CampaignRepository,
	private val contributionRepository: ContributionRepository,
	private val sponsorshipPackageRepository: SponsorshipPackageRepository,
	private val sponsorRepository: SponsorRepository,
	private val sponsorshipRepository: SponsorshipRepository,
	private val storeRepository: StoreRepository,
	private val productRepository: ProductRepository,
	private val productVariantRepository: ProductVariantRepository,
	private val orderRepository: OrderRepository,
	private val orderItemRepository: OrderItemRepository,
	private val fulfillmentRepository: FulfillmentRepository,
	private val fulfillmentHistoryRepository: FulfillmentHistoryRepository,
	private val membershipService: MembershipService,
	private val ledgerService: LedgerService,
	private val auditService: AuditService,
	private val outboxWriter: OutboxWriter,
	private val objectMapper: ObjectMapper,
	private val clock: Clock,
) {
	fun list(
		organizationId: UUID,
		verificationStatus: OfflineVerificationStatus?,
		recordType: OfflineFinancialRecordType?,
		offset: Int,
		limit: Int,
		currentUser: CurrentUser,
	): List<OfflineFinancialRecord> {
		membershipService.requireManagerRole(organizationId, currentUser)
		return repository.list(organizationId, verificationStatus, recordType, offset, limit.coerceIn(1, 100))
	}

	fun count(
		organizationId: UUID,
		verificationStatus: OfflineVerificationStatus?,
		recordType: OfflineFinancialRecordType?,
		currentUser: CurrentUser,
	): Long {
		membershipService.requireManagerRole(organizationId, currentUser)
		return repository.count(organizationId, verificationStatus, recordType)
	}

	fun get(organizationId: UUID, recordId: UUID, currentUser: CurrentUser): OfflineFinancialRecord {
		membershipService.requireManagerRole(organizationId, currentUser)
		return repository.findById(recordId, organizationId)
			?: throw NotFoundException("OFFLINE_FINANCIAL_RECORD_NOT_FOUND", "The offline financial record could not be found.")
	}

	@Transactional
	fun createContribution(
		organizationId: UUID,
		campaignId: UUID,
		amountMinor: Long,
		supporterName: String?,
		isAnonymous: Boolean,
		supporterEmail: String?,
		paymentMethod: OfflinePaymentMethod,
		paymentReference: String?,
		receivedAt: Instant,
		internalNotes: String?,
		idempotencyKey: String,
		markVerified: Boolean,
		sendAcknowledgement: Boolean,
		currentUser: CurrentUser,
	): OfflineFinancialRecord {
		membershipService.requireManagerRole(organizationId, currentUser)
		validateReceivedAt(receivedAt)
		if (!ContributionLimits.isAmountAllowed(amountMinor)) {
			throw ValidationException(
				"Contribution amount must be between ${ContributionLimits.MIN_AMOUNT_MINOR} and ${ContributionLimits.MAX_AMOUNT_MINOR} minor units.",
			)
		}
		val campaign = campaignRepository.findById(campaignId, organizationId)
			?: throw NotFoundException("CAMPAIGN_NOT_FOUND", "The campaign could not be found.")
		if (campaign.status == CampaignStatus.ARCHIVED) throw ValidationException("Archived campaigns cannot receive new offline records.")
		val payerName = if (isAnonymous) null else clean(supporterName, 120)
		val payerEmail = clean(supporterEmail, 254)?.lowercase(Locale.ROOT)
		validateAcknowledgement(sendAcknowledgement, payerEmail)
		val normalizedKey = normalizeIdempotencyKey(idempotencyKey)
		val reference = clean(paymentReference, 200)
		val fingerprint = fingerprint(
			organizationId, OfflineFinancialRecordType.CONTRIBUTION, campaign.id, amountMinor, campaign.currency,
			paymentMethod, reference, receivedAt, payerName, payerEmail,
		)
		findIdempotentOrDuplicate(organizationId, normalizedKey, fingerprint)?.let { return it }

		val contribution = contributionRepository.insertOfflinePending(
			organizationId, campaign.id, amountMinor, campaign.currency, payerName, isAnonymous, payerEmail,
		)
		val record = insertRecord(
			organizationId = organizationId,
			recordType = OfflineFinancialRecordType.CONTRIBUTION,
			recordId = contribution.id,
			displayLabel = campaign.name,
			paymentMethod = paymentMethod,
			amountMinor = amountMinor,
			currency = campaign.currency,
			payerName = payerName,
			payerEmail = payerEmail,
			paymentReference = reference,
			receivedAt = receivedAt,
			internalNotes = clean(internalNotes, 4000),
			idempotencyKey = normalizedKey,
			fingerprint = fingerprint,
			sendAcknowledgement = sendAcknowledgement,
			currentUser = currentUser,
		)
		return if (markVerified) verifyInternal(record, currentUser) else record
	}

	@Transactional
	fun createSponsorship(
		organizationId: UUID,
		packageId: UUID,
		sponsorName: String,
		sponsorContactEmail: String?,
		sponsorPhone: String?,
		sponsorCompanyName: String?,
		paymentMethod: OfflinePaymentMethod,
		paymentReference: String?,
		receivedAt: Instant,
		internalNotes: String?,
		idempotencyKey: String,
		markVerified: Boolean,
		sendAcknowledgement: Boolean,
		currentUser: CurrentUser,
	): OfflineFinancialRecord {
		membershipService.requireManagerRole(organizationId, currentUser)
		validateReceivedAt(receivedAt)
		val sponsorshipPackage = sponsorshipPackageRepository.findById(packageId, organizationId)
			?: throw NotFoundException("SPONSORSHIP_PACKAGE_NOT_FOUND", "The sponsorship package could not be found.")
		if (sponsorshipPackage.priceMinor <= 0) throw ValidationException("A zero-value sponsorship is not an offline financial record.")
		val name = clean(sponsorName, 120) ?: throw ValidationException("Sponsor name is required.")
		val email = clean(sponsorContactEmail, 254)?.lowercase(Locale.ROOT)
		validateAcknowledgement(sendAcknowledgement, email)
		val reference = clean(paymentReference, 200)
		val normalizedKey = normalizeIdempotencyKey(idempotencyKey)
		val fingerprint = fingerprint(
			organizationId, OfflineFinancialRecordType.SPONSORSHIP, sponsorshipPackage.id,
			sponsorshipPackage.priceMinor, sponsorshipPackage.currency, paymentMethod, reference, receivedAt, name, email,
		)
		findIdempotentOrDuplicate(organizationId, normalizedKey, fingerprint)?.let { return it }
		ensureSponsorshipCapacity(sponsorshipPackage.id, sponsorshipPackage.effectiveMaxQuantity())

		val sponsor = sponsorRepository.insert(
			organizationId = organizationId,
			name = name,
			contactEmail = email,
			phone = clean(sponsorPhone, 40),
			companyName = clean(sponsorCompanyName, 200),
			notes = clean(internalNotes, 2000),
		)
		val sponsorship = sponsorshipRepository.insertOfflinePending(
			organizationId, sponsorshipPackage.id, sponsor.id, sponsorshipPackage.priceMinor, sponsorshipPackage.currency,
		)
		val record = insertRecord(
			organizationId = organizationId,
			recordType = OfflineFinancialRecordType.SPONSORSHIP,
			recordId = sponsorship.id,
			displayLabel = sponsorshipPackage.name,
			paymentMethod = paymentMethod,
			amountMinor = sponsorshipPackage.priceMinor,
			currency = sponsorshipPackage.currency,
			payerName = name,
			payerEmail = email,
			paymentReference = reference,
			receivedAt = receivedAt,
			internalNotes = clean(internalNotes, 4000),
			idempotencyKey = normalizedKey,
			fingerprint = fingerprint,
			sendAcknowledgement = sendAcknowledgement,
			currentUser = currentUser,
		)
		return if (markVerified) verifyInternal(record, currentUser) else record
	}

	@Transactional
	fun createOrder(
		organizationId: UUID,
		storeId: UUID,
		items: List<OfflineOrderLineItem>,
		supporterName: String?,
		supporterEmail: String?,
		shippingAddress: ShippingAddress?,
		paymentMethod: OfflinePaymentMethod,
		paymentReference: String?,
		receivedAt: Instant,
		internalNotes: String?,
		idempotencyKey: String,
		markVerified: Boolean,
		sendAcknowledgement: Boolean,
		currentUser: CurrentUser,
	): OfflineFinancialRecord {
		membershipService.requireManagerRole(organizationId, currentUser)
		validateReceivedAt(receivedAt)
		if (items.isEmpty() || items.size > MAX_ORDER_LINES) throw ValidationException("An offline order must contain 1 to $MAX_ORDER_LINES line items.")
		if (items.any { it.quantity !in 1..MAX_ORDER_QUANTITY }) throw ValidationException("Each order quantity must be between 1 and $MAX_ORDER_QUANTITY.")
		val store = storeRepository.findById(storeId, organizationId)
			?: throw NotFoundException("STORE_NOT_FOUND", "The store could not be found.")
		if (store.status != StoreStatus.ACTIVE) throw ValidationException("Only an active store can receive a new offline order.")

		val resolved = items.map { line ->
			val variant = productVariantRepository.findById(line.productVariantId, organizationId)
				?: throw NotFoundException("PRODUCT_VARIANT_NOT_FOUND", "A selected product variant could not be found.")
			val product = productRepository.findById(variant.productId, organizationId)
				?: throw NotFoundException("PRODUCT_NOT_FOUND", "A selected product could not be found.")
			if (product.storeId != store.id || product.status != ProductStatus.ACTIVE || !variant.isActive) {
				throw ValidationException("Every offline order item must be an active product and variant in the selected store.")
			}
			if (product.catalogSource != CatalogSource.MANUAL || variant.catalogSource != CatalogSource.MANUAL) {
				throw ValidationException("Offline orders can only use manually fulfilled product variants.")
			}
			Triple(product, variant, line.quantity)
		}
		val currency = resolved.first().second.currency
		if (resolved.any { it.second.currency != currency }) throw ValidationException("All order items must use the same currency.")
		val vendorIds = resolved.map { it.first.manualVendorId }.toSet()
		if (vendorIds.any { it == null }) {
			throw ValidationException("Every offline order product must have a manual vendor.")
		}
		if (vendorIds.size != 1) {
			throw ValidationException("An offline order cannot span multiple manual vendors.")
		}
		val gross = resolved.sumOf { (_, variant, quantity) -> variant.priceMinor * quantity }
		if (gross <= 0) throw ValidationException("An offline financial order must have a positive total.")
		val name = clean(supporterName, 120)
		val email = clean(supporterEmail, 254)?.lowercase(Locale.ROOT)
		validateAcknowledgement(sendAcknowledgement, email)
		val reference = clean(paymentReference, 200)
		val normalizedKey = normalizeIdempotencyKey(idempotencyKey)
		val lineFingerprint = resolved
			.sortedBy { it.second.id.toString() }
			.joinToString("|") { (_, variant, quantity) -> "${variant.id}:$quantity:${variant.priceMinor}:${variant.costMinor}" }
		val fingerprint = fingerprint(
			organizationId, OfflineFinancialRecordType.ORDER, store.id, gross, currency,
			paymentMethod, reference, receivedAt, name, email, lineFingerprint,
		)
		findIdempotentOrDuplicate(organizationId, normalizedKey, fingerprint)?.let { return it }

		val order = orderRepository.insertOfflinePending(organizationId, store.id, currency, name, email, shippingAddress)
		resolved.forEach { (_, variant, quantity) ->
			orderItemRepository.insert(order.id, variant.id, quantity, variant.priceMinor, variant.costMinor)
		}
		val record = insertRecord(
			organizationId = organizationId,
			recordType = OfflineFinancialRecordType.ORDER,
			recordId = order.id,
			displayLabel = store.name,
			paymentMethod = paymentMethod,
			amountMinor = gross,
			currency = currency,
			payerName = name,
			payerEmail = email,
			paymentReference = reference,
			receivedAt = receivedAt,
			internalNotes = clean(internalNotes, 4000),
			idempotencyKey = normalizedKey,
			fingerprint = fingerprint,
			sendAcknowledgement = sendAcknowledgement,
			currentUser = currentUser,
		)
		return if (markVerified) verifyInternal(record, currentUser) else record
	}

	@Transactional
	fun verify(organizationId: UUID, recordId: UUID, currentUser: CurrentUser): OfflineFinancialRecord {
		membershipService.requireManagerRole(organizationId, currentUser)
		val record = repository.findByIdForUpdate(recordId, organizationId)
			?: throw NotFoundException("OFFLINE_FINANCIAL_RECORD_NOT_FOUND", "The offline financial record could not be found.")
		return verifyInternal(record, currentUser)
	}

	private fun verifyInternal(record: OfflineFinancialRecord, currentUser: CurrentUser): OfflineFinancialRecord {
		if (record.verificationStatus == OfflineVerificationStatus.VERIFIED) return record
		when (record.recordType) {
			OfflineFinancialRecordType.CONTRIBUTION -> {
				val contribution = contributionRepository.findById(record.recordId)
					?: throw NotFoundException("CONTRIBUTION_NOT_FOUND", "The contribution could not be found.")
				if (contribution.organizationId != record.organizationId || contribution.paymentSource != PaymentSource.OFFLINE) {
					throw ValidationException("This contribution is not an offline record for the selected organization.")
				}
				if (contribution.status != ContributionStatus.PENDING) throw ValidationException("Only a pending offline contribution can be verified.")
				if (contributionRepository.markOfflineConfirmed(contribution.id, record.receivedAt) != 1) {
					throw ConflictException("OFFLINE_RECORD_STATE_CHANGED", "The contribution changed before it could be verified.")
				}
				ledgerService.recordOfflineContribution(
					contribution.copy(status = ContributionStatus.CONFIRMED, confirmedAt = record.receivedAt),
					record.paymentReference,
				)
			}
			OfflineFinancialRecordType.SPONSORSHIP -> {
				val sponsorship = sponsorshipRepository.findById(record.recordId)
					?: throw NotFoundException("SPONSORSHIP_NOT_FOUND", "The sponsorship could not be found.")
				if (sponsorship.organizationId != record.organizationId || sponsorship.paymentSource != PaymentSource.OFFLINE) {
					throw ValidationException("This sponsorship is not an offline record for the selected organization.")
				}
				if (sponsorship.status != SponsorshipStatus.PENDING) throw ValidationException("Only a pending offline sponsorship can be verified.")
				val sponsorshipPackage = sponsorshipPackageRepository.findById(sponsorship.packageId, record.organizationId)
					?: throw NotFoundException("SPONSORSHIP_PACKAGE_NOT_FOUND", "The sponsorship package could not be found.")
				ensureSponsorshipCapacity(sponsorshipPackage.id, sponsorshipPackage.effectiveMaxQuantity())
				if (sponsorshipRepository.markOfflineConfirmed(sponsorship.id, record.receivedAt) != 1) {
					throw ConflictException("OFFLINE_RECORD_STATE_CHANGED", "The sponsorship changed before it could be verified.")
				}
				ledgerService.recordOfflineSponsorship(
					sponsorship.copy(status = SponsorshipStatus.CONFIRMED, confirmedAt = record.receivedAt),
					record.paymentReference,
				)
			}
			OfflineFinancialRecordType.ORDER -> {
				val order = orderRepository.findById(record.recordId, record.organizationId)
					?: throw NotFoundException("ORDER_NOT_FOUND", "The order could not be found.")
				if (order.paymentSource != PaymentSource.OFFLINE) throw ValidationException("This order is not an offline record.")
				if (order.status != OrderStatus.PENDING) throw ValidationException("Only a pending offline order can be verified.")
				if (orderRepository.markOfflineConfirmed(order.id, record.receivedAt) != 1) {
					throw ConflictException("OFFLINE_RECORD_STATE_CHANGED", "The order changed before it could be verified.")
				}
				val items = orderItemRepository.findByOrder(order.id)
				ledgerService.recordOfflineOrder(
					order.copy(status = OrderStatus.CONFIRMED, confirmedAt = record.receivedAt), items, record.paymentReference,
				)
				if (fulfillmentRepository.findByOrder(order.id) == null) {
					val vendorIds = items.map { item ->
						val variant = productVariantRepository.findById(item.productVariantId, order.organizationId)
							?: error("order_item ${item.id} references a missing product_variant")
						val product = productRepository.findById(variant.productId, order.organizationId)
							?: error("product_variant ${variant.id} references a missing product")
						product.manualVendorId
					}.distinct()
					val fulfillment = fulfillmentRepository.insert(
						orderId = order.id,
						source = FulfillmentSource.MANUAL,
						status = FulfillmentStatus.READY,
						printifyOrderId = null,
						manualVendorId = vendorIds.singleOrNull(),
						lastError = null,
					)
					fulfillmentHistoryRepository.insert(
						order.organizationId, fulfillment.id, null, FulfillmentStatus.READY,
						"Manual fulfillment created when the offline order was verified.", currentUser.userId,
					)
				}
			}
		}

		val now = Instant.now(clock)
		if (repository.markVerified(record.id, record.organizationId, currentUser.userId, now) != 1) {
			throw ConflictException("OFFLINE_RECORD_STATE_CHANGED", "The offline record changed before it could be verified.")
		}
		auditService.record(
			currentUser.userId, record.organizationId, "offline_financial_record.verified", "offline_financial_record", record.id,
			objectMapper.writeValueAsString(mapOf("recordType" to record.recordType.name, "recordId" to record.recordId.toString())),
		)
		if (record.sendAcknowledgement && record.payerEmail != null) {
			outboxWriter.write(
				aggregateType = "offline_financial_record",
				aggregateId = record.id,
				organizationId = record.organizationId,
				eventType = "offline.financial.verified",
				payloadJson = objectMapper.writeValueAsString(
					OfflineFinancialAcknowledgementPayload(
						recipientEmail = record.payerEmail,
						payerName = record.payerName,
						recordType = record.recordType.name,
						displayLabel = record.displayLabel,
						amountMinor = record.amountMinor,
						currency = record.currency,
						paymentMethod = record.paymentMethod.name,
						paymentReference = record.paymentReference,
						receivedAt = record.receivedAt,
					),
				),
			)
		}
		return repository.findById(record.id, record.organizationId)!!
	}

	private fun insertRecord(
		organizationId: UUID,
		recordType: OfflineFinancialRecordType,
		recordId: UUID,
		displayLabel: String,
		paymentMethod: OfflinePaymentMethod,
		amountMinor: Long,
		currency: String,
		payerName: String?,
		payerEmail: String?,
		paymentReference: String?,
		receivedAt: Instant,
		internalNotes: String?,
		idempotencyKey: String,
		fingerprint: String,
		sendAcknowledgement: Boolean,
		currentUser: CurrentUser,
	): OfflineFinancialRecord {
		val record = try {
			repository.insert(
				organizationId, recordType, recordId, displayLabel, paymentMethod, amountMinor, currency,
				payerName, payerEmail, paymentReference, receivedAt, internalNotes, idempotencyKey,
				fingerprint, sendAcknowledgement, currentUser.userId,
			)
		} catch (_: DataIntegrityViolationException) {
			throw ConflictException(
				"OFFLINE_FINANCIAL_RECORD_DUPLICATE",
				"This offline transaction appears to have already been recorded.",
			)
		}
		auditService.record(
			currentUser.userId, organizationId, "offline_financial_record.created", "offline_financial_record", record.id,
			objectMapper.writeValueAsString(mapOf("recordType" to recordType.name, "recordId" to recordId.toString())),
		)
		return record
	}

	private fun findIdempotentOrDuplicate(organizationId: UUID, idempotencyKey: String, fingerprint: String): OfflineFinancialRecord? {
		val byKey = repository.findByIdempotencyKey(organizationId, idempotencyKey)
		if (byKey != null) {
			if (byKey.duplicateFingerprint != fingerprint) {
				throw ConflictException("IDEMPOTENCY_KEY_REUSED", "This idempotency key was already used for a different offline transaction.")
			}
			return byKey
		}
		if (repository.findByFingerprint(organizationId, fingerprint) != null) {
			throw ConflictException(
				"OFFLINE_FINANCIAL_RECORD_DUPLICATE",
				"A matching offline transaction is already recorded for this organization.",
			)
		}
		return null
	}

	private fun ensureSponsorshipCapacity(packageId: UUID, maxQuantity: Int?) {
		if (maxQuantity != null && sponsorshipRepository.countConfirmedForPackage(packageId) >= maxQuantity) {
			throw ValidationException("This sponsorship package is sold out.")
		}
	}

	private fun validateReceivedAt(receivedAt: Instant) {
		if (receivedAt.isAfter(Instant.now(clock).plus(MAX_FUTURE_SKEW))) {
			throw ValidationException("Received time cannot be in the future.")
		}
	}

	private fun validateAcknowledgement(sendAcknowledgement: Boolean, payerEmail: String?) {
		if (sendAcknowledgement && payerEmail == null) {
			throw ValidationException("A payer email is required to send an acknowledgement.")
		}
	}

	private fun normalizeIdempotencyKey(value: String): String {
		val normalized = value.trim().lowercase(Locale.ROOT)
		if (normalized.length !in 8..120) throw ValidationException("Idempotency key must be between 8 and 120 characters.")
		return normalized
	}

	private fun clean(value: String?, maxLength: Int): String? = value?.trim()?.takeIf { it.isNotEmpty() }?.let {
		if (it.length > maxLength) throw ValidationException("A supplied text value exceeds the $maxLength character limit.")
		it
	}

	private fun fingerprint(vararg parts: Any?): String {
		val canonical = parts.joinToString("\u001f") { value ->
			when (value) {
				null -> ""
				is Instant -> value.toString()
				else -> value.toString().trim().lowercase(Locale.ROOT)
			}
		}
		return MessageDigest.getInstance("SHA-256")
			.digest(canonical.toByteArray(StandardCharsets.UTF_8))
			.joinToString("") { "%02x".format(it) }
	}
}
