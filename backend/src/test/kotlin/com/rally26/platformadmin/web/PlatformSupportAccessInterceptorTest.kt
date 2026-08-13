package com.rally26.platformadmin.web

import com.rally26.common.error.ForbiddenException
import com.rally26.common.web.CurrentUser
import com.rally26.config.CurrentUserAuthenticationToken
import com.rally26.platformadmin.application.PlatformAdminConsoleService
import com.rally26.platformadmin.domain.PlatformSupportAccess
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlatformSupportAccessInterceptorTest {
    private val service = mockk<PlatformAdminConsoleService>()
    private val interceptor = PlatformSupportAccessInterceptor(service)
    private val organizationId = UUID.randomUUID()
    private val accessId = UUID.randomUUID()
    private val admin = CurrentUser(UUID.randomUUID(), "employee@rally26.com", "Support Employee", platformAdministrator = true)

    @AfterTest
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `ordinary organization users are not subject to platform support sessions`() {
        SecurityContextHolder.getContext().authentication =
            CurrentUserAuthenticationToken(
                CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner"),
            )
        val request = MockHttpServletRequest("GET", "/api/v1/organizations/$organizationId/teams")

        assertTrue(interceptor.preHandle(request, MockHttpServletResponse(), Any()))
        verify(exactly = 0) { service.requireActiveSupportAccess(any(), any(), any()) }
    }

    @Test
    fun `platform admin organization request requires a support access header`() {
        authenticateAdmin()
        val request = MockHttpServletRequest("GET", "/api/v1/organizations/$organizationId/teams")

        assertFailsWith<ForbiddenException> {
            interceptor.preHandle(request, MockHttpServletResponse(), Any())
        }
    }

    @Test
    fun `platform admin organization request validates the reasoned session`() {
        authenticateAdmin()
        val request = MockHttpServletRequest("GET", "/api/v1/organizations/$organizationId/teams")
        request.addHeader(PLATFORM_SUPPORT_ACCESS_HEADER, accessId.toString())
        every { service.requireActiveSupportAccess(admin, accessId, organizationId) } returns mockk<PlatformSupportAccess>()

        assertTrue(interceptor.preHandle(request, MockHttpServletResponse(), Any()))
        verify(exactly = 1) { service.requireActiveSupportAccess(admin, accessId, organizationId) }
    }

    @Test
    fun `resource-first schedule request uses its organization query scope`() {
        authenticateAdmin()
        val teamId = UUID.randomUUID()
        val request = MockHttpServletRequest("GET", "/api/v1/teams/$teamId/events")
        request.addParameter("organizationId", organizationId.toString())
        request.addHeader(PLATFORM_SUPPORT_ACCESS_HEADER, accessId.toString())
        every { service.requireActiveSupportAccess(admin, accessId, organizationId) } returns mockk<PlatformSupportAccess>()

        assertTrue(interceptor.preHandle(request, MockHttpServletResponse(), Any()))
        verify(exactly = 1) { service.requireActiveSupportAccess(admin, accessId, organizationId) }
    }

    @Test
    fun `resource-first request cannot omit organization scope`() {
        authenticateAdmin()
        val request = MockHttpServletRequest("GET", "/api/v1/events/${UUID.randomUUID()}/rsvps")
        request.addHeader(PLATFORM_SUPPORT_ACCESS_HEADER, accessId.toString())

        assertFailsWith<ForbiddenException> {
            interceptor.preHandle(request, MockHttpServletResponse(), Any())
        }
    }

    @Test
    fun `platform admin cannot start a QuickBooks connection even with an active support session`() {
        authenticateAdmin()
        val request = MockHttpServletRequest("POST", "/api/v1/organizations/$organizationId/integrations/QUICKBOOKS_ONLINE/oauth/start")
        request.addHeader(PLATFORM_SUPPORT_ACCESS_HEADER, accessId.toString())

        assertFailsWith<ForbiddenException> {
            interceptor.preHandle(request, MockHttpServletResponse(), Any())
        }
        verify(exactly = 0) { service.requireActiveSupportAccess(any(), any(), any()) }
    }

    @Test
    fun `platform admin cannot disconnect an ICS feed even with an active support session`() {
        authenticateAdmin()
        val connectionId = UUID.randomUUID()
        val request = MockHttpServletRequest("DELETE", "/api/v1/organizations/$organizationId/event-source-connections/$connectionId")
        request.addHeader(PLATFORM_SUPPORT_ACCESS_HEADER, accessId.toString())

        assertFailsWith<ForbiddenException> {
            interceptor.preHandle(request, MockHttpServletResponse(), Any())
        }
    }

    @Test
    fun `platform admin cannot run a CSV schedule import even with an active support session`() {
        authenticateAdmin()
        val request = MockHttpServletRequest("POST", "/api/v1/organizations/$organizationId/events/csv-import")
        request.addHeader(PLATFORM_SUPPORT_ACCESS_HEADER, accessId.toString())

        assertFailsWith<ForbiddenException> {
            interceptor.preHandle(request, MockHttpServletResponse(), Any())
        }
    }

    @Test
    fun `platform admin can still view integration status with a valid support session`() {
        authenticateAdmin()
        val request = MockHttpServletRequest("GET", "/api/v1/organizations/$organizationId/integrations/quickbooks")
        request.addHeader(PLATFORM_SUPPORT_ACCESS_HEADER, accessId.toString())
        every { service.requireActiveSupportAccess(admin, accessId, organizationId) } returns mockk<PlatformSupportAccess>()

        assertTrue(interceptor.preHandle(request, MockHttpServletResponse(), Any()))
        verify(exactly = 1) { service.requireActiveSupportAccess(admin, accessId, organizationId) }
    }

    @Test
    fun `an org's own staff can still mutate their own integrations`() {
        SecurityContextHolder.getContext().authentication =
            CurrentUserAuthenticationToken(
                CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner"),
            )
        val request = MockHttpServletRequest("POST", "/api/v1/organizations/$organizationId/integrations/QUICKBOOKS_ONLINE/oauth/start")

        assertTrue(interceptor.preHandle(request, MockHttpServletResponse(), Any()))
    }

    private fun authenticateAdmin() {
        SecurityContextHolder.getContext().authentication = CurrentUserAuthenticationToken(admin)
    }
}
