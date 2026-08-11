package com.rally26.eligibility.persistence

import com.rally26.eligibility.domain.AcceptanceMethod
import com.rally26.eligibility.domain.EligibilityEvidence
import com.rally26.eligibility.domain.EvidenceClassification
import com.rally26.eligibility.domain.EvidenceStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val EVIDENCE_COLUMNS =
    "id, organization_id, participant_id, requirement_id, classification, acceptance_method, signer_user_id, " +
        "signer_guardian_relationship, entered_legal_name, document_asset_id, provider, external_identifiers, " +
        "content_hash, status, superseded_by_evidence_id, accepted_at, expires_at, ip_address, user_agent, " +
        "reviewed_by_user_id, review_note, created_at, updated_at"

@Repository
class EligibilityEvidenceRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findById(
        id: UUID,
        organizationId: UUID,
    ): EligibilityEvidence? =
        jdbcClient
            .sql("select $EVIDENCE_COLUMNS from eligibility_evidence where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** ACTIVE evidence for one participant across every requirement — the input to clearance recomputation. */
    fun listActiveForParticipant(
        participantId: UUID,
        organizationId: UUID,
    ): List<EligibilityEvidence> =
        jdbcClient
            .sql(
                """
                select $EVIDENCE_COLUMNS from eligibility_evidence
                where participant_id = :participantId and organization_id = :organizationId and status = 'ACTIVE'
                """.trimIndent(),
            ).param("participantId", participantId)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .list()

    /** The single ACTIVE evidence row for one participant against one specific requirement version, if any. */
    fun findActiveForRequirement(
        participantId: UUID,
        requirementId: UUID,
        organizationId: UUID,
    ): EligibilityEvidence? =
        jdbcClient
            .sql(
                """
                select $EVIDENCE_COLUMNS from eligibility_evidence
                where participant_id = :participantId and requirement_id = :requirementId
                  and organization_id = :organizationId and status = 'ACTIVE'
                """.trimIndent(),
            ).param("participantId", participantId)
            .param("requirementId", requirementId)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun insert(
        organizationId: UUID,
        participantId: UUID,
        requirementId: UUID,
        classification: EvidenceClassification,
        acceptanceMethod: AcceptanceMethod,
        signerUserId: UUID?,
        signerGuardianRelationship: String?,
        enteredLegalName: String?,
        documentAssetId: UUID?,
        provider: String?,
        externalIdentifiersJson: String?,
        contentHash: String?,
        expiresAt: Instant?,
        ipAddress: String?,
        userAgent: String?,
        reviewedByUserId: UUID?,
        reviewNote: String?,
    ): EligibilityEvidence {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into eligibility_evidence (
                    id, organization_id, participant_id, requirement_id, classification, acceptance_method,
                    signer_user_id, signer_guardian_relationship, entered_legal_name, document_asset_id, provider,
                    external_identifiers, content_hash, status, accepted_at, expires_at, ip_address, user_agent,
                    reviewed_by_user_id, review_note, created_at, updated_at
                ) values (
                    :id, :organizationId, :participantId, :requirementId, :classification, :acceptanceMethod,
                    :signerUserId, :signerGuardianRelationship, :enteredLegalName, :documentAssetId, :provider,
                    cast(:externalIdentifiers as jsonb), :contentHash, 'ACTIVE', :now, :expiresAt, :ipAddress, :userAgent,
                    :reviewedByUserId, :reviewNote, :now, :now
                )
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("participantId", participantId)
            .param("requirementId", requirementId)
            .param("classification", classification.name)
            .param("acceptanceMethod", acceptanceMethod.name)
            .param("signerUserId", signerUserId)
            .param("signerGuardianRelationship", signerGuardianRelationship)
            .param("enteredLegalName", enteredLegalName)
            .param("documentAssetId", documentAssetId)
            .param("provider", provider)
            .param("externalIdentifiers", externalIdentifiersJson)
            .param("contentHash", contentHash)
            .param("now", Timestamp.from(now))
            .param("expiresAt", expiresAt?.let { Timestamp.from(it) })
            .param("ipAddress", ipAddress)
            .param("userAgent", userAgent)
            .param("reviewedByUserId", reviewedByUserId)
            .param("reviewNote", reviewNote)
            .update()
        return EligibilityEvidence(
            id = id,
            organizationId = organizationId,
            participantId = participantId,
            requirementId = requirementId,
            classification = classification,
            acceptanceMethod = acceptanceMethod,
            signerUserId = signerUserId,
            signerGuardianRelationship = signerGuardianRelationship,
            enteredLegalName = enteredLegalName,
            documentAssetId = documentAssetId,
            provider = provider,
            externalIdentifiers = externalIdentifiersJson,
            contentHash = contentHash,
            status = EvidenceStatus.ACTIVE,
            supersededByEvidenceId = null,
            acceptedAt = now,
            expiresAt = expiresAt,
            ipAddress = ipAddress,
            userAgent = userAgent,
            reviewedByUserId = reviewedByUserId,
            reviewNote = reviewNote,
            createdAt = now,
            updatedAt = now,
        )
    }

    /** Marks [id] SUPERSEDED/REVOKED/REJECTED/EXPIRED and links it to whatever replaced it, if anything — never deletes. */
    fun updateStatus(
        id: UUID,
        organizationId: UUID,
        status: EvidenceStatus,
        supersededByEvidenceId: UUID?,
    ): Int =
        jdbcClient
            .sql(
                """
                update eligibility_evidence
                set status = :status, superseded_by_evidence_id = :supersededByEvidenceId, updated_at = :now
                where id = :id and organization_id = :organizationId
                """.trimIndent(),
            ).param("status", status.name)
            .param("supersededByEvidenceId", supersededByEvidenceId)
            .param("now", Timestamp.from(Instant.now()))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): EligibilityEvidence =
        EligibilityEvidence(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            participantId = rs.getObject("participant_id", UUID::class.java),
            requirementId = rs.getObject("requirement_id", UUID::class.java),
            classification = EvidenceClassification.valueOf(rs.getString("classification")),
            acceptanceMethod = AcceptanceMethod.valueOf(rs.getString("acceptance_method")),
            signerUserId = rs.getObject("signer_user_id", UUID::class.java),
            signerGuardianRelationship = rs.getString("signer_guardian_relationship"),
            enteredLegalName = rs.getString("entered_legal_name"),
            documentAssetId = rs.getObject("document_asset_id", UUID::class.java),
            provider = rs.getString("provider"),
            externalIdentifiers = rs.getString("external_identifiers"),
            contentHash = rs.getString("content_hash"),
            status = EvidenceStatus.valueOf(rs.getString("status")),
            supersededByEvidenceId = rs.getObject("superseded_by_evidence_id", UUID::class.java),
            acceptedAt = rs.getTimestamp("accepted_at").toInstant(),
            expiresAt = rs.getTimestamp("expires_at")?.toInstant(),
            ipAddress = rs.getString("ip_address"),
            userAgent = rs.getString("user_agent"),
            reviewedByUserId = rs.getObject("reviewed_by_user_id", UUID::class.java),
            reviewNote = rs.getString("review_note"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
