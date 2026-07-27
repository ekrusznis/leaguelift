package com.leaguelift.publicpage.web

import com.leaguelift.publicpage.application.PublicPageService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/public")
class PublicController(private val publicPageService: PublicPageService) {

    @GetMapping("/pages/{slug}")
    fun getPage(@PathVariable slug: String): PublicPageResponse =
        publicPageService.getPublic(slug).toResponse()
}
