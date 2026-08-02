package com.leaguelift.offlinefinance.persistence

import com.leaguelift.offlinefinance.domain.OfflineFinancialRecord
import com.leaguelift.offlinefinance.domain.OfflineFinancialRecordType
import com.leaguelift.offlinefinance.domain.OfflinePaymentMethod
import com.leaguelift.offlinefinance.domain.OfflineVerificationStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS = """
    id, organization_id, record_type, record_id, display_label, payment_method,
    verification_status, amount_minor, currency, payer_name, payer_email,
    payment_reference, received_at, internal_notes, idempotency_key,
    duplicate_fingerprint, send_acknowledgement, recorded_by_user_id,
    verified_by_user_id, verified_at, created_at, updated_at
"""

@Repository
class OfflineFinancialRecordRepository(private val jdbcClient: JdbcClient) {
	fun findById(id: UUID, organizationId: UUID): OfflineFinancialRecord? =
		jdbcClient.sql("select $COLUMNS from offline_financial_record where id = :id and organization_id = :organizationId")
			.param("id", id)
			.param("organizationId", organizationId)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun findByIdForUpdate(id: UUID, organizationId: UUID): OfflineFinancialRecord? =
		jdbcClient.sql("select $COLUMNS from offline_financial_record where id = :id and organization_id = :organizationId for update")
			.param("id", id)
			.param("organizationId", organizationId)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun findByIdempotencyKey(organizationId: UUID, idempotencyKey: String): OfflineFinancialRecord? =
		jdbcClient.sql("select $COLUMNS from offline_financial_record where organization_id = :organizationId and idempotency_key = :idempotencyKey")
			.param("organizationId", organizationId)
			.param("idempotencyKey", idempotencyKey)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun findByFingerprint(organizationId: UUID, fingerprint: String): OfflineFinancialRecord? =
		jdbcClient.sql("select $COLUMNS from offline_financial_record where organization_id = :organizationId and duplicate_fingerprint = :fingerprint")
			.param("organizationId", organizationId)
			.param("fingerprint", fingerprint)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun list(
		organizationId: UUID,
		verificationStatus: OfflineVerificationStatus?,
		recordType: OfflineFinancialRecordType?,
		offset: Int,
		limit: Int,
	): List<OfflineFinancialRecord> {
		val sql = buildString {
			append("select $COLUMNS from offline_financial_record where organization_id = :organizationId")
			if (verificationStatus != null) append(" and verification_status = :verificationStatus")
			if (recordType != null) append(" and record_type = :recordType")
			append(" order by received_at desc, created_at desc offset :offset limit :limit")
		}
		var statement = jdbcClient.sql(sql)
			.param("organizationId", organizationId)
			.param("offset", offset)
			.param("limit", limit)
		if (verificationStatus != null) statement = statement.param("verificationStatus", verificationStatus.name)
		if (recordType != null) statement = statement.param("recordType", recordType.name)
		return statement.query(::mapRow).list()
	}

	fun count(
		organizationId: UUID,
		verificationStatus: OfflineVerificationStatus?,
		recordType: OfflineFinancialRecordType?,
	): Long {
		val sql = buildString {
			append("select count(*) from offline_financial_record where organization_id = :organizationId")
			if (verificationStatus != null) append(" and verification_status = :verificationStatus")
			if (recordType != null) append(" and record_type = :recordType")
		}
		var statement = jdbcClient.sql(sql).param("organizationId", organizationId)
		if (verificationStatus != null) statement = statement.param("verificationStatus", verificationStatus.name)
		if (recordType != null) statement = statement.param("recordType", recordType.name)
		return statement.query(Long::class.java).single()
	}

	fun countPending(organizationId: UUID): Long = jdbcClient.sql(
		"select count(*) from offline_financial_record where organization_id = :organizationId and verification_status = 'PENDING_VERIFICATION'",
	)
		.param("organizationId", organizationId)
		.query(Long::class.java)
		.single()

	fun insert(
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
		duplicateFingerprint: String,
		sendAcknowledgement: Boolean,
		recordedByUserId: UUID,
	): OfflineFinancialRecord {
		val id = UUID.randomUUID()
		val now = Instant.now()
		jdbcClient.sql(
			"""
			insert into offline_financial_record
				(id, organization_id, record_type, record_id, display_label, payment_method,
				 verification_status, amount_minor, currency, payer_name, payer_email,
				 payment_reference, received_at, internal_notes, idempotency_key,
				 duplicate_fingerprint, send_acknowledgement, recorded_by_user_id,
				 created_at, updated_at)
			values
				(:id, :organizationId, :recordType, :recordId, :displayLabel, :paymentMethod,
				 'PENDING_VERIFICATION', :amountMinor, :currency, :payerName, :payerEmail,
				 :paymentReference, :receivedAt, :internalNotes, :idempotencyKey,
				 :duplicateFingerprint, :sendAcknowledgement, :recordedByUserId,
				 :now, :now)
			""".trimIndent(),
		)
			.param("id", id)
			.param("organizationId", organizationId)
			.param("recordType", recordType.name)
			.param("recordId", recordId)
			.param("displayLabel", displayLabel)
			.param("paymentMethod", paymentMethod.name)
			.param("amountMinor", amountMinor)
			.param("currency", currency)
			.param("payerName", payerName)
			.param("payerEmail", payerEmail)
			.param("paymentReference", paymentReference)
			.param("receivedAt", Timestamp.from(receivedAt))
			.param("internalNotes", internalNotes)
			.param("idempotencyKey", idempotencyKey)
			.param("duplicateFingerprint", duplicateFingerprint)
			.param("sendAcknowledgement", sendAcknowledgement)
			.param("recordedByUserId", recordedByUserId)
			.param("now", Timestamp.from(now))
			.update()
		return findById(id, organizationId)!!
	}

	fun markVerified(id: UUID, organizationId: UUID, verifiedByUserId: UUID, verifiedAt: Instant): Int = jdbcClient.sql(
		"""
		update offline_financial_record
		set verification_status = 'VERIFIED', verified_by_user_id = :verifiedByUserId,
		    verified_at = :verifiedAt, updated_at = :verifiedAt
		where id = :id and organization_id = :organizationId
		  and verification_status = 'PENDING_VERIFICATION'
		""".trimIndent(),
	)
		.param("verifiedByUserId", verifiedByUserId)
		.param("verifiedAt", Timestamp.from(verifiedAt))
		.param("id", id)
		.param("organizationId", organizationId)
		.update()

	private fun mapRow(rs: java.sql.ResultSet, _rowNum: Int): OfflineFinancialRecord = OfflineFinancialRecord(
		id = rs.getObject("id", UUID::class.java),
		organizationId = rs.getObject("organization_id", UUID::class.java),
		recordType = OfflineFinancialRecordType.valueOf(rs.getString("record_type")),
		recordId = rs.getObject("record_id", UUID::class.java),
		displayLabel = rs.getString("display_label"),
		paymentMethod = OfflinePaymentMethod.valueOf(rs.getString("payment_method")),
		verificationStatus = OfflineVerificationStatus.valueOf(rs.getString("verification_status")),
		amountMinor = rs.getLong("amount_minor"),
		currency = rs.getString("currency"),
		payerName = rs.getString("payer_name"),
		payerEmail = rs.getString("payer_email"),
		paymentReference = rs.getString("payment_reference"),
		receivedAt = rs.getTimestamp("received_at").toInstant(),
		internalNotes = rs.getString("internal_notes"),
		idempotencyKey = rs.getString("idempotency_key"),
		duplicateFingerprint = rs.getString("duplicate_fingerprint"),
		sendAcknowledgement = rs.getBoolean("send_acknowledgement"),
		recordedByUserId = rs.getObject("recorded_by_user_id", UUID::class.java),
		verifiedByUserId = rs.getObject("verified_by_user_id", UUID::class.java),
		verifiedAt = rs.getTimestamp("verified_at")?.toInstant(),
		createdAt = rs.getTimestamp("created_at").toInstant(),
		updatedAt = rs.getTimestamp("updated_at").toInstant(),
	)
}
