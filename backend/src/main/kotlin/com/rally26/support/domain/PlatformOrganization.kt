package com.rally26.support.domain

import java.util.UUID

/**
 * Media assets/assignments always carry a non-null `organization_id` foreign key
 * (DESIGN-DOC.md section 11.3) — every existing entity type that can hold media
 * belongs to a real organization. Help Center articles are the first entity type that
 * doesn't: they're platform-level content with no owning organization at all. Rather
 * than making organization_id nullable everywhere the media pipeline touches it, this
 * is a real, fixed-UUID seed row (see V85 migration) that article attachments are
 * scoped under — every other media query and constraint keeps working unmodified.
 */
object PlatformOrganization {
    val ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
}
