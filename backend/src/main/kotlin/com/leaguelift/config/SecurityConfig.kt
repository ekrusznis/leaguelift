package com.leaguelift.config

import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Every protected endpoint requires an authenticated [com.leaguelift.common.web.CurrentUser].
 * Authorization (organization membership/role) is enforced in application services,
 * never here and never in React (DESIGN-DOC.md sections 7, 18.2).
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
	private val authProperties: AuthProperties,
	private val jwtCurrentUserConverter: JwtCurrentUserConverter,
	private val corsProperties: CorsProperties,
) {

	@Bean
	fun filterChain(
		http: HttpSecurity,
		localDevAuthenticationFilterProvider: ObjectProvider<LocalDevAuthenticationFilter>,
	): SecurityFilterChain {
		http
			.csrf { it.disable() }
			.cors { it.configurationSource(corsConfigurationSource()) }
			.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
			.authorizeHttpRequests { authorize ->
				authorize
					.requestMatchers("/api/v1/public/**").permitAll()
					.requestMatchers("/actuator/health/**").permitAll()
					.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
					.anyRequest().authenticated()
			}

		if (authProperties.devBypassEnabled) {
			val devFilter = localDevAuthenticationFilterProvider.ifAvailable
				?: error(
					"leaguelift.auth.dev-bypass-enabled=true but no LocalDevAuthenticationFilter " +
						"bean exists — it is @Profile(\"local\", \"test\")-gated. Refusing to start " +
						"rather than silently falling back to real JWT validation.",
				)
			http.addFilterBefore(devFilter, UsernamePasswordAuthenticationFilter::class.java)
		} else {
			http.oauth2ResourceServer { oauth2 ->
				oauth2.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtCurrentUserConverter) }
			}
		}

		return http.build()
	}

	private fun corsConfigurationSource(): CorsConfigurationSource {
		val configuration = CorsConfiguration().apply {
			allowedOrigins = corsProperties.allowedOrigins
			allowedMethods = listOf("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
			allowedHeaders = listOf("Authorization", "Content-Type", "X-Request-Id")
			exposedHeaders = listOf("X-Request-Id")
			allowCredentials = false
		}
		val source = UrlBasedCorsConfigurationSource()
		source.registerCorsConfiguration("/**", configuration)
		return source
	}
}
