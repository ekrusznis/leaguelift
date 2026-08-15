package com.rally26.fundraising.domain

import com.rally26.finance.domain.PaymentSource

data class ContributionListCriteria(
    val keyword: String? = null,
    val status: ContributionStatus? = null,
    val paymentSource: PaymentSource? = null,
    val sort: ContributionListSort = ContributionListSort.NEWEST,
)

enum class ContributionListSort {
    NEWEST,
    OLDEST,
    AMOUNT_DESC,
    AMOUNT_ASC,
    SUPPORTER_ASC,
}
