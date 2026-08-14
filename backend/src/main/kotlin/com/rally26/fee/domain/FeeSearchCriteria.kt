package com.rally26.fee.domain

data class FeeTemplateSearchCriteria(
    val keyword: String? = null,
    val status: FeeTemplateStatus? = FeeTemplateStatus.ACTIVE,
    val sort: FeeTemplateSearchSort = FeeTemplateSearchSort.NAME_ASC,
)

enum class FeeTemplateSearchSort {
    NAME_ASC,
    NAME_DESC,
    AMOUNT_ASC,
    AMOUNT_DESC,
    NEWEST,
    OLDEST,
}

data class FeeAssignmentSearchCriteria(
    val keyword: String? = null,
    val status: FeeAssignmentStatus? = null,
    val overdueOnly: Boolean = false,
    val sort: FeeAssignmentSearchSort = FeeAssignmentSearchSort.DUE_DATE_ASC,
)

enum class FeeAssignmentSearchSort {
    DUE_DATE_ASC,
    DUE_DATE_DESC,
    BALANCE_DESC,
    BALANCE_ASC,
    DESCRIPTION_ASC,
    HOUSEHOLD_ASC,
    NEWEST,
    OLDEST,
}
