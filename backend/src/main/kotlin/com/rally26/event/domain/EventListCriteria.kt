package com.rally26.event.domain

import java.time.Instant

data class EventListCriteria(
    val keyword: String? = null,
    val eventType: EventType? = null,
    val status: EventStatus? = null,
    val from: Instant? = null,
    val to: Instant? = null,
    val sort: EventListSort = EventListSort.DATE_ASC,
)

enum class EventListSort {
    DATE_ASC,
    DATE_DESC,
    TITLE_ASC,
    CREATED_DESC,
}
