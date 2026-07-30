package com.leaguelift.activity.web

import com.leaguelift.activity.application.ActivityFeedService
import com.leaguelift.common.web.CurrentUser
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class ActivityController(private val activityFeedService: ActivityFeedService) {

	@GetMapping("/me/activity")
	fun myActivity(@AuthenticationPrincipal currentUser: CurrentUser): ActivityFeedResponse =
		ActivityFeedResponse(activityFeedService.getFeed(currentUser))
}
