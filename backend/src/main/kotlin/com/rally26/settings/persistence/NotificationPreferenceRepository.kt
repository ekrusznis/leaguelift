package com.rally26.settings.persistence

import com.rally26.settings.domain.NotificationContactContext
import com.rally26.settings.domain.NotificationPreferenceState
import com.rally26.settings.domain.NotificationTopic
import com.rally26.settings.domain.SmsConsentState
import com.rally26.settings.domain.UserNotificationPreference
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class NotificationPreferenceRepository(
    private val jdbcClient: JdbcClient,
) {
    fun listForUser(userId: UUID): List<UserNotificationPreference> =
        jdbcClient
            .sql(
                """
                select user_id, topic, in_app_state, email_state, sms_state, created_at, updated_at
                from user_notification_preference
                where user_id = :userId
                order by topic
                """.trimIndent(),
            ).param("userId", userId)
            .query(::mapPreference)
            .list()

    fun find(
        userId: UUID,
        topic: NotificationTopic,
    ): UserNotificationPreference? =
        jdbcClient
            .sql(
                """
                select user_id, topic, in_app_state, email_state, sms_state, created_at, updated_at
                from user_notification_preference
                where user_id = :userId and topic = :topic
                """.trimIndent(),
            ).param("userId", userId)
            .param("topic", topic.name)
            .query(::mapPreference)
            .optional()
            .orElse(null)

    fun upsert(
        userId: UUID,
        topic: NotificationTopic,
        inAppState: NotificationPreferenceState,
        emailState: NotificationPreferenceState,
        smsState: NotificationPreferenceState,
    ): UserNotificationPreference {
        jdbcClient
            .sql(
                """
                insert into user_notification_preference
                    (user_id, topic, in_app_state, email_state, sms_state)
                values
                    (:userId, :topic, :inAppState, :emailState, :smsState)
                on conflict (user_id, topic)
                do update set
                    in_app_state = excluded.in_app_state,
                    email_state = excluded.email_state,
                    sms_state = excluded.sms_state,
                    updated_at = now()
                """.trimIndent(),
            ).param("userId", userId)
            .param("topic", topic.name)
            .param("inAppState", inAppState.name)
            .param("emailState", emailState.name)
            .param("smsState", smsState.name)
            .update()
        return requireNotNull(find(userId, topic)) { "Notification preference was unavailable after upsert." }
    }

    fun currentSmsConsent(userId: UUID): Boolean =
        jdbcClient
            .sql(
                """
                select state
                from user_sms_consent_event
                where user_id = :userId
                order by recorded_at desc, id desc
                limit 1
                """.trimIndent(),
            ).param("userId", userId)
            .query(String::class.java)
            .optional()
            .map { it == SmsConsentState.GRANTED.name }
            .orElse(false)

    fun insertSmsConsentEvent(
        userId: UUID,
        state: SmsConsentState,
    ) {
        jdbcClient
            .sql(
                """
                insert into user_sms_consent_event (id, user_id, state)
                values (:id, :userId, :state)
                """.trimIndent(),
            ).param("id", UUID.randomUUID())
            .param("userId", userId)
            .param("state", state.name)
            .update()
    }

    fun contactContext(
        userId: UUID,
        householdId: UUID?,
    ): NotificationContactContext? {
        if (householdId == null) {
            return jdbcClient
                .sql(
                    """
                    select nullif(trim(au.email), '') as email,
                           null::text as phone,
                           true as legacy_email_allowed
                    from app_user au
                    where au.id = :userId and au.status = 'ACTIVE'
                    """.trimIndent(),
                ).param("userId", userId)
                .query(::mapContactContext)
                .optional()
                .orElse(null)
        }
        return jdbcClient
            .sql(
                """
                select coalesce(nullif(trim(ha.email), ''), nullif(trim(h.contact_email), ''), nullif(trim(au.email), '')) as email,
                       coalesce(nullif(trim(ha.phone), ''), nullif(trim(h.contact_phone), '')) as phone,
                       not coalesce(h.email_reminders_opt_out, false) as legacy_email_allowed
                from app_user au
                left join guardian_relationship gr
                  on gr.user_id = au.id
                 and gr.household_id = :householdId
                 and gr.status = 'ACTIVE'
                left join household_adult ha
                  on ha.id = gr.household_adult_id
                 and ha.status = 'ACTIVE'
                left join household h
                  on h.id = gr.household_id
                 and h.status = 'ACTIVE'
                where au.id = :userId and au.status = 'ACTIVE'
                order by coalesce(ha.is_primary, false) desc, ha.created_at asc nulls last
                limit 1
                """.trimIndent(),
            ).param("userId", userId)
            .param("householdId", householdId)
            .query(::mapContactContext)
            .optional()
            .orElse(null)
    }

    private fun mapPreference(
        rs: java.sql.ResultSet,
        _rowNum: Int,
    ) = UserNotificationPreference(
        userId = rs.getObject("user_id", UUID::class.java),
        topic = NotificationTopic.valueOf(rs.getString("topic")),
        inAppState = NotificationPreferenceState.valueOf(rs.getString("in_app_state")),
        emailState = NotificationPreferenceState.valueOf(rs.getString("email_state")),
        smsState = NotificationPreferenceState.valueOf(rs.getString("sms_state")),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
    )

    private fun mapContactContext(
        rs: java.sql.ResultSet,
        _rowNum: Int,
    ) = NotificationContactContext(
        email = rs.getString("email"),
        phone = rs.getString("phone"),
        legacyEmailAllowed = rs.getBoolean("legacy_email_allowed"),
    )
}
