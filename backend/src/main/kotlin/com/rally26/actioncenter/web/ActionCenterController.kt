package com.rally26.actioncenter.web

import com.rally26.actioncenter.application.ActionCenterService
import com.rally26.actioncenter.domain.ActionCenter
import com.rally26.common.web.CurrentUser
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/me/action-center")
class ActionCenterController(
    private val service: ActionCenterService,
) {
    @GetMapping
    fun get(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ActionCenter = service.get(currentUser)
}
