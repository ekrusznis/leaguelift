package com.rally26.system.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class StatusResponse(val status: String = "OK", val version: String = "0.1.0")

// Public, unauthenticated status endpoint — permitted in SecurityConfig via the /api/v1/public/** wildcard.
@RestController
@RequestMapping("/api/v1/public")
class StatusController {

	@GetMapping("/status")
	fun status(): StatusResponse = StatusResponse()
}
