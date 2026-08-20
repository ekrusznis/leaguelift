package com.rally26.onboarding.owner.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.onboarding.owner.domain.OwnerOnboarding
import com.rally26.onboarding.owner.domain.OwnerOnboardingStep
import com.rally26.organization.domain.Organization
import com.rally26.organization.domain.OrganizationStatus
import com.rally26.organization.domain.OrganizationType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val ONBOARDING_COLUMNS =
    "id, owner_user_id, organization_id, current_step, selected_plan_code, selected_billing_frequency, " +
        "checkout_session_id, checkout_started_at, completed_at, created_at, updated_at"

@Repository
class OwnerOnboardingRepository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper,
) {
    fun createForOwner(ownerUserId: UUID) {
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into owner_onboarding
                    (id, owner_user_id, current_step, terms_accepted_at, adult_confirmed_at, created_at, updated_at)
                values
                    (:id, :ownerUserId, 'ORGANIZATION', :now, :now, :now, :now)
                on conflict (owner_user_id) do nothing
                """.trimIndent(),
            ).param("id", UUID.randomUUID())
            .param("ownerUserId", ownerUserId)
            .param("now", Timestamp.from(now))
            .update()
    }

    fun findByOwnerUserId(ownerUserId: UUID): OwnerOnboarding? =
        jdbcClient
            .sql("select $ONBOARDING_COLUMNS from owner_onboarding where owner_user_id = :ownerUserId")
            .param("ownerUserId", ownerUserId)
            .query(::mapOnboarding)
            .optional()
            .orElse(null)

    fun findByOwnerUserIdForUpdate(ownerUserId: UUID): OwnerOnboarding? =
        jdbcClient
            .sql("select $ONBOARDING_COLUMNS from owner_onboarding where owner_user_id = :ownerUserId for update")
            .param("ownerUserId", ownerUserId)
            .query(::mapOnboarding)
            .optional()
            .orElse(null)

    fun insertDraftOrganization(
        name: String,
        slug: String,
        organizationType: OrganizationType,
        sports: List<String>,
        contactEmail: String,
        contactPhone: String?,
        addressLine1: String,
        addressLine2: String?,
        addressCity: String,
        addressState: String,
        addressPostalCode: String,
        addressCountry: String,
        timezone: String,
    ): Organization {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into organization
                    (id, name, slug, organization_type, status, sports, contact_email, contact_phone,
                     address_line1, address_line2, address_city, address_state, address_postal_code,
                     address_country, timezone, created_at, updated_at)
                values
                    (:id, :name, :slug, :organizationType, 'DRAFT', cast(:sports as jsonb), :contactEmail,
                     :contactPhone, :addressLine1, :addressLine2, :addressCity, :addressState,
                     :addressPostalCode, :addressCountry, :timezone, :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("name", name)
            .param("slug", slug)
            .param("organizationType", organizationType.name)
            .param("sports", objectMapper.writeValueAsString(sports))
            .param("contactEmail", contactEmail)
            .param("contactPhone", contactPhone)
            .param("addressLine1", addressLine1)
            .param("addressLine2", addressLine2)
            .param("addressCity", addressCity)
            .param("addressState", addressState)
            .param("addressPostalCode", addressPostalCode)
            .param("addressCountry", addressCountry)
            .param("timezone", timezone)
            .param("now", Timestamp.from(now))
            .update()
        return Organization(
            id = id,
            name = name,
            slug = slug,
            organizationType = organizationType,
            status = OrganizationStatus.DRAFT,
            sports = sports,
            contactEmail = contactEmail,
            contactPhone = contactPhone,
            createdAt = now,
            updatedAt = now,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            addressCity = addressCity,
            addressState = addressState,
            addressPostalCode = addressPostalCode,
            addressCountry = addressCountry,
            timezone = timezone,
        )
    }

    fun attachOrganization(
        onboardingId: UUID,
        organizationId: UUID,
    ) {
        jdbcClient
            .sql(
                """
                update owner_onboarding
                set organization_id = :organizationId, current_step = 'PLAN', updated_at = now()
                where id = :onboardingId and organization_id is null
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("onboardingId", onboardingId)
            .update()
    }

    fun selectPlan(
        onboardingId: UUID,
        planCode: String,
        billingFrequency: String,
    ) {
        jdbcClient
            .sql(
                """
                update owner_onboarding
                set selected_plan_code = :planCode,
                    selected_billing_frequency = :billingFrequency,
                    current_step = 'REVIEW',
                    updated_at = now()
                where id = :onboardingId
                """.trimIndent(),
            ).param("planCode", planCode)
            .param("billingFrequency", billingFrequency)
            .param("onboardingId", onboardingId)
            .update()
    }

    /** FREE-tier only — collapses PLAN/REVIEW/CHECKOUT into one hop straight to COMPLETE, since a FREE plan never goes through Stripe Checkout at all. */
    fun activateFreeOnboarding(
        onboardingId: UUID,
        planCode: String,
    ) {
        jdbcClient
            .sql(
                """
                update owner_onboarding
                set selected_plan_code = :planCode,
                    selected_billing_frequency = null,
                    current_step = 'COMPLETE',
                    completed_at = coalesce(completed_at, now()),
                    updated_at = now()
                where id = :onboardingId
                """.trimIndent(),
            ).param("planCode", planCode)
            .param("onboardingId", onboardingId)
            .update()
    }

    fun markCheckout(
        onboardingId: UUID,
        checkoutSessionId: String,
    ) {
        jdbcClient
            .sql(
                """
                update owner_onboarding
                set current_step = 'CHECKOUT', checkout_session_id = :checkoutSessionId,
                    checkout_started_at = now(), updated_at = now()
                where id = :onboardingId
                """.trimIndent(),
            ).param("checkoutSessionId", checkoutSessionId)
            .param("onboardingId", onboardingId)
            .update()
    }

    fun markCompleteForOrganization(organizationId: UUID) {
        jdbcClient
            .sql(
                """
                update owner_onboarding
                set current_step = 'COMPLETE', completed_at = coalesce(completed_at, now()), updated_at = now()
                where organization_id = :organizationId
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .update()
    }

    fun activateOrganization(organizationId: UUID) {
        jdbcClient
            .sql("update organization set status = 'ACTIVE', updated_at = now() where id = :organizationId and status <> 'ARCHIVED'")
            .param("organizationId", organizationId)
            .update()
    }

    fun suspendActivatedOrganization(organizationId: UUID) {
        jdbcClient
            .sql("update organization set status = 'SUSPENDED', updated_at = now() where id = :organizationId and status = 'ACTIVE'")
            .param("organizationId", organizationId)
            .update()
    }

    private fun mapOnboarding(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): OwnerOnboarding =
        OwnerOnboarding(
            id = rs.getObject("id", UUID::class.java),
            ownerUserId = rs.getObject("owner_user_id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            currentStep = OwnerOnboardingStep.valueOf(rs.getString("current_step")),
            selectedPlanCode = rs.getString("selected_plan_code"),
            selectedBillingFrequency = rs.getString("selected_billing_frequency"),
            checkoutSessionId = rs.getString("checkout_session_id"),
            checkoutStartedAt = rs.getTimestamp("checkout_started_at")?.toInstant(),
            completedAt = rs.getTimestamp("completed_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
