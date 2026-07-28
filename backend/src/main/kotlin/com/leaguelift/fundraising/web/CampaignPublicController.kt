package com.leaguelift.fundraising.web

import com.leaguelift.fundraising.application.CampaignService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/public/campaigns")
class CampaignPublicController(private val campaignService: CampaignService) {

    @GetMapping("/{slug}")
    fun getCampaign(@PathVariable slug: String): PublicCampaignResponse =
        campaignService.getPublic(slug).toPublicResponse()
}
