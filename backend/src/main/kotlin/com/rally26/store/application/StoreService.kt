package com.rally26.store.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ConflictException
import com.rally26.common.error.FieldError
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.store.domain.Store
import com.rally26.store.domain.StoreStatus
import com.rally26.store.domain.isValidStoreSlug
import com.rally26.store.persistence.StoreRepository
import com.rally26.team.persistence.TeamRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class StoreService(
	private val storeRepository: StoreRepository,
	private val teamRepository: TeamRepository,
	private val membershipService: MembershipService,
	private val auditService: AuditService,
) {

	fun list(organizationId: UUID, currentUser: CurrentUser, offset: Int, limit: Int): List<Store> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return storeRepository.findAll(organizationId, offset, limit)
	}

	fun count(organizationId: UUID, currentUser: CurrentUser): Long {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return storeRepository.countAll(organizationId)
	}

	fun get(organizationId: UUID, storeId: UUID, currentUser: CurrentUser): Store {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return storeRepository.findById(storeId, organizationId)
			?: throw NotFoundException("STORE_NOT_FOUND", "The store could not be found.")
	}

	/** Only ACTIVE stores are publicly visible (mirrors CampaignService.getPublic). */
	fun getPublic(slug: String): Store {
		val store = storeRepository.findBySlug(slug)
			?: throw NotFoundException("STORE_NOT_FOUND", "The store could not be found.")
		if (store.status != StoreStatus.ACTIVE) {
			throw NotFoundException("STORE_NOT_FOUND", "The store could not be found.")
		}
		return store
	}

	@Transactional
	fun create(organizationId: UUID, teamId: UUID?, name: String, slug: String, currentUser: CurrentUser): Store {
		membershipService.requireManagerRole(organizationId, currentUser)
		if (!isValidStoreSlug(slug)) {
			throw ValidationException(
				"Slug must be lowercase alphanumeric with optional hyphens.",
				listOf(FieldError("slug", "Invalid slug format.")),
			)
		}
		if (teamId != null) {
			teamRepository.findById(teamId, organizationId)
				?: throw NotFoundException("TEAM_NOT_FOUND", "The team could not be found.")
		}
		return try {
			val store = storeRepository.insert(organizationId, teamId, name, slug)
			auditService.record(currentUser.userId, organizationId, "store.created", "store", store.id)
			store
		} catch (e: DuplicateKeyException) {
			throw ConflictException("STORE_SLUG_TAKEN", "This slug is already in use.")
		}
	}

	@Transactional
	fun updateStatus(organizationId: UUID, storeId: UUID, status: StoreStatus, currentUser: CurrentUser): Store {
		membershipService.requireManagerRole(organizationId, currentUser)
		storeRepository.findById(storeId, organizationId)
			?: throw NotFoundException("STORE_NOT_FOUND", "The store could not be found.")
		storeRepository.updateStatus(storeId, organizationId, status)
		auditService.record(currentUser.userId, organizationId, "store.status_updated", "store", storeId)
		return storeRepository.findById(storeId, organizationId)!!
	}
}
