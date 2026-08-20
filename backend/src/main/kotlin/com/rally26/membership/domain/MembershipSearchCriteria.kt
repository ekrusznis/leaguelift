package com.rally26.membership.domain

data class MembershipSearchCriteria(
    val keyword: String? = null,
    val role: MembershipRole? = null,
    val status: MembershipStatus? = null,
    val sort: MembershipSearchSort = MembershipSearchSort.NAME_ASC,
)

enum class MembershipSearchSort { NAME_ASC, NAME_DESC, ROLE_ASC, NEWEST, OLDEST }

/** [MembershipRepository.search]'s result row — `organization_membership` carries no email/display name, those live on `app_user`. */
data class MembershipSearchRow(
    val membership: OrganizationMembership,
    val userEmail: String?,
    val userDisplayName: String?,
)
