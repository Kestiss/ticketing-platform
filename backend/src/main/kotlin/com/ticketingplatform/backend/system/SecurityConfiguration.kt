package com.ticketingplatform.backend.system

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
                it.requestMatchers("POST", "/api/v1/organizations").permitAll()
                it.anyRequest().denyAll()
            }
            .build()
}
