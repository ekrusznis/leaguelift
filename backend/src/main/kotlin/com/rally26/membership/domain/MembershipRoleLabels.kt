package com.rally26.membership.domain

/**
 * Human-readable labels for [MembershipRole], shared by anything that puts a role name
 * in front of an end user (Phase 8 slice 4) — currently the invitation and welcome
 * emails. Kept separate from [MembershipRole] itself so this presentation concern
 * doesn't leak into the enum the authorization layer treats as its source of truth.
 */
object MembershipRoleLabels {
    fun label(role: MembershipRole): String =
        when (role) {
            MembershipRole.OWNER -> "Owner"
            MembershipRole.ADMINISTRATOR -> "Administrator"
            MembershipRole.TEAM_ADMINISTRATOR -> "Team Administrator"
            MembershipRole.TOURNAMENT_ADMINISTRATOR -> "Tournament Administrator"
            MembershipRole.VIEWER -> "Member"
        }
}
