package com.rally26.platformadmin.persistence

import com.rally26.common.web.PageRequest
import com.rally26.eligibility.domain.ClearanceStatus
import com.rally26.platformadmin.domain.PlatformAthleteListItem
import com.rally26.platformadmin.domain.PlatformCoachListItem
import com.rally26.platformadmin.domain.PlatformOrganizationDetail
import com.rally26.platformadmin.domain.PlatformOrganizationListItem
import com.rally26.platformadmin.domain.PlatformPaymentListItem
import com.rally26.platformadmin.domain.PlatformPaymentType
import com.rally26.platformadmin.domain.PlatformSupportAccessListItem
import com.rally26.platformadmin.domain.PlatformSupportAccessStatus
import com.rally26.platformadmin.domain.PlatformSwagShopProductListItem
import com.rally26.platformadmin.domain.PlatformUserListItem
import com.rally26.platformadmin.domain.PlatformUserOrganizationMembership
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class PlatformAdminConsoleRepository(
    private val jdbcClient: JdbcClient,
) {
    fun listOrganizations(
        query: String?,
        status: String?,
        pageRequest: PageRequest,
    ): List<PlatformOrganizationListItem> {
        val where = mutableListOf<String>()
        if (!query.isNullOrBlank()) {
            where +=
                "(lower(o.name) like :query or lower(o.slug) like :query or lower(coalesce(o.contact_email, '')) like :query)"
        }
        if (!status.isNullOrBlank()) where += "o.status = :status"
        val whereSql = if (where.isEmpty()) "" else "where ${where.joinToString(" and ")}"
        var spec =
            jdbcClient.sql(
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
        return spec
            .param("limit", pageRequest.size)
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

    fun countOrganizations(
        query: String?,
        status: String?,
    ): Long {
        val where = mutableListOf<String>()
        if (!query.isNullOrBlank()) {
            where +=
                "(lower(name) like :query or lower(slug) like :query or lower(coalesce(contact_email, '')) like :query)"
        }
        if (!status.isNullOrBlank()) where += "status = :status"
        val whereSql = if (where.isEmpty()) "" else "where ${where.joinToString(" and ")}"
        var spec = jdbcClient.sql("select count(*) from organization $whereSql")
        if (!query.isNullOrBlank()) spec = spec.param("query", "%${query.trim().lowercase()}%")
        if (!status.isNullOrBlank()) spec = spec.param("status", status)
        return spec.query(Long::class.java).single()
    }

    fun findOrganization(organizationId: UUID): PlatformOrganizationDetail? =
        jdbcClient
            .sql(
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
            ).param("organizationId", organizationId)
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
            }.optional()
            .orElse(null)

    fun listSupportAccess(
        status: String?,
        pageRequest: PageRequest,
    ): List<PlatformSupportAccessListItem> {
        val effectiveStatus = "case when psa.status = 'ACTIVE' and psa.expires_at <= now() then 'EXPIRED' else psa.status end"
        val whereSql = if (status.isNullOrBlank()) "" else "where $effectiveStatus = :status"
        var spec =
            jdbcClient.sql(
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
        return spec
            .param("limit", pageRequest.size)
            .param("offset", pageRequest.offset)
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

    fun listUsers(
        query: String?,
        status: String?,
        pageRequest: PageRequest,
    ): List<PlatformUserListItem> {
        val where = mutableListOf<String>()
        if (!query.isNullOrBlank()) where += "(lower(u.email) like :query or lower(u.display_name) like :query)"
        if (!status.isNullOrBlank()) where += "u.status = :status"
        val whereSql = if (where.isEmpty()) "" else "where ${where.joinToString(" and ")}"
        var spec =
            jdbcClient.sql(
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
        return spec
            .param("limit", pageRequest.size)
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
                    organizationMemberships =
                        membershipAggregates(
                            splitAggregate(rs.getString("organization_ids")),
                            splitAggregate(rs.getString("organizations")),
                            splitAggregate(rs.getString("organization_roles")),
                        ),
                )
            }.list()
    }

    fun countUsers(
        query: String?,
        status: String?,
    ): Long {
        val where = mutableListOf<String>()
        if (!query.isNullOrBlank()) where += "(lower(email) like :query or lower(display_name) like :query)"
        if (!status.isNullOrBlank()) where += "status = :status"
        val whereSql = if (where.isEmpty()) "" else "where ${where.joinToString(" and ")}"
        var spec = jdbcClient.sql("select count(*) from app_user $whereSql")
        if (!query.isNullOrBlank()) spec = spec.param("query", "%${query.trim().lowercase()}%")
        if (!status.isNullOrBlank()) spec = spec.param("status", status)
        return spec.query(Long::class.java).single()
    }

    fun listSwagShopProducts(
        query: String?,
        status: String?,
        organizationId: UUID?,
        pageRequest: PageRequest,
    ): List<PlatformSwagShopProductListItem> {
        val where = mutableListOf<String>()
        if (!query.isNullOrBlank()) {
            where += "(lower(p.name) like :query or lower(s.name) like :query or lower(o.name) like :query)"
        }
        if (!status.isNullOrBlank()) {
            where += "p.status = :status"
        } else {
            where += "p.status <> 'ARCHIVED'"
        }
        if (organizationId != null) where += "p.organization_id = :organizationId"
        val whereSql = if (where.isEmpty()) "" else "where ${where.joinToString(" and ")}"
        var spec =
            jdbcClient.sql(
                """
                select p.id, p.name, p.status, p.catalog_source, p.swag_logo_media_asset_id, p.created_at, p.updated_at,
                       s.id as store_id, s.name as store_name, s.team_id,
                       t.name as team_name,
                       o.id as organization_id, o.name as organization_name,
                       (select count(*) from product_variant pv where pv.product_id = p.id) as variant_count
                from product p
                join store s on s.id = p.store_id
                join organization o on o.id = p.organization_id
                left join team t on t.id = s.team_id
                $whereSql
                order by p.created_at desc
                limit :limit offset :offset
                """.trimIndent(),
            )
        if (!query.isNullOrBlank()) spec = spec.param("query", "%${query.trim().lowercase()}%")
        if (!status.isNullOrBlank()) spec = spec.param("status", status)
        if (organizationId != null) spec = spec.param("organizationId", organizationId)
        return spec
            .param("limit", pageRequest.size)
            .param("offset", pageRequest.offset)
            .query { rs, _ ->
                PlatformSwagShopProductListItem(
                    productId = rs.getObject("id", UUID::class.java),
                    productName = rs.getString("name"),
                    status = rs.getString("status"),
                    catalogSource = rs.getString("catalog_source"),
                    storeId = rs.getObject("store_id", UUID::class.java),
                    storeName = rs.getString("store_name"),
                    teamId = rs.getObject("team_id", UUID::class.java),
                    teamName = rs.getString("team_name"),
                    organizationId = rs.getObject("organization_id", UUID::class.java),
                    organizationName = rs.getString("organization_name"),
                    variantCount = rs.getLong("variant_count"),
                    hasSwagLogo = rs.getObject("swag_logo_media_asset_id") != null,
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                    updatedAt = rs.getTimestamp("updated_at").toInstant(),
                )
            }.list()
    }

    fun countSwagShopProducts(
        query: String?,
        status: String?,
        organizationId: UUID?,
    ): Long {
        val where = mutableListOf<String>()
        if (!query.isNullOrBlank()) {
            where += "(lower(p.name) like :query or lower(s.name) like :query or lower(o.name) like :query)"
        }
        if (!status.isNullOrBlank()) {
            where += "p.status = :status"
        } else {
            where += "p.status <> 'ARCHIVED'"
        }
        if (organizationId != null) where += "p.organization_id = :organizationId"
        val whereSql = if (where.isEmpty()) "" else "where ${where.joinToString(" and ")}"
        var spec =
            jdbcClient.sql(
                """
                select count(*) from product p
                join store s on s.id = p.store_id
                join organization o on o.id = p.organization_id
                $whereSql
                """.trimIndent(),
            )
        if (!query.isNullOrBlank()) spec = spec.param("query", "%${query.trim().lowercase()}%")
        if (!status.isNullOrBlank()) spec = spec.param("status", status)
        if (organizationId != null) spec = spec.param("organizationId", organizationId)
        return spec.query(Long::class.java).single()
    }

    /**
     * Platform Admin "every attempted payment" list — a `union all` normalizing
     * order/contribution/sponsorship/fee_payment into one row shape (see
     * PlatformPaymentListItem), so a support-facing search can span all four
     * payment-taking domains in one paginated query instead of four separate lookups.
     */
    private val paymentsCte =
        """
        with payments as (
            select 'ORDER' as type, o.id, o.organization_id, org.name as organization_name, s.team_id, t.name as team_name,
                   cast(null as uuid) as parent_id, o.supporter_name as payer_name, o.supporter_email as payer_email,
                   coalesce((select sum(oi.unit_price_minor * oi.quantity) from order_item oi where oi.order_id = o.id), 0) as amount_minor,
                   o.currency, o.status, o.created_at, o.confirmed_at,
                   coalesce(o.refunded_at, case when o.status = 'CANCELED' then o.created_at end) as closed_at,
                   (o.stripe_payment_intent_id is not null and o.status = 'CONFIRMED') as can_refund_or_void
            from "order" o
            join organization org on org.id = o.organization_id
            join store s on s.id = o.store_id
            left join team t on t.id = s.team_id

            union all

            select 'CONTRIBUTION', c.id, c.organization_id, org.name, cast(null as uuid), cast(null as varchar),
                   c.campaign_id, c.supporter_name, c.supporter_email, c.amount_minor, c.currency, c.status, c.created_at, c.confirmed_at,
                   c.refunded_at, (c.stripe_payment_intent_id is not null and c.status = 'CONFIRMED')
            from contribution c
            join organization org on org.id = c.organization_id

            union all

            select 'SPONSORSHIP', sp.id, sp.organization_id, org.name, cast(null as uuid), cast(null as varchar),
                   sp.package_id, spn.name, spn.contact_email, sp.amount_minor, sp.currency, sp.status, sp.created_at, sp.confirmed_at,
                   sp.refunded_at, (sp.stripe_payment_intent_id is not null and sp.status = 'CONFIRMED')
            from sponsorship sp
            join organization org on org.id = sp.organization_id
            join sponsor spn on spn.id = sp.sponsor_id

            union all

            select 'FEE', fp.id, fp.organization_id, org.name, cast(null as uuid), cast(null as varchar),
                   fp.fee_assignment_id, fp.payer_name, fp.payer_email, fp.amount_minor, fp.currency, fp.status, fp.created_at,
                   cast(null as timestamptz),
                   fp.voided_at, (fp.status = 'CONFIRMED' and fp.voided_at is null)
            from fee_payment fp
            join organization org on org.id = fp.organization_id
        )
        """.trimIndent()

    private fun paymentsWhere(
        type: String?,
        status: String?,
        organizationId: UUID?,
        teamId: UUID?,
        query: String?,
        dateFrom: Instant?,
        dateTo: Instant?,
    ): Pair<String, List<Pair<String, Any>>> {
        val where = mutableListOf<String>()
        val params = mutableListOf<Pair<String, Any>>()
        if (!type.isNullOrBlank()) {
            where += "type = :type"
            params += "type" to type
        }
        if (!status.isNullOrBlank()) {
            where += "status = :status"
            params += "status" to status
        }
        if (organizationId != null) {
            where += "organization_id = :organizationId"
            params += "organizationId" to organizationId
        }
        if (teamId != null) {
            where += "team_id = :teamId"
            params += "teamId" to teamId
        }
        if (!query.isNullOrBlank()) {
            where +=
                "(lower(coalesce(payer_name, '')) like :query or lower(coalesce(payer_email, '')) like :query " +
                    "or lower(organization_name) like :query or lower(coalesce(team_name, '')) like :query)"
            params += "query" to "%${query.trim().lowercase()}%"
        }
        if (dateFrom != null) {
            where += "created_at >= :dateFrom"
            params += "dateFrom" to Timestamp.from(dateFrom)
        }
        if (dateTo != null) {
            where += "created_at <= :dateTo"
            params += "dateTo" to Timestamp.from(dateTo)
        }
        val whereSql = if (where.isEmpty()) "" else "where ${where.joinToString(" and ")}"
        return whereSql to params
    }

    fun listPayments(
        type: String?,
        status: String?,
        organizationId: UUID?,
        teamId: UUID?,
        query: String?,
        dateFrom: Instant?,
        dateTo: Instant?,
        pageRequest: PageRequest,
    ): List<PlatformPaymentListItem> {
        val (whereSql, params) = paymentsWhere(type, status, organizationId, teamId, query, dateFrom, dateTo)
        var spec =
            jdbcClient.sql(
                """
                $paymentsCte
                select * from payments
                $whereSql
                order by created_at desc
                limit :limit offset :offset
                """.trimIndent(),
            )
        params.forEach { (name, value) -> spec = spec.param(name, value) }
        return spec
            .param("limit", pageRequest.size)
            .param("offset", pageRequest.offset)
            .query { rs, _ ->
                PlatformPaymentListItem(
                    type = PlatformPaymentType.valueOf(rs.getString("type")),
                    id = rs.getObject("id", UUID::class.java),
                    organizationId = rs.getObject("organization_id", UUID::class.java),
                    organizationName = rs.getString("organization_name"),
                    teamId = rs.getObject("team_id", UUID::class.java),
                    teamName = rs.getString("team_name"),
                    parentId = rs.getObject("parent_id", UUID::class.java),
                    payerName = rs.getString("payer_name"),
                    payerEmail = rs.getString("payer_email"),
                    amountMinor = rs.getLong("amount_minor"),
                    currency = rs.getString("currency"),
                    status = rs.getString("status"),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                    confirmedAt = rs.getTimestamp("confirmed_at")?.toInstant(),
                    closedAt = rs.getTimestamp("closed_at")?.toInstant(),
                    canRefundOrVoid = rs.getBoolean("can_refund_or_void"),
                )
            }.list()
    }

    fun countPayments(
        type: String?,
        status: String?,
        organizationId: UUID?,
        teamId: UUID?,
        query: String?,
        dateFrom: Instant?,
        dateTo: Instant?,
    ): Long {
        val (whereSql, params) = paymentsWhere(type, status, organizationId, teamId, query, dateFrom, dateTo)
        var spec =
            jdbcClient.sql(
                """
                $paymentsCte
                select count(*) from payments
                $whereSql
                """.trimIndent(),
            )
        params.forEach { (name, value) -> spec = spec.param(name, value) }
        return spec.query(Long::class.java).single()
    }

    /**
     * Platform Admin cross-org athlete roster (org > team > household > athlete
     * drill-down). One row per non-archived participant, aggregating every team
     * they're actively rostered on and their worst (most-concerning) eligibility
     * clearance across those teams — same severity order as the frontend's
     * worstClearanceByParticipant (HouseholdDetailPage.tsx), just computed in SQL so
     * it can be paginated and filtered server-side instead of client-side.
     */
    private val athletesCte =
        """
        with athletes as (
            select p.id, p.first_name, p.last_name, p.date_of_birth,
                   h.id as household_id, h.display_name as household_name,
                   o.id as organization_id, o.name as organization_name,
                   coalesce(string_agg(distinct t.name, ', ' order by t.name), '') as team_names,
                   (select ec.status from eligibility_clearance ec
                    where ec.participant_id = p.id
                    order by case ec.status
                        when 'INELIGIBLE' then 0 when 'EXPIRED' then 1 when 'UNDER_REVIEW' then 2
                        when 'DOCUMENTS_REQUIRED' then 3 when 'ROSTER_PENDING' then 4 else 5 end
                    limit 1) as eligibility_status
            from participant p
            join household h on h.id = p.household_id
            join organization o on o.id = p.organization_id
            left join participant_team pt on pt.participant_id = p.id and pt.status = 'ACTIVE'
            left join team t on t.id = pt.team_id
            where p.status <> 'ARCHIVED'
        """.trimIndent()

    private fun athletesFilters(
        organizationId: UUID?,
        teamId: UUID?,
        householdId: UUID?,
        query: String?,
    ): Pair<String, List<Pair<String, Any>>> {
        val where = mutableListOf<String>()
        val params = mutableListOf<Pair<String, Any>>()
        if (organizationId != null) {
            where += "o.id = :organizationId"
            params += "organizationId" to organizationId
        }
        if (householdId != null) {
            where += "h.id = :householdId"
            params += "householdId" to householdId
        }
        if (teamId != null) {
            where += "exists (select 1 from participant_team pt2 where pt2.participant_id = p.id and pt2.team_id = :teamId and pt2.status = 'ACTIVE')"
            params += "teamId" to teamId
        }
        if (!query.isNullOrBlank()) {
            where += "(lower(p.first_name) like :query or lower(p.last_name) like :query or lower(h.display_name) like :query)"
            params += "query" to "%${query.trim().lowercase()}%"
        }
        val whereSql = if (where.isEmpty()) "" else "and ${where.joinToString(" and ")}"
        return whereSql to params
    }

    fun listAthletes(
        organizationId: UUID?,
        teamId: UUID?,
        householdId: UUID?,
        eligibilityStatus: ClearanceStatus?,
        query: String?,
        pageRequest: PageRequest,
    ): List<PlatformAthleteListItem> {
        val (filterSql, filterParams) = athletesFilters(organizationId, teamId, householdId, query)
        val eligibilitySql = if (eligibilityStatus != null) "where eligibility_status = :eligibilityStatus" else ""
        var spec =
            jdbcClient.sql(
                """
                $athletesCte
                $filterSql
                group by p.id, h.id, o.id
                )
                select * from athletes
                $eligibilitySql
                order by last_name, first_name
                limit :limit offset :offset
                """.trimIndent(),
            )
        filterParams.forEach { (name, value) -> spec = spec.param(name, value) }
        if (eligibilityStatus != null) spec = spec.param("eligibilityStatus", eligibilityStatus.name)
        return spec
            .param("limit", pageRequest.size)
            .param("offset", pageRequest.offset)
            .query { rs, _ ->
                PlatformAthleteListItem(
                    participantId = rs.getObject("id", UUID::class.java),
                    firstName = rs.getString("first_name"),
                    lastName = rs.getString("last_name"),
                    dateOfBirth = rs.getDate("date_of_birth")?.toLocalDate(),
                    householdId = rs.getObject("household_id", UUID::class.java),
                    householdName = rs.getString("household_name"),
                    organizationId = rs.getObject("organization_id", UUID::class.java),
                    organizationName = rs.getString("organization_name"),
                    teamNames = rs.getString("team_names").let { if (it.isNullOrBlank()) emptyList() else it.split(", ") },
                    eligibilityStatus = rs.getString("eligibility_status")?.let(ClearanceStatus::valueOf),
                )
            }.list()
    }

    fun countAthletes(
        organizationId: UUID?,
        teamId: UUID?,
        householdId: UUID?,
        eligibilityStatus: ClearanceStatus?,
        query: String?,
    ): Long {
        val (filterSql, filterParams) = athletesFilters(organizationId, teamId, householdId, query)
        val eligibilitySql = if (eligibilityStatus != null) "where eligibility_status = :eligibilityStatus" else ""
        var spec =
            jdbcClient.sql(
                """
                $athletesCte
                $filterSql
                group by p.id, h.id, o.id
                )
                select count(*) from athletes
                $eligibilitySql
                """.trimIndent(),
            )
        filterParams.forEach { (name, value) -> spec = spec.param(name, value) }
        if (eligibilityStatus != null) spec = spec.param("eligibilityStatus", eligibilityStatus.name)
        return spec.query(Long::class.java).single()
    }

    /** Platform Admin cross-org coach/staff roster — one row per active TEAM-context coach-tier role_assignment. */
    fun listCoaches(
        organizationId: UUID?,
        teamId: UUID?,
        query: String?,
        pageRequest: PageRequest,
    ): List<PlatformCoachListItem> {
        val (whereSql, params) = coachesWhere(organizationId, teamId, query)
        var spec =
            jdbcClient.sql(
                """
                select ra.id, u.id as user_id, u.display_name, u.email, ra.role,
                       t.id as team_id, t.name as team_name, o.id as organization_id, o.name as organization_name
                from role_assignment ra
                join app_user u on u.id = ra.user_id
                join team t on t.id = ra.resource_id
                join organization o on o.id = t.organization_id
                where ra.context_type = 'TEAM' and ra.role in ('COACH_READ', 'TEAM_EDITOR', 'TEAM_MANAGER') and ra.status = 'ACTIVE'
                $whereSql
                order by o.name, t.name, u.display_name
                limit :limit offset :offset
                """.trimIndent(),
            )
        params.forEach { (name, value) -> spec = spec.param(name, value) }
        return spec
            .param("limit", pageRequest.size)
            .param("offset", pageRequest.offset)
            .query { rs, _ ->
                PlatformCoachListItem(
                    roleAssignmentId = rs.getObject("id", UUID::class.java),
                    userId = rs.getObject("user_id", UUID::class.java),
                    displayName = rs.getString("display_name"),
                    email = rs.getString("email"),
                    role = rs.getString("role"),
                    teamId = rs.getObject("team_id", UUID::class.java),
                    teamName = rs.getString("team_name"),
                    organizationId = rs.getObject("organization_id", UUID::class.java),
                    organizationName = rs.getString("organization_name"),
                )
            }.list()
    }

    fun countCoaches(
        organizationId: UUID?,
        teamId: UUID?,
        query: String?,
    ): Long {
        val (whereSql, params) = coachesWhere(organizationId, teamId, query)
        var spec =
            jdbcClient.sql(
                """
                select count(*)
                from role_assignment ra
                join app_user u on u.id = ra.user_id
                join team t on t.id = ra.resource_id
                join organization o on o.id = t.organization_id
                where ra.context_type = 'TEAM' and ra.role in ('COACH_READ', 'TEAM_EDITOR', 'TEAM_MANAGER') and ra.status = 'ACTIVE'
                $whereSql
                """.trimIndent(),
            )
        params.forEach { (name, value) -> spec = spec.param(name, value) }
        return spec.query(Long::class.java).single()
    }

    private fun coachesWhere(
        organizationId: UUID?,
        teamId: UUID?,
        query: String?,
    ): Pair<String, List<Pair<String, Any>>> {
        val where = mutableListOf<String>()
        val params = mutableListOf<Pair<String, Any>>()
        if (organizationId != null) {
            where += "o.id = :organizationId"
            params += "organizationId" to organizationId
        }
        if (teamId != null) {
            where += "t.id = :teamId"
            params += "teamId" to teamId
        }
        if (!query.isNullOrBlank()) {
            where += "(lower(u.display_name) like :query or lower(u.email) like :query)"
            params += "query" to "%${query.trim().lowercase()}%"
        }
        val whereSql = if (where.isEmpty()) "" else "and ${where.joinToString(" and ")}"
        return whereSql to params
    }

    private fun membershipAggregates(
        ids: List<String>,
        names: List<String>,
        roles: List<String>,
    ): List<PlatformUserOrganizationMembership> {
        require(ids.size == names.size && names.size == roles.size) { "Platform user membership aggregates are misaligned." }
        return ids.indices.map { index ->
            PlatformUserOrganizationMembership(UUID.fromString(ids[index]), names[index], roles[index])
        }
    }

    private fun splitAggregate(value: String?): List<String> =
        value
            ?.takeIf { it.isNotBlank() }
            ?.split("|||")
            ?: emptyList()
}
