package com.ticketingplatform.backend.system

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfiguration {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers("/api/v1/system/health", "/actuator/health").permitAll()
                // TODO: Replace these temporary local-bootstrap endpoints with Keycloak resource-server authorization.
                it.requestMatchers(HttpMethod.POST, "/api/v1/organizations").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/v1/organizations/*/payment-profiles").permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/v1/organizations/*/payment-profiles/*").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/v1/organizations/*/events").permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/v1/organizations/*/events/*").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/v1/organizations/*/events/*/ticket-types").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/v1/organizations/*/events/*/sales/open").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/v1/organizations/*/events/*/reservations").permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/v1/organizations/*/events/*/reservations/*").permitAll()
                it.requestMatchers(HttpMethod.DELETE, "/api/v1/organizations/*/events/*/reservations/*").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/v1/organizations/*/events/*/orders").permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/v1/organizations/*/events/*/orders/*").permitAll()
                it.anyRequest().denyAll()
            }
            .build()
}
