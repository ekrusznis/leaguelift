package com.leaguelift.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Every protected endpoint requires an authenticated [com.leaguelift.common.web.CurrentUser],
 * resolved from a real, self-issued JWT (see `JwtConfig`/`JwtCurrentUserConverter`) in
 * every environment — there is no local/test authentication bypass. Local development
 * and the test suite authenticate the same way production does: register/log in via
 * `POST /api/v1/auth/register` or `/login` (or, for the seeded dashboard-role fixtures,
 * see `db/seed/V9000__dev_seed_dashboard_role_users.sql`). Authorization (organization
 * membership/role) is enforced in application services, never here and never in React
 * (DESIGN-DOC.md sections 7, 18.2).
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
	private val jwtCurrentUserConverter: JwtCurrentUserConverter,
	private val corsProperties: CorsProperties,
) {

	@Bean
	fun filterChain(http: HttpSecurity): SecurityFilterChain {
		http
			.csrf { it.disable() }
			.cors { it.configurationSource(corsConfigurationSource()) }
			.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
			.authorizeHttpRequests { authorize ->
				authorize
					.requestMatchers("/api/v1/public/**").permitAll()
					.requestMatchers("/api/v1/auth/**").permitAll()
					// Stripe calls this directly — no JWT, authenticity comes from the
					// Stripe-Signature header instead (verified in StripeWebhookController).
					.requestMatchers("/api/v1/webhooks/**").permitAll()
					.requestMatchers("/actuator/health/**").permitAll()
					.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
					.anyRequest().authenticated()
			}
			.oauth2ResourceServer { oauth2 ->
				oauth2.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtCurrentUserConverter) }
			}

		return http.build()
	}

	@Bean
	fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

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
