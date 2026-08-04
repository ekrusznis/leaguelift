package com.rally26.platformadmin.persistence

import com.rally26.common.web.PageRequest
import com.rally26.platformadmin.domain.PlatformOrganizationDetail
import com.rally26.platformadmin.domain.PlatformOrganizationListItem
import com.rally26.platformadmin.domain.PlatformSupportAccessListItem
import com.rally26.platformadmin.domain.PlatformSupportAccessStatus
import com.rally26.platformadmin.domain.PlatformUserListItem
import com.rally26.platformadmin.domain.PlatformUserOrganizationMembership
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class PlatformAdminConsoleRepository(private val jdbcClient: JdbcClient) {

	fun listOrganizations(query: String?, status: String?, pageRequest: PageRequest): List<PlatformOrganizationListItem> {
		val where = mutableListOf<String>()
		if (!query.isNullOrBlank()) where += "(lower(o.name) like :query or lower(o.slug) like :query or lower(coalesce(o.contact_email, '')) like :query)"
		if (!status.isNullOrBlank()) where += "o.status = :status"
		val whereSql = if (where.isEmpty()) "" else "where ${where.joinToString(" and ")}"
		var spec = jdbcClient.sql(
			"""
			select o.id, o.name, o.slug, o.organization_type, o.status, o.contact_email, o.created_at,
			       (select u.display_name from organization_membership om join app_user u on u.id = om.user_id where om.organization_id = o.id and om.role = 'OWNER' and om.status = 'ACTIVE' order by om.created_at asc limit 1) as primary_owner_name,
			       (select u.email from organization_membership om join app_user u on u.id = om.user_id where om.organization_id = o.id and om.role = 'OWNER' and om.status = 'ACTIVE' order by om.created_at asc limit 1) as primary_owner_email,
			       (select count(*) from organization_membership om where om.organization_id = o.id and om.status = 'ACTIVE') as active_members,
			       (select count(*) from team t where t.organization_id = o.id and t.status = 'ACTIVE') as teams,
			       (select count(*) from household h where h.organization_id = o.id and h.status = 'ACTIVE') as households,
			       (select count(*) from participant p where p.organization_id = o.id and p.status = 'ACTIVE') as participants,
			       coalesce((select sum(le.amount_minor) from ledger_entry le where le.organization_id = o.id and le.direction = 'CREDIT' and le.entry_type in ('GROSS_SALE', 'CONTRIBUTION')), 0) as gross_volume_minor
			from organization o
			$whereSql
			order by o.created_at desc, o.name asc
			limit :limit offset :offset
			""".trimIndent(),
		)
		if (!query.isNullOrBlank()) spec = spec.param("query", "%${query.trim().lowercase()}%")
		if (!status.isNullOrBlank()) spec = spec.param("status", status)
		return spec.param("limit", pageRequest.size)
			.param("offset", pageRequest.offset)
			.query { rs, _ ->
				PlatformOrganizationListItem(
					organizationId = rs.getObject("id", UUID::class.java),
					name = rs.getString("name"),
					slug = rs.getString("slug"),
					organizationType = rs.getString("organization_type"),
					status = rs.getString("status"),
					contactEmail = rs.getString("contact_email"),
					primaryOwnerName = rs.getString("primary_owner_name"),
					primaryOwnerEmail = rs.getString("primary_owner_email"),
					createdAt = rs.getTimestamp("created_at").toInstant(),
					activeMembers = rs.getLong("active_members"),
					teams = rs.getLong("teams"),
					households = rs.getLong("households"),
					participants = rs.getLong("participants"),
					grossVolumeMinor = rs.getLong("gross_volume_minor"),
				)
			}.list()
	}

	fun countOrganizations(query: String?, status: String?): Long {
		val where = mutableListOf<String>()
		if (!query.isNullOrBlank()) where += "(lower(name) like :query or lower(slug) like :query or lower(coalesce(contact_email, '')) like :query)"
		if (!status.isNullOrBlank()) where += "status = :status"
		val whereSql = if (where.isEmpty()) "" else "where ${where.joinToString(" and ")}"
		var spec = jdbcClient.sql("select count(*) from organization $whereSql")
		if (!query.isNullOrBlank()) spec = spec.param("query", "%${query.trim().lowercase()}%")
		if (!status.isNullOrBlank()) spec = spec.param("status", status)
		return spec.query(Long::class.java).single()
	}

	fun findOrganization(organizationId: UUID): PlatformOrganizationDetail? =
		jdbcClient.sql(
			"""
			select o.id, o.name, o.slug, o.organization_type, o.status, o.contact_email, o.contact_phone, o.created_at, o.updated_at,
			       coalesce((select string_agg(u.display_name, '|||' order by u.email) from organization_membership om join app_user u on u.id = om.user_id where om.organization_id = o.id and om.role = 'OWNER' and om.status = 'ACTIVE'), '') as owner_names,
			       coalesce((select string_agg(u.email, '|||' order by u.email) from organization_membership om join app_user u on u.id = om.user_id where om.organization_id = o.id and om.role = 'OWNER' and om.status = 'ACTIVE'), '') as owner_emails,
			       (select count(*) from organization_membership om where om.organization_id = o.id and om.status = 'ACTIVE') as active_members,
			       (select count(*) from organization_membership om where om.organization_id = o.id and om.status = 'INVITED') as invited_members,
			       (select count(*) from team t where t.organization_id = o.id and t.status = 'ACTIVE') as teams,
			       (select count(*) from tournament t where t.organization_id = o.id and t.status = 'ACTIVE') as tournaments,
			       (select count(*) from household h where h.organization_id = o.id and h.status = 'ACTIVE') as households,
			       (select count(*) from household_adult ha where ha.organization_id = o.id and ha.status = 'ACTIVE') as guardians,
			       (select count(*) from participant p where p.organization_id = o.id and p.status = 'ACTIVE') as participants,
			       (select count(*) from event e where e.organization_id = o.id) as events,
			       (select count(*) from store s where s.organization_id = o.id and s.status <> 'ARCHIVED') as stores,
			       (select count(*) from "order" ord where ord.organization_id = o.id) as orders,
			       (select count(*) from campaign c where c.organization_id = o.id and c.status <> 'ARCHIVED') as campaigns,
			       (select count(*) from contribution c where c.organization_id = o.id) as contributions,
			       (select count(*) from sponsorship s where s.organization_id = o.id) as sponsorships,
			       (select count(*)
			        from media_assignment ma
			        left join household document_household
			          on ma.entity_type = 'HOUSEHOLD' and document_household.id = ma.entity_id
			        where ma.usage_slot = 'DOCUMENT'
			          and ma.publication_status <> 'RETIRED'
			          and ((ma.entity_type = 'ORGANIZATION' and ma.entity_id = o.id)
			               or document_household.organization_id = o.id)) as documents,
			       (select count(*) from event_source_connection esc where esc.organization_id = o.id and esc.status = 'ACTIVE') as active_event_connections,
			       coalesce((select sum(le.amount_minor) from ledger_entry le where le.organization_id = o.id and le.direction = 'CREDIT' and le.entry_type in ('GROSS_SALE', 'CONTRIBUTION')), 0) as gross_volume_minor,
			       coalesce((select sum(le.amount_minor) from ledger_entry le where le.organization_id = o.id and le.direction = 'DEBIT' and le.entry_type = 'REFUND'), 0) as refunded_minor,
			       coalesce((select sum(case when le.direction = 'CREDIT' then le.amount_minor else -le.amount_minor end) from ledger_entry le where le.organization_id = o.id and le.entry_type = 'ORGANIZATION_EARNING'), 0) as organization_earnings_minor
			from organization o
			where o.id = :organizationId
			""".trimIndent(),
		)
			.param("organizationId", organizationId)
			.query { rs, _ ->
				PlatformOrganizationDetail(
					organizationId = rs.getObject("id", UUID::class.java),
					name = rs.getString("name"),
					slug = rs.getString("slug"),
					organizationType = rs.getString("organization_type"),
					status = rs.getString("status"),
					contactEmail = rs.getString("contact_email"),
					contactPhone = rs.getString("contact_phone"),
					createdAt = rs.getTimestamp("created_at").toInstant(),
					updatedAt = rs.getTimestamp("updated_at").toInstant(),
					ownerNames = splitAggregate(rs.getString("owner_names")),
					ownerEmails = splitAggregate(rs.getString("owner_emails")),
					activeMembers = rs.getLong("active_members"),
					invitedMembers = rs.getLong("invited_members"),
					teams = rs.getLong("teams"),
					tournaments = rs.getLong("tournaments"),
					households = rs.getLong("households"),
					guardians = rs.getLong("guardians"),
					participants = rs.getLong("participants"),
					events = rs.getLong("events"),
					stores = rs.getLong("stores"),
					orders = rs.getLong("orders"),
					campaigns = rs.getLong("campaigns"),
					contributions = rs.getLong("contributions"),
					sponsorships = rs.getLong("sponsorships"),
					documents = rs.getLong("documents"),
					activeEventConnections = rs.getLong("active_event_connections"),
					grossVolumeMinor = rs.getLong("gross_volume_minor"),
					refundedMinor = rs.getLong("refunded_minor"),
					organizationEarningsMinor = rs.getLong("organization_earnings_minor"),
				)
			}.optional().orElse(null)

	fun listSupportAccess(status: String?, pageRequest: PageRequest): List<PlatformSupportAccessListItem> {
		val effectiveStatus = "case when psa.status = 'ACTIVE' and psa.expires_at <= now() then 'EXPIRED' else psa.status end"
		val whereSql = if (status.isNullOrBlank()) "" else "where $effectiveStatus = :status"
		var spec = jdbcClient.sql(
			"""
			select psa.id, psa.platform_admin_user_id, u.display_name as platform_admin_name, u.email as platform_admin_email,
			       psa.organization_id, o.name as organization_name, psa.reason, $effectiveStatus as effective_status,
			       psa.expires_at, psa.ended_at, psa.created_at
			from platform_support_access psa
			join app_user u on u.id = psa.platform_admin_user_id
			join organization o on o.id = psa.organization_id
			$whereSql
			order by case when $effectiveStatus = 'ACTIVE' then 0 else 1 end, psa.created_at desc
			limit :limit offset :offset
			""".trimIndent(),
		)
		if (!status.isNullOrBlank()) spec = spec.param("status", status)
		return spec.param("limit", pageRequest.size).param("offset", pageRequest.offset)
			.query { rs, _ ->
				PlatformSupportAccessListItem(
					id = rs.getObject("id", UUID::class.java),
					platformAdminUserId = rs.getObject("platform_admin_user_id", UUID::class.java),
					platformAdminName = rs.getString("platform_admin_name"),
					platformAdminEmail = rs.getString("platform_admin_email"),
					organizationId = rs.getObject("organization_id", UUID::class.java),
					organizationName = rs.getString("organization_name"),
					reason = rs.getString("reason"),
					status = PlatformSupportAccessStatus.valueOf(rs.getString("effective_status")),
					expiresAt = rs.getTimestamp("expires_at").toInstant(),
					endedAt = rs.getTimestamp("ended_at")?.toInstant(),
					createdAt = rs.getTimestamp("created_at").toInstant(),
				)
			}.list()
	}

	fun countSupportAccess(status: String?): Long {
		val effectiveStatus = "case when status = 'ACTIVE' and expires_at <= now() then 'EXPIRED' else status end"
		val whereSql = if (status.isNullOrBlank()) "" else "where $effectiveStatus = :status"
		var spec = jdbcClient.sql("select count(*) from platform_support_access $whereSql")
		if (!status.isNullOrBlank()) spec = spec.param("status", status)
		return spec.query(Long::class.java).single()
	}

	fun listUsers(query: String?, status: String?, pageRequest: PageRequest): List<PlatformUserListItem> {
		val where = mutableListOf<String>()
		if (!query.isNullOrBlank()) where += "(lower(u.email) like :query or lower(u.display_name) like :query)"
		if (!status.isNullOrBlank()) where += "u.status = :status"
		val whereSql = if (where.isEmpty()) "" else "where ${where.joinToString(" and ")}"
		var spec = jdbcClient.sql(
			"""
			select u.id, u.email, u.display_name, u.status, u.created_at,
			       exists(select 1 from role_assignment ra where ra.user_id = u.id and ra.context_type = 'PLATFORM' and ra.role = 'PLATFORM_ADMIN' and ra.status = 'ACTIVE') as platform_admin,
			       (select count(*) from organization_membership om where om.user_id = u.id and om.status = 'ACTIVE') as active_memberships,
			       coalesce((select string_agg(o.id::text, '|||' order by o.name, o.id::text) from organization_membership om join organization o on o.id = om.organization_id where om.user_id = u.id and om.status = 'ACTIVE'), '') as organization_ids,
			       coalesce((select string_agg(o.name, '|||' order by o.name, o.id::text) from organization_membership om join organization o on o.id = om.organization_id where om.user_id = u.id and om.status = 'ACTIVE'), '') as organizations,
			       coalesce((select string_agg(om.role, '|||' order by o.name, o.id::text) from organization_membership om join organization o on o.id = om.organization_id where om.user_id = u.id and om.status = 'ACTIVE'), '') as organization_roles
			from app_user u
			$whereSql
			order by u.created_at desc, u.display_name asc
			limit :limit offset :offset
			""".trimIndent(),
		)
		if (!query.isNullOrBlank()) spec = spec.param("query", "%${query.trim().lowercase()}%")
		if (!status.isNullOrBlank()) spec = spec.param("status", status)
		return spec.param("limit", pageRequest.size)
			.param("offset", pageRequest.offset)
			.query { rs, _ ->
				PlatformUserListItem(
					userId = rs.getObject("id", UUID::class.java),
					email = rs.getString("email"),
					displayName = rs.getString("display_name"),
					status = rs.getString("status"),
					createdAt = rs.getTimestamp("created_at").toInstant(),
					platformAdmin = rs.getBoolean("platform_admin"),
					activeMemberships = rs.getLong("active_memberships"),
					organizationMemberships = membershipAggregates(
						splitAggregate(rs.getString("organization_ids")),
						splitAggregate(rs.getString("organizations")),
						splitAggregate(rs.getString("organization_roles")),
					),
				)
			}.list()
	}

	fun countUsers(query: String?, status: String?): Long {
		val where = mutableListOf<String>()
		if (!query.isNullOrBlank()) where += "(lower(email) like :query or lower(display_name) like :query)"
		if (!status.isNullOrBlank()) where += "status = :status"
		val whereSql = if (where.isEmpty()) "" else "where ${where.joinToString(" and ")}"
		var spec = jdbcClient.sql("select count(*) from app_user $whereSql")
		if (!query.isNullOrBlank()) spec = spec.param("query", "%${query.trim().lowercase()}%")
		if (!status.isNullOrBlank()) spec = spec.param("status", status)
		return spec.query(Long::class.java).single()
	}

	private fun membershipAggregates(ids: List<String>, names: List<String>, roles: List<String>): List<PlatformUserOrganizationMembership> {
		require(ids.size == names.size && names.size == roles.size) { "Platform user membership aggregates are misaligned." }
		return ids.indices.map { index ->
			PlatformUserOrganizationMembership(UUID.fromString(ids[index]), names[index], roles[index])
		}
	}

	private fun splitAggregate(value: String?): List<String> = value
		?.takeIf { it.isNotBlank() }
		?.split("|||")
		?: emptyList()
}
