package com.livewell.controller

import com.livewell.entity.AlertHistory
import com.livewell.entity.AlertLevel
import com.livewell.entity.AlertType
import com.livewell.repository.AlertHistoryRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/alerts")
class AlertController(
    private val alertHistoryRepository: AlertHistoryRepository
) {
    
    @GetMapping("/history")
    fun getAlertHistory(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<Page<AlertHistory>> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        return ResponseEntity.ok(alertHistoryRepository.findAll(pageable))
    }
    
    @GetMapping("/realtime")
    fun getRealtimeAlerts(): ResponseEntity<List<AlertHistory>> {
        val recentAlerts = alertHistoryRepository.findAll()
            .filter { !it.resolved }
            .take(10)
        return ResponseEntity.ok(recentAlerts)
    }
}