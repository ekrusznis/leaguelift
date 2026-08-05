package com.rally26.platformadmin.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** Separate from MVC configuration to avoid a bean-creation cycle through the interceptor/service. */
@Configuration
class PlatformAdminClockConfig {
    @Bean
    fun platformAdminClock(): Clock = Clock.systemUTC()
}
