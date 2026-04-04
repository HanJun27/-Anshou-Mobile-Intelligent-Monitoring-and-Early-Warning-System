package com.livewell.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {
    
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }  // 禁用 CSRF（API 不需要）
            .authorizeHttpRequests { auth ->
                auth.anyRequest().permitAll()  // 允许所有请求，无需认证
            }
        
        return http.build()
    }
}