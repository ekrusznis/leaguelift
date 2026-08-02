package com.leaguelift.store.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.store.domain.ManualVendor
import com.leaguelift.store.domain.ManualVendorStatus
import com.leaguelift.store.persistence.ManualVendorRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ManualVendorService(
	private val repository: ManualVendorRepository,
	private val membershipService: MembershipService,
	private val auditService: AuditService,
) {
	fun list(organizationId: UUID, includeArchived: Boolean, currentUser: CurrentUser): List<ManualVendor> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return repository.list(organizationId, includeArchived)
	}

	fun get(organizationId: UUID, vendorId: UUID, currentUser: CurrentUser): ManualVendor {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return requireVendor(organizationId, vendorId)
	}

	@Transactional
	fun create(
		organizationId: UUID,
		name: String,
		contactName: String?,
		contactEmail: String?,
		phone: String?,
		websiteUrl: String?,
		notes: String?,
		currentUser: CurrentUser,
	): ManualVendor {
		membershipService.requireManagerRole(organizationId, currentUser)
		val normalizedName = name.trim()
		requireUniqueActiveName(organizationId, normalizedName, null)
		val vendor = repository.insert(
			organizationId,
			normalizedName,
			contactName.normalized(),
			contactEmail.normalized(),
			phone.normalized(),
			websiteUrl.normalized(),
			notes.normalized(),
		)
		auditService.record(currentUser.userId, organizationId, "manual_vendor.created", "manual_vendor", vendor.id)
		return vendor
	}

	@Transactional
	fun update(
		organizationId: UUID,
		vendorId: UUID,
		name: String,
		contactName: String?,
		contactEmail: String?,
		phone: String?,
		websiteUrl: String?,
		notes: String?,
		currentUser: CurrentUser,
	): ManualVendor {
		membershipService.requireManagerRole(organizationId, currentUser)
		val existing = requireVendor(organizationId, vendorId)
		if (existing.status == ManualVendorStatus.ARCHIVED) throw ValidationException("Archived vendors cannot be edited.")
		val normalizedName = name.trim()
		requireUniqueActiveName(organizationId, normalizedName, vendorId)
		repository.update(
			vendorId,
			organizationId,
			normalizedName,
			contactName.normalized(),
			contactEmail.normalized(),
			phone.normalized(),
			websiteUrl.normalized(),
			notes.normalized(),
		)
		auditService.record(currentUser.userId, organizationId, "manual_vendor.updated", "manual_vendor", vendorId)
		return requireVendor(organizationId, vendorId)
	}

	@Transactional
	fun archive(organizationId: UUID, vendorId: UUID, currentUser: CurrentUser): ManualVendor {
		membershipService.requireManagerRole(organizationId, currentUser)
		requireVendor(organizationId, vendorId)
		repository.archive(vendorId, organizationId)
		auditService.record(currentUser.userId, organizationId, "manual_vendor.archived", "manual_vendor", vendorId)
		return requireVendor(organizationId, vendorId)
	}

	fun requireActiveVendor(organizationId: UUID, vendorId: UUID): ManualVendor {
		val vendor = requireVendor(organizationId, vendorId)
		if (vendor.status != ManualVendorStatus.ACTIVE) throw ValidationException("Choose an active manual vendor.")
		return vendor
	}

	private fun requireVendor(organizationId: UUID, vendorId: UUID): ManualVendor =
		repository.findById(vendorId, organizationId)
			?: throw NotFoundException("MANUAL_VENDOR_NOT_FOUND", "The manual vendor could not be found.")

	private fun requireUniqueActiveName(organizationId: UUID, name: String, currentId: UUID?) {
		val duplicate = repository.findActiveByName(organizationId, name)
		if (duplicate != null && duplicate.id != currentId) throw ValidationException("An active vendor with this name already exists.")
	}

	private fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
