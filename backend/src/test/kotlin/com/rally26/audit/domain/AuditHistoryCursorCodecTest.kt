package com.rally26.audit.domain

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class AuditHistoryCursorCodecTest {
    @Test
    fun `cursor round trips sort direction value time and id`() {
        val cursor =
            AuditHistoryCursor(
                sortBy = AuditHistorySortField.ACTION,
                direction = AuditHistorySortDirection.ASC,
                sortValue = "participant updated / résumé",
                createdAt = Instant.parse("2026-08-07T21:15:33.123456Z"),
                id = UUID.fromString("bdb25294-89fd-4f67-a4b6-2a749375611f"),
            )

        assertEquals(cursor, AuditHistoryCursorCodec.decode(AuditHistoryCursorCodec.encode(cursor)))
    }
}
