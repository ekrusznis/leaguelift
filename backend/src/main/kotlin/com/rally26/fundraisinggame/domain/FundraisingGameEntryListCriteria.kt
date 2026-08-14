package com.rally26.fundraisinggame.domain

data class FundraisingGameEntryListCriteria(
    val keyword: String? = null,
    val winnerOnly: Boolean = false,
    val sort: FundraisingGameEntryListSort = FundraisingGameEntryListSort.NEWEST,
)

enum class FundraisingGameEntryListSort {
    NEWEST,
    OLDEST,
    NAME_ASC,
}
