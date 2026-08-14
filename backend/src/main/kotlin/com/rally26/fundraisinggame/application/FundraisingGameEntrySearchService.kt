package com.rally26.fundraisinggame.application

import com.rally26.common.web.CurrentUser
import com.rally26.fundraisinggame.domain.FundraisingGameEntry
import com.rally26.fundraisinggame.domain.FundraisingGameEntryListCriteria
import com.rally26.fundraisinggame.persistence.FundraisingGameEntrySearchRepository
import com.rally26.fundraisinggame.persistence.FundraisingGameRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class FundraisingGameEntrySearchService(
    private val searchRepository: FundraisingGameEntrySearchRepository,
    private val repository: FundraisingGameRepository,
    private val gameService: FundraisingGameService,
) {
    fun search(
        organizationId: UUID,
        campaignId: UUID,
        criteria: FundraisingGameEntryListCriteria,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<FundraisingGameEntry> {
        val game = gameService.getForManagement(organizationId, campaignId, currentUser) ?: return emptyList()
        return searchRepository
            .searchIds(game.id, criteria, offset, limit)
            .mapNotNull { repository.findEntry(it, game.id) }
    }

    fun count(
        organizationId: UUID,
        campaignId: UUID,
        criteria: FundraisingGameEntryListCriteria,
        currentUser: CurrentUser,
    ): Long {
        val game = gameService.getForManagement(organizationId, campaignId, currentUser) ?: return 0
        return searchRepository.count(game.id, criteria)
    }
}
