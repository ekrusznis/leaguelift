package com.rally26.household.domain

import java.util.UUID

data class HouseholdSearchCriteria(
    val keyword: String? = null,
    val status: HouseholdStatus? = null,
    val teamId: UUID? = null,
    val sort: HouseholdSearchSort = HouseholdSearchSort.NAME_ASC,
)

enum class HouseholdSearchSort {
    NAME_ASC,
    NAME_DESC,
    NEWEST,
    OLDEST,
}
