package com.leaguelift.authorization.domain

/**
 * The seven context types from DESIGN-DOC.md section 4.2. A signed-in user holds zero
 * or more of these simultaneously (e.g. an org owner who is also a household guardian)
 * and, in the full design target, switches between them rather than between "apps".
 */
enum class ContextType {
	PERSONAL,
	ATHLETE,
	HOUSEHOLD,
	TEAM,
	ORGANIZATION,
	TOURNAMENT,
	PLATFORM_ADMIN,
}

/**
 * The narrower set of context types [com.leaguelift.authorization.domain.RoleAssignment]
 * rows actually persist. ORGANIZATION is deliberately excluded here — it is still
 * sourced from `organization_membership` (see ADR-020's "additive, not a rewrite"
 * decision), never from `role_assignment`. HOUSEHOLD/ATHLETE-as-persisted-rows use
 * PARTICIPANT (the athlete's own self-link) and a separate `guardian_relationship`
 * table respectively — see [com.leaguelift.authorization.domain.GuardianRelationship].
 */
enum class RoleAssignmentContextType {
	TEAM,
	TOURNAMENT,
	PLATFORM,
	PARTICIPANT,
}
