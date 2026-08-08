package com.rally26.identityintegrity.persistence

import com.rally26.identityintegrity.domain.DuplicateCandidateGroup
import com.rally26.identityintegrity.domain.DuplicateGuardianLink
import com.rally26.identityintegrity.domain.DuplicateIdentityKind
import com.rally26.identityintegrity.domain.DuplicateIdentitySummary
import com.rally26.identityintegrity.domain.DuplicateMatchEvidence
import com.rally26.identityintegrity.domain.DuplicateMatchType
import com.rally26.identityintegrity.domain.DuplicateOrganizationMembership
import com.rally26.identityintegrity.domain.DuplicateRoleAssignment
import com.rally26.identityintegrity.domain.IdentityDependency
import com.rally26.identityintegrity.domain.IdentityRef
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class DuplicateIdentityRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findCandidates(
        query: String?,
        limit: Int,
    ): List<DuplicateCandidateGroup> {
        val duplicateKeys = duplicateKeys(query, limit)
        return duplicateKeys.mapNotNull { key ->
            val refs = identityRefsForKey(key.first, key.second)
            val identities =
                refs
                    .mapNotNull(::findIdentity)
                    .distinctBy { it.ref }
                    .sortedWith(compareBy<DuplicateIdentitySummary>({ it.ref.kind.name }, { it.createdAt }, { it.ref.id }))
            if (identities.size < 2) null else DuplicateCandidateGroup(key.first, key.second, identities)
        }
    }

    fun findIdentity(ref: IdentityRef): DuplicateIdentitySummary? =
        when (ref.kind) {
            DuplicateIdentityKind.APP_USER -> findAppUser(ref.id)
            DuplicateIdentityKind.GUARDIAN_SHELL -> findGuardianShell(ref.id)
        }

    fun sharedMatchEvidence(
        source: IdentityRef,
        target: IdentityRef,
    ): List<DuplicateMatchEvidence> {
        val targetKeys = matchEvidence(target).toSet()
        return matchEvidence(source)
            .filter { it in targetKeys }
            .distinct()
            .sortedWith(compareBy({ it.matchType.name }, { it.normalizedValue }))
    }

    private fun matchEvidence(ref: IdentityRef): List<DuplicateMatchEvidence> {
        val sql =
            when (ref.kind) {
                DuplicateIdentityKind.APP_USER ->
                    """
                    select 'EMAIL' as match_type, lower(trim(u.email)) as match_key
                    from app_user u
                    where u.id = :id and nullif(trim(u.email), '') is not null
                    union
                    select 'EMAIL', lower(trim(ha.email))
                    from guardian_relationship gr
                    join household_adult ha on ha.id = gr.household_adult_id
                    where gr.user_id = :id and gr.status = 'ACTIVE' and nullif(trim(ha.email), '') is not null
                    union
                    select 'PHONE', regexp_replace(ha.phone, '[^0-9]', '', 'g')
                    from guardian_relationship gr
                    join household_adult ha on ha.id = gr.household_adult_id
                    where gr.user_id = :id and gr.status = 'ACTIVE'
                      and length(regexp_replace(coalesce(ha.phone, ''), '[^0-9]', '', 'g')) >= 7
                    """.trimIndent()
                DuplicateIdentityKind.GUARDIAN_SHELL ->
                    """
                    select 'EMAIL' as match_type, lower(trim(ha.email)) as match_key
                    from household_adult ha
                    where ha.id = :id and nullif(trim(ha.email), '') is not null
                    union
                    select 'PHONE', regexp_replace(ha.phone, '[^0-9]', '', 'g')
                    from household_adult ha
                    where ha.id = :id
                      and length(regexp_replace(coalesce(ha.phone, ''), '[^0-9]', '', 'g')) >= 7
                    """.trimIndent()
            }
        return jdbcClient
            .sql(sql)
            .param("id", ref.id)
            .query { rs, _ ->
                DuplicateMatchEvidence(
                    matchType = DuplicateMatchType.valueOf(rs.getString("match_type")),
                    normalizedValue = rs.getString("match_key"),
                )
            }.list()
    }

    fun dependencyInventory(ref: IdentityRef): List<IdentityDependency> {
        val targetTable = if (ref.kind == DuplicateIdentityKind.APP_USER) "app_user" else "household_adult"
        return foreignKeyColumns(targetTable)
            .mapNotNull { (tableName, columnName) ->
                if (!SAFE_IDENTIFIER.matches(tableName) || !SAFE_IDENTIFIER.matches(columnName)) return@mapNotNull null
                val count =
                    jdbcClient
                        .sql("select count(*) from \"$tableName\" where \"$columnName\" = :id")
                        .param("id", ref.id)
                        .query { rs, _ -> rs.getLong(1) }
                        .single()
                if (count == 0L) {
                    null
                } else {
                    IdentityDependency(
                        tableName = tableName,
                        columnName = columnName,
                        count = count,
                        historical = isHistoricalReference(tableName, columnName),
                    )
                }
            }.sortedWith(compareBy({ it.tableName }, { it.columnName }))
    }

    private fun duplicateKeys(
        query: String?,
        limit: Int,
    ): List<Pair<DuplicateMatchType, String>> {
        val normalizedQuery = query?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val queryPredicate = if (normalizedQuery == null) "" else "where match_key like :queryLike"
        val sql =
            """
            with active_guardian_link as (
                select household_adult_id, user_id
                from guardian_relationship
                where status = 'ACTIVE'
            ), identity_key as (
                select 'APP_USER' as kind, u.id as identity_id, 'EMAIL' as match_type, lower(trim(u.email)) as match_key
                from app_user u
                where u.merged_into_user_id is null and nullif(trim(u.email), '') is not null
                union
                select 'APP_USER', gr.user_id, 'EMAIL', lower(trim(ha.email))
                from guardian_relationship gr
                join app_user u on u.id = gr.user_id and u.merged_into_user_id is null
                join household_adult ha on ha.id = gr.household_adult_id
                where gr.status = 'ACTIVE' and nullif(trim(ha.email), '') is not null
                union
                select 'APP_USER', gr.user_id, 'PHONE', regexp_replace(ha.phone, '[^0-9]', '', 'g')
                from guardian_relationship gr
                join app_user u on u.id = gr.user_id and u.merged_into_user_id is null
                join household_adult ha on ha.id = gr.household_adult_id
                where gr.status = 'ACTIVE'
                  and length(regexp_replace(coalesce(ha.phone, ''), '[^0-9]', '', 'g')) >= 7
                union
                select 'GUARDIAN_SHELL', ha.id, 'EMAIL', lower(trim(ha.email))
                from household_adult ha
                left join active_guardian_link gr on gr.household_adult_id = ha.id
                where gr.user_id is null and ha.status = 'ACTIVE' and nullif(trim(ha.email), '') is not null
                union
                select 'GUARDIAN_SHELL', ha.id, 'PHONE', regexp_replace(ha.phone, '[^0-9]', '', 'g')
                from household_adult ha
                left join active_guardian_link gr on gr.household_adult_id = ha.id
                where gr.user_id is null and ha.status = 'ACTIVE'
                  and length(regexp_replace(coalesce(ha.phone, ''), '[^0-9]', '', 'g')) >= 7
            )
            select match_type, match_key
            from identity_key
            $queryPredicate
            group by match_type, match_key
            having count(distinct kind || ':' || identity_id::text) > 1
            order by case match_type when 'EMAIL' then 0 else 1 end, match_key
            limit :limit
            """.trimIndent()
        var spec = jdbcClient.sql(sql)
        if (normalizedQuery != null) spec = spec.param("queryLike", "%$normalizedQuery%")
        return spec
            .param("limit", limit)
            .query { rs, _ -> DuplicateMatchType.valueOf(rs.getString("match_type")) to rs.getString("match_key") }
            .list()
    }

    private fun identityRefsForKey(
        matchType: DuplicateMatchType,
        matchKey: String,
    ): List<IdentityRef> {
        val sql =
            when (matchType) {
                DuplicateMatchType.EMAIL ->
                    """
                    with active_guardian_link as (
                        select household_adult_id, user_id from guardian_relationship where status = 'ACTIVE'
                    )
                    select distinct 'APP_USER' as kind, u.id
                    from app_user u
                    where u.merged_into_user_id is null and lower(trim(u.email)) = :matchKey
                    union
                    select distinct 'APP_USER', gr.user_id
                    from guardian_relationship gr
                    join app_user u on u.id = gr.user_id and u.merged_into_user_id is null
                    join household_adult ha on ha.id = gr.household_adult_id
                    where gr.status = 'ACTIVE' and lower(trim(ha.email)) = :matchKey
                    union
                    select distinct 'GUARDIAN_SHELL', ha.id
                    from household_adult ha
                    left join active_guardian_link gr on gr.household_adult_id = ha.id
                    where gr.user_id is null and ha.status = 'ACTIVE' and lower(trim(ha.email)) = :matchKey
                    """.trimIndent()
                DuplicateMatchType.PHONE ->
                    """
                    with active_guardian_link as (
                        select household_adult_id, user_id from guardian_relationship where status = 'ACTIVE'
                    )
                    select distinct 'APP_USER' as kind, gr.user_id as id
                    from guardian_relationship gr
                    join app_user u on u.id = gr.user_id and u.merged_into_user_id is null
                    join household_adult ha on ha.id = gr.household_adult_id
                    where gr.status = 'ACTIVE' and regexp_replace(coalesce(ha.phone, ''), '[^0-9]', '', 'g') = :matchKey
                    union
                    select distinct 'GUARDIAN_SHELL', ha.id
                    from household_adult ha
                    left join active_guardian_link gr on gr.household_adult_id = ha.id
                    where gr.user_id is null and ha.status = 'ACTIVE'
                      and regexp_replace(coalesce(ha.phone, ''), '[^0-9]', '', 'g') = :matchKey
                    """.trimIndent()
            }
        return jdbcClient
            .sql(sql)
            .param("matchKey", matchKey)
            .query { rs, _ -> IdentityRef(DuplicateIdentityKind.valueOf(rs.getString("kind")), rs.getObject("id", UUID::class.java)) }
            .list()
    }

    private fun findAppUser(userId: UUID): DuplicateIdentitySummary? {
        val base =
            jdbcClient
                .sql(
                    """
                    select u.id, u.display_name, u.email, u.status, u.created_at, u.merged_into_user_id,
                           (select ha.phone
                            from guardian_relationship gr join household_adult ha on ha.id = gr.household_adult_id
                            where gr.user_id = u.id and gr.status = 'ACTIVE' and nullif(trim(ha.phone), '') is not null
                            order by ha.created_at, ha.id limit 1) as phone,
                           exists(select 1 from role_assignment ra where ra.user_id = u.id and ra.context_type = 'PLATFORM' and ra.role = 'PLATFORM_ADMIN') as platform_admin
                    from app_user u where u.id = :id
                    """.trimIndent(),
                ).param("id", userId)
                .query { rs, _ ->
                    DuplicateIdentitySummary(
                        ref = IdentityRef(DuplicateIdentityKind.APP_USER, rs.getObject("id", UUID::class.java)),
                        displayName = rs.getString("display_name"),
                        email = rs.getString("email"),
                        phone = rs.getString("phone"),
                        status = rs.getString("status"),
                        createdAt = rs.getTimestamp("created_at").toInstant(),
                        organizationId = null,
                        organizationName = null,
                        householdId = null,
                        householdName = null,
                        linkedUserId = null,
                        platformAdministrator = rs.getBoolean("platform_admin"),
                        memberships = emptyList(),
                        externalIds = emptyList(),
                        mergedIntoUserId = rs.getObject("merged_into_user_id", UUID::class.java),
                    )
                }.optional()
                .orElse(null) ?: return null
        return base.copy(
            memberships = memberships(userId),
            externalIds = userExternalIds(userId),
            roleAssignments = roleAssignments(userId),
            guardianLinks = guardianLinks(userId),
        )
    }

    private fun findGuardianShell(adultId: UUID): DuplicateIdentitySummary? =
        jdbcClient
            .sql(
                """
                select ha.id, concat_ws(' ', ha.first_name, ha.last_name) as display_name, ha.email, ha.phone, ha.status, ha.created_at,
                       ha.organization_id, o.name as organization_name, ha.household_id, h.display_name as household_name,
                       (select gr.user_id from guardian_relationship gr where gr.household_adult_id = ha.id and gr.status = 'ACTIVE' order by gr.created_at, gr.id limit 1) as linked_user_id,
                       coalesce((select array_agg(oi.external_id order by oi.external_id)
                                 from onboarding_import_identity oi
                                 where oi.entity_type = 'GUARDIAN' and oi.entity_id = ha.id), array[]::text[]) as external_ids
                from household_adult ha
                join organization o on o.id = ha.organization_id
                join household h on h.id = ha.household_id
                where ha.id = :id
                """.trimIndent(),
            ).param("id", adultId)
            .query { rs, _ ->
                DuplicateIdentitySummary(
                    ref = IdentityRef(DuplicateIdentityKind.GUARDIAN_SHELL, rs.getObject("id", UUID::class.java)),
                    displayName = rs.getString("display_name"),
                    email = rs.getString("email"),
                    phone = rs.getString("phone"),
                    status = rs.getString("status"),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                    organizationId = rs.getObject("organization_id", UUID::class.java),
                    organizationName = rs.getString("organization_name"),
                    householdId = rs.getObject("household_id", UUID::class.java),
                    householdName = rs.getString("household_name"),
                    linkedUserId = rs.getObject("linked_user_id", UUID::class.java),
                    platformAdministrator = false,
                    memberships = emptyList(),
                    externalIds = (rs.getArray("external_ids")?.array as? Array<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                )
            }.optional()
            .orElse(null)

    private fun memberships(userId: UUID): List<DuplicateOrganizationMembership> =
        jdbcClient
            .sql(
                """
                select om.organization_id, o.name as organization_name, om.role, om.status
                from organization_membership om join organization o on o.id = om.organization_id
                where om.user_id = :userId
                order by o.name, om.organization_id
                """.trimIndent(),
            ).param("userId", userId)
            .query { rs, _ ->
                DuplicateOrganizationMembership(
                    organizationId = rs.getObject("organization_id", UUID::class.java),
                    organizationName = rs.getString("organization_name"),
                    role = rs.getString("role"),
                    status = rs.getString("status"),
                )
            }.list()

    private fun roleAssignments(userId: UUID): List<DuplicateRoleAssignment> =
        jdbcClient
            .sql(
                """
                select organization_id, context_type, resource_id, role
                from role_assignment
                where user_id = :userId and status = 'ACTIVE' and context_type <> 'PLATFORM'
                order by organization_id, context_type, resource_id, role
                """.trimIndent(),
            ).param("userId", userId)
            .query { rs, _ ->
                DuplicateRoleAssignment(
                    organizationId = rs.getObject("organization_id", UUID::class.java),
                    contextType = rs.getString("context_type"),
                    resourceId = rs.getObject("resource_id", UUID::class.java),
                    role = rs.getString("role"),
                )
            }.list()

    private fun guardianLinks(userId: UUID): List<DuplicateGuardianLink> =
        jdbcClient
            .sql(
                """
                select organization_id, household_id, household_adult_id
                from guardian_relationship
                where user_id = :userId and status = 'ACTIVE'
                order by organization_id, household_id, household_adult_id
                """.trimIndent(),
            ).param("userId", userId)
            .query { rs, _ ->
                DuplicateGuardianLink(
                    organizationId = rs.getObject("organization_id", UUID::class.java),
                    householdId = rs.getObject("household_id", UUID::class.java),
                    householdAdultId = rs.getObject("household_adult_id", UUID::class.java),
                )
            }.list()

    private fun userExternalIds(userId: UUID): List<String> =
        jdbcClient
            .sql(
                """
                select distinct oi.external_id
                from guardian_relationship gr
                join onboarding_import_identity oi on oi.entity_type = 'GUARDIAN' and oi.entity_id = gr.household_adult_id
                where gr.user_id = :userId and gr.status = 'ACTIVE'
                order by oi.external_id
                """.trimIndent(),
            ).param("userId", userId)
            .query { rs, _ -> rs.getString("external_id") }
            .list()
            .filterNotNull()

    private fun foreignKeyColumns(targetTable: String): List<Pair<String, String>> =
        jdbcClient
            .sql(
                """
                select distinct tc.table_name, kcu.column_name
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on tc.constraint_name = kcu.constraint_name and tc.table_schema = kcu.table_schema
                join information_schema.constraint_column_usage ccu
                  on ccu.constraint_name = tc.constraint_name and ccu.table_schema = tc.table_schema
                where tc.constraint_type = 'FOREIGN KEY'
                  and tc.table_schema = 'public'
                  and ccu.table_name = :targetTable
                  and ccu.column_name = 'id'
                order by tc.table_name, kcu.column_name
                """.trimIndent(),
            ).param("targetTable", targetTable)
            .query { rs, _ -> rs.getString("table_name") to rs.getString("column_name") }
            .list()

    private fun isHistoricalReference(
        tableName: String,
        columnName: String,
    ): Boolean =
        (tableName == "audit_event" && columnName == "actor_user_id") ||
            (tableName == "role_assignment" && columnName == "granted_by") ||
            columnName.endsWith("_by_user_id")

    companion object {
        private val SAFE_IDENTIFIER = Regex("^[a-z][a-z0-9_]*$")
    }
}
