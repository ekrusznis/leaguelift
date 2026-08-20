package com.rally26.sponsorship.domain

data class SponsorshipPackageSearchCriteria(
    val keyword: String? = null,
    val status: SponsorshipPackageStatus? = null,
    val exclusive: Boolean? = null,
    val sort: SponsorshipPackageSearchSort = SponsorshipPackageSearchSort.NEWEST,
)

enum class SponsorshipPackageSearchSort { NEWEST, OLDEST, NAME_ASC, NAME_DESC, PRICE_ASC, PRICE_DESC, SPONSORS_DESC }

/** [SponsorshipPackageRepository.search]'s result row — confirmed count is computed in the same query rather than N+1'd per item. */
data class SponsorshipPackageSearchRow(
    val sponsorshipPackage: SponsorshipPackage,
    val confirmedCount: Long,
)
