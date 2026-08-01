package com.leaguelift.actioncenter.web

import com.leaguelift.actioncenter.application.ActionCenterService
import com.leaguelift.actioncenter.domain.ActionCenter
import com.leaguelift.common.web.CurrentUser
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/me/action-center")
class ActionCenterController(private val service: ActionCenterService) {
    @GetMapping
    fun get(@AuthenticationPrincipal currentUser: CurrentUser): ActionCenter = service.get(currentUser)
}
