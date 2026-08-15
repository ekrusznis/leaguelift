package com.rally26.event.domain

import java.time.Instant
import java.time.LocalDate

data class EventListCriteria(
    val keyword: String? = null,
    val eventType: EventType? = null,
    val status: EventStatus? = null,
    val from: Instant? = null,
    val to: Instant? = null,
    /**
     * Optional calendar-date boundaries for true all-day events. These are deliberately
     * separate from [from]/[to] so an all-day LocalDate is never timezone-converted.
     */
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
    val sort: EventListSort = EventListSort.DATE_ASC,
)

enum class EventListSort {
    DATE_ASC,
    DATE_DESC,
    TITLE_ASC,
    CREATED_DESC,
}
