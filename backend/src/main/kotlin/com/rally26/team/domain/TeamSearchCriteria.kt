package com.rally26.team.domain

data class TeamSearchCriteria(
    val keyword: String? = null,
    val sport: String? = null,
    val season: String? = null,
    val genderCategory: TeamGenderCategory? = null,
    val status: TeamStatus? = null,
    val sort: TeamSearchSort = TeamSearchSort.NAME_ASC,
)

enum class TeamSearchSort {
    NAME_ASC,
    NAME_DESC,
    SPORT_ASC,
    NEWEST,
    OLDEST,
}
