package com.rally26.platformadmin.config

import com.rally26.platformadmin.web.PlatformSupportAccessInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class PlatformAdminWebConfig(private val platformSupportAccessInterceptor: PlatformSupportAccessInterceptor) : WebMvcConfigurer {
	override fun addInterceptors(registry: InterceptorRegistry) {
		registry.addInterceptor(platformSupportAccessInterceptor)
			.addPathPatterns(
				"/api/v1/organizations/**",
				"/api/v1/teams/**",
				"/api/v1/tournaments/**",
				"/api/v1/households/**",
				"/api/v1/participants/**",
				"/api/v1/events/**",
			)
	}
}
