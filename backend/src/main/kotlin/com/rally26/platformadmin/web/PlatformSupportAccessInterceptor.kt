package com.rally26.platformadmin.web

import com.rally26.common.error.ForbiddenException
import com.rally26.common.web.CurrentUser
import com.rally26.platformadmin.application.PlatformAdminConsoleService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.util.UUID

const val PLATFORM_SUPPORT_ACCESS_HEADER = "X-Rally26-Support-Access"
private val ORGANIZATION_PATH = Regex("^/api/v1/organizations/([0-9a-fA-F-]{36})(?:/.*)?$")
private val RESOURCE_SCOPED_PATH = Regex("^/api/v1/(?:teams|tournaments|households|participants|events)/.*$")

/**
 * Organization members continue to use the API normally. A Platform Admin is broader
 * than an org member and therefore must present the id of a reasoned support-access
 * session for the target organization on every customer-data request.
 *
 * Most APIs carry the organization UUID in the path. The event/schedule APIs use a
 * resource-first path and carry `organizationId` as a required query parameter; both
 * forms are covered so a Rally26 employee cannot bypass the reason/scope gate by
 * opening a team, tournament, household, participant, or RSVP deep link directly.
 */
@Component
class PlatformSupportAccessInterceptor(private val service: PlatformAdminConsoleService) : HandlerInterceptor {
	override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
		val currentUser = SecurityContextHolder.getContext().authentication?.principal as? CurrentUser ?: return true
		if (!currentUser.platformAdministrator) return true

		val organizationId = organizationIdFor(request) ?: return true
		val accessId = request.getHeader(PLATFORM_SUPPORT_ACCESS_HEADER)
			?.let(::parseUuid)
			?: throw ForbiddenException("SUPPORT_ACCESS_REQUIRED", "Start a support access session before opening organization data.")
		service.requireActiveSupportAccess(currentUser, accessId, organizationId)
		return true
	}

	private fun organizationIdFor(request: HttpServletRequest): UUID? {
		val pathOrganization = ORGANIZATION_PATH.matchEntire(request.requestURI)?.groupValues?.get(1)
		if (pathOrganization != null) {
			return parseUuid(pathOrganization)
				?: throw ForbiddenException("SUPPORT_ACCESS_SCOPE_INVALID", "The organization in this request is invalid.")
		}
		if (!RESOURCE_SCOPED_PATH.matches(request.requestURI)) return null
		return request.getParameter("organizationId")
			?.let(::parseUuid)
			?: throw ForbiddenException(
				"SUPPORT_ACCESS_SCOPE_REQUIRED",
				"Organization scope is required before a Platform Admin can open this resource.",
			)
	}

	private fun parseUuid(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()
}
