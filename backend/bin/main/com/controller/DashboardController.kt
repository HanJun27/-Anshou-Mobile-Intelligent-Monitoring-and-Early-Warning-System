package com.livewell.controller

import com.livewell.repository.AlertHistoryRepository
import com.livewell.repository.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/dashboard")
class DashboardController(
    private val userRepository: UserRepository,
    private val alertHistoryRepository: AlertHistoryRepository
) {
    
    @GetMapping
    fun getDashboardData(): ResponseEntity<Map<String, Any>> {
        val totalUsers = userRepository.count()
        val activeUsers = userRepository.findAll().count { it.status.name == "ACTIVE" }
        val totalAlerts = alertHistoryRepository.count()
        val unresolvedAlerts = alertHistoryRepository.findAll().count { !it.resolved }
        
        return ResponseEntity.ok(mapOf(
            "totalUsers" to totalUsers,
            "activeUsers" to activeUsers,
            "totalAlerts" to totalAlerts,
            "unresolvedAlerts" to unresolvedAlerts,
            "lastUpdateTime" to LocalDateTime.now().toString()
        ))
    }
}