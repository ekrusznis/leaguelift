package com.leaguelift.seasonrollover.persistence

import com.leaguelift.authorization.domain.ResourceRole
import com.leaguelift.media.domain.PublicationStatus
import com.leaguelift.media.domain.Visibility
import com.leaguelift.seasonrollover.domain.SeasonRolloverBrandingItem
import com.leaguelift.seasonrollover.domain.SeasonRolloverRosterItem
import com.leaguelift.seasonrollover.domain.SeasonRolloverRun
import com.leaguelift.seasonrollover.domain.SeasonRolloverStaffItem
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Suppress("UNUSED_PARAMETER")
@Repository
class SeasonRolloverRepository(private val jdbcClient: JdbcClient) {

	fun teamNameExists(organizationId: UUID, name: String): Boolean =
		jdbcClient.sql(
			"select exists(select 1 from team where organization_id = :organizationId and lower(name) = lower(:name))",
		)
			.param("organizationId", organizationId)
			.param("name", name)
			.query(Boolean::class.java)
			.single()

	fun listRoster(organizationId: UUID, teamId: UUID): List<SeasonRolloverRosterItem> =
		jdbcClient.sql(
			"""
			select p.id as participant_id,
			       p.first_name || ' ' || p.last_name as display_name,
			       pt.joined_at,
			       p.updated_at as participant_updated_at,
			       pt.updated_at as assignment_updated_at
			from participant_team pt
			join participant p on p.id = pt.participant_id and p.organization_id = pt.organization_id
			where pt.organization_id = :organizationId and pt.team_id = :teamId
			  and pt.status = 'ACTIVE' and p.status = 'ACTIVE'
			order by lower(p.last_name), lower(p.first_name), p.id
			""".trimIndent(),
		)
			.param("organizationId", organizationId)
			.param("teamId", teamId)
			.query(::mapRoster)
			.list()

	fun listStaff(organizationId: UUID, teamId: UUID): List<SeasonRolloverStaffItem> =
		jdbcClient.sql(
			"""
			select ra.id as assignment_id, ra.user_id, u.display_name, u.email, ra.role,
			       ra.updated_at as assignment_updated_at, u.updated_at as user_updated_at
			from role_assignment ra
			join app_user u on u.id = ra.user_id
			where ra.organization_id = :organizationId and ra.context_type = 'TEAM'
			  and ra.resource_id = :teamId and ra.status = 'ACTIVE' and u.status = 'ACTIVE'
			order by lower(u.display_name), lower(u.email), ra.role, ra.id
			""".trimIndent(),
		)
			.param("organizationId", organizationId)
			.param("teamId", teamId)
			.query(::mapStaff)
			.list()

	fun listBranding(organizationId: UUID, teamId: UUID): List<SeasonRolloverBrandingItem> =
		jdbcClient.sql(
			"""
			select ma.id as assignment_id, ma.asset_id, ma.usage_slot, a.original_file_name,
			       ma.publication_status, ma.visibility, ma.alt_text,
			       ma.updated_at as assignment_updated_at, a.updated_at as asset_updated_at
			from media_assignment ma
			join media_asset a on a.id = ma.asset_id and a.organization_id = ma.organization_id
			where ma.organization_id = :organizationId and ma.entity_type = 'TEAM'
			  and ma.entity_id = :teamId and ma.publication_status <> 'RETIRED'
			  and ma.usage_slot in ('LOGO', 'COVER') and a.status = 'READY'
			order by ma.usage_slot, ma.id
			""".trimIndent(),
		)
			.param("organizationId", organizationId)
			.param("teamId", teamId)
			.query(::mapBranding)
			.list()

	fun copyRoster(organizationId: UUID, sourceTeamId: UUID, destinationTeamId: UUID): Int =
		jdbcClient.sql(
			"""
			insert into participant_team
			    (id, participant_id, team_id, organization_id, status, joined_at, created_at, updated_at)
			select gen_random_uuid(), pt.participant_id, :destinationTeamId, pt.organization_id,
			       'ACTIVE', null, now(), now()
			from participant_team pt
			join participant p on p.id = pt.participant_id and p.organization_id = pt.organization_id
			where pt.organization_id = :organizationId and pt.team_id = :sourceTeamId
			  and pt.status = 'ACTIVE' and p.status = 'ACTIVE'
			order by pt.participant_id
			""".trimIndent(),
		)
			.param("destinationTeamId", destinationTeamId)
			.param("organizationId", organizationId)
			.param("sourceTeamId", sourceTeamId)
			.update()

	fun copyStaff(organizationId: UUID, sourceTeamId: UUID, destinationTeamId: UUID, grantedBy: UUID): Int =
		jdbcClient.sql(
			"""
			insert into role_assignment
			    (id, organization_id, user_id, context_type, resource_id, role, status, granted_by, created_at, updated_at)
			select gen_random_uuid(), ra.organization_id, ra.user_id, 'TEAM', :destinationTeamId,
			       ra.role, 'ACTIVE', :grantedBy, now(), now()
			from role_assignment ra
			join app_user u on u.id = ra.user_id
			where ra.organization_id = :organizationId and ra.context_type = 'TEAM'
			  and ra.resource_id = :sourceTeamId and ra.status = 'ACTIVE' and u.status = 'ACTIVE'
			order by ra.id
			""".trimIndent(),
		)
			.param("destinationTeamId", destinationTeamId)
			.param("grantedBy", grantedBy)
			.param("organizationId", organizationId)
			.param("sourceTeamId", sourceTeamId)
			.update()

	fun copyBranding(organizationId: UUID, sourceTeamId: UUID, destinationTeamId: UUID): Int =
		jdbcClient.sql(
			"""
			insert into media_assignment
			    (id, organization_id, asset_id, entity_type, entity_id, usage_slot,
			     publication_status, visibility, alt_text, created_at, updated_at)
			select gen_random_uuid(), ma.organization_id, ma.asset_id, 'TEAM', :destinationTeamId,
			       ma.usage_slot, ma.publication_status, ma.visibility, ma.alt_text, now(), now()
			from media_assignment ma
			join media_asset a on a.id = ma.asset_id and a.organization_id = ma.organization_id
			where ma.organization_id = :organizationId and ma.entity_type = 'TEAM'
			  and ma.entity_id = :sourceTeamId and ma.publication_status <> 'RETIRED'
			  and ma.usage_slot in ('LOGO', 'COVER') and a.status = 'READY'
			order by ma.usage_slot, ma.id
			""".trimIndent(),
		)
			.param("destinationTeamId", destinationTeamId)
			.param("organizationId", organizationId)
			.param("sourceTeamId", sourceTeamId)
			.update()

	fun findRunByHash(organizationId: UUID, confirmationHash: String): SeasonRolloverRun? =
		jdbcClient.sql(
			"""
			select id, organization_id, source_team_id, destination_team_id, confirmation_hash,
			       archive_source_team, copy_roster, copy_staff, copy_branding,
			       roster_copied_count, staff_copied_count, branding_copied_count,
			       executed_by_user_id, created_at
			from season_rollover_run
			where organization_id = :organizationId and confirmation_hash = :confirmationHash
			""".trimIndent(),
		)
			.param("organizationId", organizationId)
			.param("confirmationHash", confirmationHash)
			.query(::mapRun)
			.optional()
			.orElse(null)

	fun insertRun(
		organizationId: UUID,
		sourceTeamId: UUID,
		destinationTeamId: UUID,
		confirmationHash: String,
		archiveSourceTeam: Boolean,
		copyRoster: Boolean,
		copyStaff: Boolean,
		copyBranding: Boolean,
		rosterCopiedCount: Int,
		staffCopiedCount: Int,
		brandingCopiedCount: Int,
		executedByUserId: UUID,
	): SeasonRolloverRun {
		val id = UUID.randomUUID()
		val now = Instant.now()
		jdbcClient.sql(
			"""
			insert into season_rollover_run
			    (id, organization_id, source_team_id, destination_team_id, confirmation_hash,
			     archive_source_team, copy_roster, copy_staff, copy_branding,
			     roster_copied_count, staff_copied_count, branding_copied_count,
			     executed_by_user_id, created_at)
			values
			    (:id, :organizationId, :sourceTeamId, :destinationTeamId, :confirmationHash,
			     :archiveSourceTeam, :copyRoster, :copyStaff, :copyBranding,
			     :rosterCopiedCount, :staffCopiedCount, :brandingCopiedCount,
			     :executedByUserId, :createdAt)
			""".trimIndent(),
		)
			.param("id", id)
			.param("organizationId", organizationId)
			.param("sourceTeamId", sourceTeamId)
			.param("destinationTeamId", destinationTeamId)
			.param("confirmationHash", confirmationHash)
			.param("archiveSourceTeam", archiveSourceTeam)
			.param("copyRoster", copyRoster)
			.param("copyStaff", copyStaff)
			.param("copyBranding", copyBranding)
			.param("rosterCopiedCount", rosterCopiedCount)
			.param("staffCopiedCount", staffCopiedCount)
			.param("brandingCopiedCount", brandingCopiedCount)
			.param("executedByUserId", executedByUserId)
			.param("createdAt", Timestamp.from(now))
			.update()
		return SeasonRolloverRun(
			id, organizationId, sourceTeamId, destinationTeamId, confirmationHash,
			archiveSourceTeam, copyRoster, copyStaff, copyBranding,
			rosterCopiedCount, staffCopiedCount, brandingCopiedCount, executedByUserId, now,
		)
	}

	private fun mapRoster(rs: ResultSet, rowNum: Int) = SeasonRolloverRosterItem(
		participantId = rs.getObject("participant_id", UUID::class.java),
		displayName = rs.getString("display_name"),
		joinedAt = rs.getDate("joined_at")?.toLocalDate(),
		participantUpdatedAt = rs.getTimestamp("participant_updated_at").toInstant(),
		assignmentUpdatedAt = rs.getTimestamp("assignment_updated_at").toInstant(),
	)

	private fun mapStaff(rs: ResultSet, rowNum: Int) = SeasonRolloverStaffItem(
		assignmentId = rs.getObject("assignment_id", UUID::class.java),
		userId = rs.getObject("user_id", UUID::class.java),
		displayName = rs.getString("display_name"),
		email = rs.getString("email"),
		role = ResourceRole.valueOf(rs.getString("role")),
		assignmentUpdatedAt = rs.getTimestamp("assignment_updated_at").toInstant(),
		userUpdatedAt = rs.getTimestamp("user_updated_at").toInstant(),
	)

	private fun mapBranding(rs: ResultSet, rowNum: Int) = SeasonRolloverBrandingItem(
		assignmentId = rs.getObject("assignment_id", UUID::class.java),
		assetId = rs.getObject("asset_id", UUID::class.java),
		usageSlot = rs.getString("usage_slot"),
		fileName = rs.getString("original_file_name"),
		publicationStatus = PublicationStatus.valueOf(rs.getString("publication_status")),
		visibility = Visibility.valueOf(rs.getString("visibility")),
		altText = rs.getString("alt_text"),
		assignmentUpdatedAt = rs.getTimestamp("assignment_updated_at").toInstant(),
		assetUpdatedAt = rs.getTimestamp("asset_updated_at").toInstant(),
	)

	private fun mapRun(rs: ResultSet, rowNum: Int) = SeasonRolloverRun(
		id = rs.getObject("id", UUID::class.java),
		organizationId = rs.getObject("organization_id", UUID::class.java),
		sourceTeamId = rs.getObject("source_team_id", UUID::class.java),
		destinationTeamId = rs.getObject("destination_team_id", UUID::class.java),
		confirmationHash = rs.getString("confirmation_hash").trim(),
		archiveSourceTeam = rs.getBoolean("archive_source_team"),
		copyRoster = rs.getBoolean("copy_roster"),
		copyStaff = rs.getBoolean("copy_staff"),
		copyBranding = rs.getBoolean("copy_branding"),
		rosterCopiedCount = rs.getInt("roster_copied_count"),
		staffCopiedCount = rs.getInt("staff_copied_count"),
		brandingCopiedCount = rs.getInt("branding_copied_count"),
		executedByUserId = rs.getObject("executed_by_user_id", UUID::class.java),
		createdAt = rs.getTimestamp("created_at").toInstant(),
	)
}
