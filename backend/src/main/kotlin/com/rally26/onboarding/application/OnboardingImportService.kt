package com.rally26.onboarding.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.common.error.ConflictException
import com.rally26.common.error.ValidationException
import com.rally26.common.util.CsvUtil
import com.rally26.common.web.CurrentUser
import com.rally26.household.domain.AdultStatus
import com.rally26.household.domain.HouseholdStatus
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.membership.application.MembershipService
import com.rally26.onboarding.domain.ImportedOnboardingEntity
import com.rally26.onboarding.domain.OnboardingImportPreview
import com.rally26.onboarding.domain.OnboardingImportResult
import com.rally26.onboarding.domain.OnboardingImportRowError
import com.rally26.onboarding.domain.OnboardingPreviewAction
import com.rally26.onboarding.domain.OnboardingPreviewRow
import com.rally26.onboarding.domain.OnboardingRecordType
import com.rally26.onboarding.persistence.OnboardingImportIdentityRepository
import com.rally26.participant.domain.ParticipantStatus
import com.rally26.participant.persistence.ParticipantRepository
import com.rally26.team.domain.Sport
import com.rally26.team.domain.TeamStatus
import com.rally26.team.persistence.TeamRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

private const val MAX_IMPORT_BYTES = 2 * 1024 * 1024
private const val MAX_IMPORT_ROWS = 5_000
private val REQUIRED_HEADERS = setOf("record_type", "external_id")
private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/**
 * Phase 16 slice 1 manual-onboarding CSV import.
 *
 * Preview is side-effect free. Execute re-runs the same validation, verifies the
 * caller-confirmed content hash, then upserts valid rows in dependency order. The
 * original CSV is never persisted; only organization-scoped external-id mappings and
 * one immutable audit summary remain.
 */
@Service
class OnboardingImportService(
    private val identityRepository: OnboardingImportIdentityRepository,
    private val teamRepository: TeamRepository,
    private val householdRepository: HouseholdRepository,
    private val participantRepository: ParticipantRepository,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
) {
    fun preview(
        organizationId: UUID,
        fileName: String?,
        csvContent: String,
        currentUser: CurrentUser,
    ): OnboardingImportPreview {
        membershipService.requireManagerRole(organizationId, currentUser)
        return analyze(organizationId, fileName, csvContent).toPreview()
    }

    @Transactional
    fun execute(
        organizationId: UUID,
        fileName: String?,
        csvContent: String,
        expectedContentHash: String,
        currentUser: CurrentUser,
    ): OnboardingImportResult {
        membershipService.requireManagerRole(organizationId, currentUser)
        val analysis = analyze(organizationId, fileName, csvContent)
        if (!analysis.contentHash.equals(expectedContentHash.trim(), ignoreCase = true)) {
            throw ValidationException("The CSV changed after preview. Preview the current file again before importing.")
        }

        val errors =
            analysis.rows
                .filter { it.errors.isNotEmpty() }
                .map {
                    OnboardingImportRowError(
                        rowNumber = it.rowNumber,
                        recordType = it.recordType,
                        externalId = it.externalId,
                        message = it.errors.joinToString(" "),
                    )
                }.toMutableList()
        val validRows = analysis.rows.filter { it.errors.isEmpty() && it.recordType != null && it.externalId != null }
        val resolved = mutableMapOf<Pair<OnboardingRecordType, String>, UUID>()
        validRows.forEach { row ->
            row.existingEntityId?.let { resolved[row.recordType!! to row.externalId!!] = it }
        }

        var created = 0
        var updated = 0
        var matched = 0
        var assignmentsCreated = 0
        val imported = mutableListOf<ImportedOnboardingEntity>()

        val processingOrder =
            listOf(
                OnboardingRecordType.TEAM,
                OnboardingRecordType.HOUSEHOLD,
                OnboardingRecordType.GUARDIAN,
                OnboardingRecordType.PARTICIPANT,
            )
        for (recordType in processingOrder) {
            for (row in validRows.filter { it.recordType == recordType }) {
                val externalId = row.externalId!!
                val entityId =
                    try {
                        when (recordType) {
                            OnboardingRecordType.TEAM -> upsertTeam(organizationId, row)
                            OnboardingRecordType.HOUSEHOLD -> upsertHousehold(organizationId, row)
                            OnboardingRecordType.GUARDIAN -> upsertGuardian(organizationId, row, resolved)
                            OnboardingRecordType.PARTICIPANT -> {
                                val result = upsertParticipant(organizationId, row, resolved)
                                if (result.assignmentCreated) assignmentsCreated++
                                result.entityId
                            }
                        }
                    } catch (error: ValidationException) {
                        errors +=
                            OnboardingImportRowError(
                                rowNumber = row.rowNumber,
                                recordType = row.recordType,
                                externalId = externalId,
                                message = error.message,
                            )
                        continue
                    }

                val identity =
                    identityRepository.insert(
                        organizationId = organizationId,
                        entityType = recordType,
                        externalId = externalId,
                        entityId = entityId,
                        createdByUserId = currentUser.userId,
                    )
                if (identity.entityId != entityId || identity.externalId != externalId) {
                    throw ConflictException(
                        "ONBOARDING_EXTERNAL_ID_RACE",
                        "The ${recordType.name.lowercase()} external_id \"$externalId\" or matched record was bound by another import. Preview the file again.",
                    )
                }
                resolved[recordType to externalId] = entityId
                when (row.action) {
                    OnboardingPreviewAction.CREATE -> created++
                    OnboardingPreviewAction.UPDATE -> updated++
                    OnboardingPreviewAction.MATCH_EXISTING -> matched++
                    OnboardingPreviewAction.ERROR -> Unit
                }
                imported +=
                    ImportedOnboardingEntity(
                        recordType = recordType,
                        externalId = externalId,
                        entityId = entityId,
                        label = row.label,
                        action = row.action,
                    )
            }
        }

        val importRunId = UUID.randomUUID()
        val metadata =
            objectMapper.writeValueAsString(
                mapOf(
                    "fileName" to fileName?.take(255),
                    "contentHash" to analysis.contentHash,
                    "totalRows" to analysis.rows.size,
                    "created" to created,
                    "updated" to updated,
                    "matched" to matched,
                    "teamAssignmentsCreated" to assignmentsCreated,
                    "errors" to errors.size,
                ),
            )
        auditService.record(
            actorUserId = currentUser.userId,
            organizationId = organizationId,
            action = "onboarding.csv_imported",
            entityType = "onboarding_import",
            entityId = importRunId,
            metadataJson = metadata,
        )

        return OnboardingImportResult(
            fileName = fileName,
            contentHash = analysis.contentHash,
            createdCount = created,
            updatedCount = updated,
            matchedCount = matched,
            teamAssignmentsCreated = assignmentsCreated,
            errors = errors.sortedBy { it.rowNumber },
            entities = imported,
        )
    }

    private fun analyze(
        organizationId: UUID,
        fileName: String?,
        csvContent: String,
    ): Analysis {
        val bytes = csvContent.toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty()) throw ValidationException("The CSV file is empty.")
        if (bytes.size > MAX_IMPORT_BYTES) throw ValidationException("The CSV file must be 2 MB or smaller.")

        val parsed = CsvUtil.parse(csvContent)
        if (parsed.isEmpty()) throw ValidationException("The CSV file is empty.")
        if (parsed.size - 1 > MAX_IMPORT_ROWS) throw ValidationException("The CSV file may contain at most $MAX_IMPORT_ROWS data rows.")

        val header =
            parsed.first().mapIndexed { index, value ->
                value.trim().removePrefix(if (index == 0) "\uFEFF" else "").lowercase()
            }
        val duplicateHeaders =
            header
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        if (duplicateHeaders.isNotEmpty()) {
            throw ValidationException("Duplicate column(s): ${duplicateHeaders.sorted().joinToString(", ")}.")
        }
        val missingHeaders = REQUIRED_HEADERS - header.toSet()
        if (missingHeaders.isNotEmpty()) {
            throw ValidationException("Missing required column(s): ${missingHeaders.sorted().joinToString(", ")}.")
        }

        val rows =
            parsed
                .drop(1)
                .mapIndexedNotNull { index, raw ->
                    if (raw.all { it.isBlank() }) return@mapIndexedNotNull null
                    val fields = header.mapIndexed { column, name -> name to raw.getOrElse(column) { "" }.trim() }.toMap()
                    val row = AnalysisRow(rowNumber = index + 2, fields = fields)
                    if (raw.size > header.size && raw.drop(header.size).any { it.isNotBlank() }) {
                        row.errors += "The row contains more values than the header defines."
                    }
                    parseIdentity(row)
                    validateFields(row)
                    row
                }.toMutableList()

        val duplicateKeys =
            rows
                .filter { it.recordType != null && it.externalId != null }
                .groupBy { it.recordType!! to it.externalId!! }
                .filterValues { it.size > 1 }
        duplicateKeys.values.flatten().forEach { it.errors += "Duplicate record_type/external_id in this file." }

        val fileKeys =
            rows
                .filter { it.recordType != null && it.externalId != null }
                .map { it.recordType!! to it.externalId!! }
                .toSet()

        rows.forEach { row ->
            validateReferences(organizationId, row, fileKeys)

            if (row.errors.isNotEmpty()) {
                row.action = OnboardingPreviewAction.ERROR
            }
        }

        val rowsByKey =
            rows
                .filter { it.recordType != null && it.externalId != null }
                .groupBy { it.recordType!! to it.externalId!! }
        val resolvedExisting = mutableMapOf<Pair<OnboardingRecordType, String>, UUID>()
        val processingOrder =
            listOf(
                OnboardingRecordType.TEAM,
                OnboardingRecordType.HOUSEHOLD,
                OnboardingRecordType.GUARDIAN,
                OnboardingRecordType.PARTICIPANT,
            )
        for (recordType in processingOrder) {
            for (row in rows.filter { it.recordType == recordType && it.errors.isEmpty() }) {
                validatePreviewDependencies(row, rowsByKey)
                if (row.errors.isNotEmpty()) {
                    row.action = OnboardingPreviewAction.ERROR
                    continue
                }
                resolvePreviewAction(organizationId, row, resolvedExisting)
                if (row.errors.isNotEmpty()) {
                    row.action = OnboardingPreviewAction.ERROR
                } else {
                    row.existingEntityId?.let { resolvedExisting[recordType to row.externalId!!] = it }
                }
            }
        }
        return Analysis(fileName, sha256Hex(csvContent), rows)
    }

    private fun parseIdentity(row: AnalysisRow) {
        val rawType =
            row.fields["record_type"]
                .orEmpty()
                .trim()
                .uppercase()
        row.recordType =
            when (rawType) {
                "TEAM" -> OnboardingRecordType.TEAM
                "HOUSEHOLD" -> OnboardingRecordType.HOUSEHOLD
                "GUARDIAN", "ADULT", "HOUSEHOLD_ADULT" -> OnboardingRecordType.GUARDIAN
                "PARTICIPANT", "ATHLETE", "PLAYER" -> OnboardingRecordType.PARTICIPANT
                else -> null
            }
        if (row.recordType == null) {
            row.errors += "record_type must be TEAM, HOUSEHOLD, GUARDIAN, or PARTICIPANT."
        }
        val externalId = normalizeExternalId(row.fields["external_id"])
        if (externalId == null) {
            row.errors += "external_id is required."
        } else if (externalId.length > 200) {
            row.errors += "external_id must be 200 characters or fewer."
        }
        row.externalId = externalId
    }

    private fun validateFields(row: AnalysisRow) {
        when (row.recordType) {
            OnboardingRecordType.TEAM -> {
                requireField(row, "name")
                requireField(row, "sport")
                validateMaxLength(row, "name", 120)
                validateMaxLength(row, "sport", 60)
                validateMaxLength(row, "season", 60)
                validateEmail(row, "contact_email")
            }
            OnboardingRecordType.HOUSEHOLD -> {
                requireField(row, "display_name")
                validateMaxLength(row, "display_name", 120)
                validateEmail(row, "contact_email")
                validateMaxLength(row, "contact_phone", 40)
                validateMaxLength(row, "notes", 1_000)
            }
            OnboardingRecordType.GUARDIAN -> {
                requireField(row, "household_external_id")
                requireField(row, "first_name")
                requireField(row, "last_name")
                validateMaxLength(row, "household_external_id", 200)
                validateMaxLength(row, "first_name", 60)
                validateMaxLength(row, "last_name", 60)
                validateEmail(row, "email")
                validateMaxLength(row, "phone", 40)
                validateMaxLength(row, "relationship", 60)
                parseBoolean(row.fields["is_primary"], "is_primary", row)
            }
            OnboardingRecordType.PARTICIPANT -> {
                requireField(row, "household_external_id")
                requireField(row, "first_name")
                requireField(row, "last_name")
                validateMaxLength(row, "household_external_id", 200)
                validateMaxLength(row, "team_external_id", 200)
                validateMaxLength(row, "first_name", 60)
                validateMaxLength(row, "last_name", 60)
                validateMaxLength(row, "notes", 1_000)
                parseDate(row.fields["date_of_birth"], "date_of_birth", row)
            }
            null -> Unit
        }
    }

    private fun validateReferences(
        organizationId: UUID,
        row: AnalysisRow,
        fileKeys: Set<Pair<OnboardingRecordType, String>>,
    ) {
        fun requireReference(
            field: String,
            targetType: OnboardingRecordType,
        ) {
            val reference = normalizeExternalId(row.fields[field]) ?: return

            /*
             * The target exists in this file. Its validity is checked later by
             * validatePreviewDependencies so the referencing row receives the more
             * useful "refers to a row in this file that has errors" message.
             */
            if (targetType to reference in fileKeys) {
                return
            }

            val identity =
                identityRepository.find(
                    organizationId,
                    targetType,
                    reference,
                )

            when {
                identity == null -> {
                    row.errors +=
                        "$field \"$reference\" does not match a $targetType row in this file or an earlier import."
                }

                !entityExists(
                    organizationId,
                    targetType,
                    identity.entityId,
                ) -> {
                    row.errors +=
                        "$field \"$reference\" points to a missing or inactive $targetType record. Contact Rally26 support before importing."
                }
            }
        }

        when (row.recordType) {
            OnboardingRecordType.GUARDIAN -> {
                requireReference(
                    "household_external_id",
                    OnboardingRecordType.HOUSEHOLD,
                )
            }

            OnboardingRecordType.PARTICIPANT -> {
                requireReference(
                    "household_external_id",
                    OnboardingRecordType.HOUSEHOLD,
                )

                if (!row.fields["team_external_id"].isNullOrBlank()) {
                    requireReference(
                        "team_external_id",
                        OnboardingRecordType.TEAM,
                    )
                }
            }

            else -> Unit
        }
    }

    private fun validatePreviewDependencies(
        row: AnalysisRow,
        rowsByKey: Map<Pair<OnboardingRecordType, String>, List<AnalysisRow>>,
    ) {
        fun requireValidFileReference(
            field: String,
            targetType: OnboardingRecordType,
        ) {
            val externalId = normalizeExternalId(row.fields[field]) ?: return
            val targetRows = rowsByKey[targetType to externalId].orEmpty()
            if (targetRows.isNotEmpty() && targetRows.any { it.errors.isNotEmpty() }) {
                row.errors += "$field \"$externalId\" refers to a row in this file that has errors."
            }
        }
        when (row.recordType) {
            OnboardingRecordType.GUARDIAN -> requireValidFileReference("household_external_id", OnboardingRecordType.HOUSEHOLD)
            OnboardingRecordType.PARTICIPANT -> {
                requireValidFileReference("household_external_id", OnboardingRecordType.HOUSEHOLD)
                if (!row.fields["team_external_id"].isNullOrBlank()) {
                    requireValidFileReference("team_external_id", OnboardingRecordType.TEAM)
                }
            }
            else -> Unit
        }
    }

    private fun resolvePreviewAction(
        organizationId: UUID,
        row: AnalysisRow,
        resolvedExisting: Map<Pair<OnboardingRecordType, String>, UUID>,
    ) {
        val recordType = row.recordType!!
        val externalId = row.externalId!!
        val identity = identityRepository.find(organizationId, recordType, externalId)
        if (identity != null) {
            if (!entityExists(organizationId, recordType, identity.entityId)) {
                row.errors +=
                    "The saved external-id mapping points to a missing or inactive record. Contact Rally26 support before importing."
                return
            }
            row.existingEntityId = identity.entityId
            validateExistingRelationship(organizationId, row, identity.entityId, resolvedExisting)
            if (row.errors.isEmpty()) row.action = OnboardingPreviewAction.UPDATE
            row.label = labelFor(row)
            return
        }

        when (recordType) {
            OnboardingRecordType.TEAM ->
                resolveSimpleNaturalMatch(
                    organizationId,
                    row,
                    teamRepository.findNameMatches(organizationId, row.required("name")).map { it.id },
                )
            OnboardingRecordType.HOUSEHOLD ->
                resolveSimpleNaturalMatch(
                    organizationId,
                    row,
                    normalizedEmail(row.fields["contact_email"])
                        ?.let { householdRepository.findContactEmailMatches(organizationId, it).map { household -> household.id } }
                        .orEmpty(),
                )
            OnboardingRecordType.GUARDIAN -> resolveGuardianNaturalMatch(organizationId, row, resolvedExisting)
            OnboardingRecordType.PARTICIPANT -> row.action = OnboardingPreviewAction.CREATE
        }
        row.label = labelFor(row)
    }

    private fun resolveSimpleNaturalMatch(
        organizationId: UUID,
        row: AnalysisRow,
        naturalMatches: List<UUID>,
    ) {
        when {
            naturalMatches.size > 1 -> {
                row.errors +=
                    "More than one existing ${row.recordType!!.name.lowercase()} matches this row. Add or reuse a previously imported external_id instead of choosing automatically."
                row.action = OnboardingPreviewAction.ERROR
            }
            naturalMatches.size == 1 -> {
                val entityId = naturalMatches.single()
                val existingBinding = identityRepository.findByEntity(organizationId, row.recordType!!, entityId)
                if (existingBinding != null) {
                    row.errors +=
                        "This existing ${row.recordType!!.name.lowercase()} is already bound to external_id \"${existingBinding.externalId}\". Reuse that external_id instead of creating an alias."
                    row.action = OnboardingPreviewAction.ERROR
                } else {
                    row.existingEntityId = entityId
                    row.action = OnboardingPreviewAction.MATCH_EXISTING
                    row.warnings +=
                        "Matched an existing ${row.recordType!!.name.lowercase()} and will only bind this external_id; existing fields will not be overwritten."
                }
            }
            else -> row.action = OnboardingPreviewAction.CREATE
        }
    }

    private fun resolveGuardianNaturalMatch(
        organizationId: UUID,
        row: AnalysisRow,
        resolvedExisting: Map<Pair<OnboardingRecordType, String>, UUID>,
    ) {
        val email = normalizedEmail(row.fields["email"])
        if (email == null) {
            row.action = OnboardingPreviewAction.CREATE
            return
        }
        val matches = householdRepository.findActiveAdultEmailMatches(organizationId, email)
        when {
            matches.size > 1 -> {
                row.errors +=
                    "More than one existing guardian uses this email. Review and merge the duplicate adult records before importing."
                row.action = OnboardingPreviewAction.ERROR
            }
            matches.size == 1 -> {
                val targetHouseholdId =
                    resolveExistingReferenceId(
                        organizationId,
                        OnboardingRecordType.HOUSEHOLD,
                        row.requiredExternalReference("household_external_id"),
                        resolvedExisting,
                    )
                val match = matches.single()
                if (targetHouseholdId == null || match.householdId != targetHouseholdId) {
                    row.errors +=
                        "A guardian with this email already belongs to another household. Use the explicit account/profile review workflow instead of creating or moving the adult silently."
                    row.action = OnboardingPreviewAction.ERROR
                } else {
                    val existingBinding = identityRepository.findByEntity(organizationId, OnboardingRecordType.GUARDIAN, match.id)
                    if (existingBinding != null) {
                        row.errors +=
                            "This guardian is already bound to external_id \"${existingBinding.externalId}\". Reuse that external_id instead of creating an alias."
                        row.action = OnboardingPreviewAction.ERROR
                    } else {
                        row.existingEntityId = match.id
                        row.action = OnboardingPreviewAction.MATCH_EXISTING
                        row.warnings +=
                            "Matched an existing guardian in the selected household and will only bind this external_id; existing fields will not be overwritten."
                    }
                }
            }
            else -> row.action = OnboardingPreviewAction.CREATE
        }
    }

    private fun validateExistingRelationship(
        organizationId: UUID,
        row: AnalysisRow,
        entityId: UUID,
        resolvedExisting: Map<Pair<OnboardingRecordType, String>, UUID>,
    ) {
        val targetHouseholdExternalId =
            when (row.recordType) {
                OnboardingRecordType.GUARDIAN, OnboardingRecordType.PARTICIPANT ->
                    row.requiredExternalReference("household_external_id")
                else -> return
            }
        val targetHouseholdId =
            resolveExistingReferenceId(
                organizationId,
                OnboardingRecordType.HOUSEHOLD,
                targetHouseholdExternalId,
                resolvedExisting,
            )
        val actualHouseholdId =
            when (row.recordType) {
                OnboardingRecordType.GUARDIAN -> householdRepository.findAdultById(entityId, organizationId)?.householdId
                OnboardingRecordType.PARTICIPANT -> participantRepository.findById(entityId, organizationId)?.householdId
                else -> null
            }
        if (targetHouseholdId == null || actualHouseholdId != targetHouseholdId) {
            row.errors +=
                "The imported ${row.recordType!!.name.lowercase()} is already linked to a different household and cannot be moved by CSV import."
        }
    }

    private fun resolveExistingReferenceId(
        organizationId: UUID,
        recordType: OnboardingRecordType,
        externalId: String,
        resolvedExisting: Map<Pair<OnboardingRecordType, String>, UUID>,
    ): UUID? =
        resolvedExisting[recordType to externalId]
            ?: identityRepository.find(organizationId, recordType, externalId)?.entityId

    /** Leniently matches an imported spreadsheet's free-text sport column to a known [Sport] code (case/whitespace-insensitive); anything unrecognized falls back to OTHER with the real imported text preserved rather than rejecting the whole row — same normalization V100's migration applied to pre-existing data. */
    private fun parseImportedSport(value: String): Pair<Sport, String?> {
        val normalized = value.trim().uppercase().replace(' ', '_')
        val matched = Sport.entries.find { it.name == normalized }
        return if (matched != null) matched to null else Sport.OTHER to value.trim()
    }

    private fun upsertTeam(
        organizationId: UUID,
        row: AnalysisRow,
    ): UUID {
        val name = row.required("name")
        val (sport, sportOtherLabel) = parseImportedSport(row.required("sport"))
        val season = blankToNull(row.fields["season"])
        val contactEmail = normalizedEmail(row.fields["contact_email"])
        val existingId = row.existingEntityId
        if (existingId == null) {
            return teamRepository.insert(organizationId, name, sport, season, contactEmail, null, null, null, sportOtherLabel).id
        }
        val existing =
            teamRepository
                .findById(existingId, organizationId)
                ?.takeIf { it.status == TeamStatus.ACTIVE }
                ?: throw ValidationException("The matched team no longer exists or is inactive.")
        if (row.action == OnboardingPreviewAction.MATCH_EXISTING) return existing.id
        teamRepository.update(existing.id, organizationId, name, sport, season, contactEmail, null, null, null, sportOtherLabel)
        return existing.id
    }

    private fun upsertHousehold(
        organizationId: UUID,
        row: AnalysisRow,
    ): UUID {
        val displayName = row.required("display_name")
        val contactEmail = normalizedEmail(row.fields["contact_email"])
        val contactPhone = blankToNull(row.fields["contact_phone"])
        val notes = blankToNull(row.fields["notes"])
        val existingId = row.existingEntityId
        if (existingId == null) {
            return householdRepository.insert(organizationId, displayName, contactEmail, contactPhone, notes).id
        }
        val existing =
            householdRepository
                .findById(existingId, organizationId)
                ?.takeIf { it.status == HouseholdStatus.ACTIVE }
                ?: throw ValidationException("The matched household no longer exists or is inactive.")
        if (row.action == OnboardingPreviewAction.MATCH_EXISTING) return existing.id
        householdRepository.update(existing.id, organizationId, displayName, contactEmail, contactPhone, notes)
        return existing.id
    }

    private fun upsertGuardian(
        organizationId: UUID,
        row: AnalysisRow,
        resolved: Map<Pair<OnboardingRecordType, String>, UUID>,
    ): UUID {
        val householdId =
            resolveReference(
                organizationId,
                OnboardingRecordType.HOUSEHOLD,
                row.requiredExternalReference("household_external_id"),
                resolved,
            )
        val firstName = row.required("first_name")
        val lastName = row.required("last_name")
        val email = normalizedEmail(row.fields["email"])
        val phone = blankToNull(row.fields["phone"])
        val relationship = blankToNull(row.fields["relationship"])
        val isPrimary = parseBooleanStrict(row.fields["is_primary"])
        val existingId = row.existingEntityId
        if (existingId == null) {
            return householdRepository
                .insertAdult(
                    householdId,
                    organizationId,
                    firstName,
                    lastName,
                    email,
                    phone,
                    relationship,
                    isPrimary ?: false,
                ).id
        }
        val existing =
            householdRepository
                .findAdultById(existingId, organizationId)
                ?.takeIf { it.status == AdultStatus.ACTIVE }
                ?: throw ValidationException("The matched guardian no longer exists or is inactive.")
        if (existing.householdId != householdId) {
            throw ValidationException("The matched guardian belongs to a different household and cannot be moved by CSV import.")
        }
        if (row.action == OnboardingPreviewAction.MATCH_EXISTING) return existing.id
        householdRepository.updateAdult(
            adultId = existing.id,
            organizationId = organizationId,
            firstName = firstName,
            lastName = lastName,
            email = email,
            phone = phone,
            relationship = relationship,
            isPrimary = isPrimary,
        )
        return existing.id
    }

    private data class ParticipantUpsertResult(
        val entityId: UUID,
        val assignmentCreated: Boolean,
    )

    private fun upsertParticipant(
        organizationId: UUID,
        row: AnalysisRow,
        resolved: Map<Pair<OnboardingRecordType, String>, UUID>,
    ): ParticipantUpsertResult {
        val householdId =
            resolveReference(
                organizationId,
                OnboardingRecordType.HOUSEHOLD,
                row.requiredExternalReference("household_external_id"),
                resolved,
            )
        val firstName = row.required("first_name")
        val lastName = row.required("last_name")
        val dateOfBirth = parseDateStrict(row.fields["date_of_birth"])
        val notes = blankToNull(row.fields["notes"])
        val teamExternalId = normalizeExternalId(row.fields["team_external_id"])
        val teamId =
            teamExternalId?.let {
                resolveReference(organizationId, OnboardingRecordType.TEAM, it, resolved)
            }
        val existingId = row.existingEntityId
        val participantId =
            if (existingId == null) {
                participantRepository.insert(householdId, organizationId, firstName, lastName, dateOfBirth, notes).id
            } else {
                val existing =
                    participantRepository
                        .findById(existingId, organizationId)
                        ?.takeIf { it.status == ParticipantStatus.ACTIVE }
                        ?: throw ValidationException("The matched participant no longer exists or is inactive.")
                if (existing.householdId != householdId) {
                    throw ValidationException("The matched participant belongs to a different household and cannot be moved by CSV import.")
                }
                participantRepository.update(existing.id, organizationId, firstName, lastName, dateOfBirth, notes)
                existing.id
            }
        val assignmentCreated =
            if (teamId != null) {
                participantRepository.ensureTeamAssignment(participantId, teamId, organizationId, joinedAt = null)
            } else {
                false
            }
        return ParticipantUpsertResult(participantId, assignmentCreated)
    }

    private fun resolveReference(
        organizationId: UUID,
        recordType: OnboardingRecordType,
        externalId: String,
        resolved: Map<Pair<OnboardingRecordType, String>, UUID>,
    ): UUID {
        val entityId =
            resolved[recordType to externalId]
                ?: identityRepository.find(organizationId, recordType, externalId)?.entityId
                ?: throw ValidationException("$recordType external_id \"$externalId\" has not been imported.")
        if (!entityExists(organizationId, recordType, entityId)) {
            throw ValidationException("$recordType external_id \"$externalId\" points to a missing or inactive record.")
        }
        return entityId
    }

    private fun entityExists(
        organizationId: UUID,
        type: OnboardingRecordType,
        entityId: UUID,
    ): Boolean =
        when (type) {
            OnboardingRecordType.TEAM -> teamRepository.findById(entityId, organizationId)?.status == TeamStatus.ACTIVE
            OnboardingRecordType.HOUSEHOLD -> householdRepository.findById(entityId, organizationId)?.status == HouseholdStatus.ACTIVE
            OnboardingRecordType.GUARDIAN -> householdRepository.findAdultById(entityId, organizationId)?.status == AdultStatus.ACTIVE
            OnboardingRecordType.PARTICIPANT -> participantRepository.findById(entityId, organizationId)?.status == ParticipantStatus.ACTIVE
        }

    private fun requireField(
        row: AnalysisRow,
        field: String,
    ) {
        if (row.fields[field].isNullOrBlank()) row.errors += "$field is required for ${row.recordType?.name ?: "this"} rows."
    }

    private fun validateMaxLength(
        row: AnalysisRow,
        field: String,
        max: Int,
    ) {
        val value = row.fields[field]?.trim() ?: return
        if (value.length > max) row.errors += "$field must be $max characters or fewer."
    }

    private fun validateEmail(
        row: AnalysisRow,
        field: String,
    ) {
        val value = normalizedEmail(row.fields[field]) ?: return
        if (value.length > 320) row.errors += "$field must be 320 characters or fewer."
        if (!EMAIL_PATTERN.matches(value)) row.errors += "$field must be a valid email address."
    }

    private fun parseBoolean(
        value: String?,
        field: String,
        row: AnalysisRow,
    ): Boolean? {
        val normalized = value?.trim()?.lowercase()
        if (normalized.isNullOrBlank()) return null
        return when (normalized) {
            "true", "yes", "y", "1" -> true
            "false", "no", "n", "0" -> false
            else -> {
                row.errors += "$field must be true/false, yes/no, or 1/0."
                null
            }
        }
    }

    private fun parseBooleanStrict(value: String?): Boolean? {
        val normalized = value?.trim()?.lowercase()
        if (normalized.isNullOrBlank()) return null
        return when (normalized) {
            "true", "yes", "y", "1" -> true
            "false", "no", "n", "0" -> false
            else -> throw ValidationException("is_primary must be true/false, yes/no, or 1/0.")
        }
    }

    private fun parseDate(
        value: String?,
        field: String,
        row: AnalysisRow,
    ): LocalDate? {
        val normalized = value?.trim()
        if (normalized.isNullOrBlank()) return null
        return try {
            LocalDate.parse(normalized)
        } catch (_: DateTimeParseException) {
            row.errors += "$field must be an ISO date such as 2012-09-15."
            null
        }
    }

    private fun parseDateStrict(value: String?): LocalDate? {
        val normalized = value?.trim()
        if (normalized.isNullOrBlank()) return null
        return try {
            LocalDate.parse(normalized)
        } catch (_: DateTimeParseException) {
            throw ValidationException("date_of_birth must be an ISO date such as 2012-09-15.")
        }
    }

    private fun labelFor(row: AnalysisRow): String =
        when (row.recordType) {
            OnboardingRecordType.TEAM -> row.fields["name"].orEmpty()
            OnboardingRecordType.HOUSEHOLD -> row.fields["display_name"].orEmpty()
            OnboardingRecordType.GUARDIAN ->
                listOf(row.fields["first_name"], row.fields["last_name"]).filterNot { it.isNullOrBlank() }.joinToString(" ")
            OnboardingRecordType.PARTICIPANT ->
                listOf(row.fields["first_name"], row.fields["last_name"]).filterNot { it.isNullOrBlank() }.joinToString(" ")
            null -> "Invalid row"
        }.ifBlank { "Unnamed record" }

    private fun normalizeExternalId(value: String?): String? = value?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

    private fun normalizedEmail(value: String?): String? = value?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

    private fun blankToNull(value: String?): String? = value?.trim()?.takeIf { it.isNotBlank() }

    private fun sha256Hex(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private data class Analysis(
        val fileName: String?,
        val contentHash: String,
        val rows: List<AnalysisRow>,
    ) {
        fun toPreview(): OnboardingImportPreview {
            val previewRows =
                rows.map {
                    OnboardingPreviewRow(
                        rowNumber = it.rowNumber,
                        recordType = it.recordType,
                        externalId = it.externalId,
                        label = it.label,
                        action = it.action,
                        warnings = it.warnings.toList(),
                        errors = it.errors.toList(),
                    )
                }
            return OnboardingImportPreview(
                fileName = fileName,
                contentHash = contentHash,
                totalRows = rows.size,
                validRows = rows.count { it.errors.isEmpty() },
                errorRows = rows.count { it.errors.isNotEmpty() },
                createCount = rows.count { it.action == OnboardingPreviewAction.CREATE },
                updateCount = rows.count { it.action == OnboardingPreviewAction.UPDATE },
                matchCount = rows.count { it.action == OnboardingPreviewAction.MATCH_EXISTING },
                rows = previewRows,
            )
        }
    }

    private data class AnalysisRow(
        val rowNumber: Int,
        val fields: Map<String, String>,
        var recordType: OnboardingRecordType? = null,
        var externalId: String? = null,
        var label: String = "Invalid row",
        var action: OnboardingPreviewAction = OnboardingPreviewAction.ERROR,
        var existingEntityId: UUID? = null,
        val warnings: MutableList<String> = mutableListOf(),
        val errors: MutableList<String> = mutableListOf(),
    ) {
        fun required(field: String): String =
            fields[field]?.trim()?.takeIf { it.isNotBlank() }
                ?: throw ValidationException("$field is required.")

        fun requiredExternalReference(field: String): String =
            fields[field]?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
                ?: throw ValidationException("$field is required.")
    }
}
