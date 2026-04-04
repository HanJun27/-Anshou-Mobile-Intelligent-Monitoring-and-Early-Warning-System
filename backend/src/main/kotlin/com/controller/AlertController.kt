package com.livewell.controller

import com.livewell.dto.*
import com.livewell.entity.AlertHistory
import com.livewell.entity.AlertLevel
import com.livewell.entity.AlertType
import com.livewell.repository.AlertHistoryRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.ZoneOffset

@RestController
@RequestMapping("/alerts")
class AlertController(
    private val alertHistoryRepository: AlertHistoryRepository
) {
    
    @GetMapping("/history")
    fun getAlertHistory(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val alertsPage: Page<AlertHistory> = alertHistoryRepository.findAll(pageable)
        
        val alertItems = alertsPage.content.map { alert ->
            AlertItem(
                id = alert.id,
                userId = alert.user.id,
                userName = alert.user.name,
                type = alert.type.name,
                status = if (alert.resolved) "success" else "pending",
                method = "notification",
                content = alert.reason ?: "${alert.user.name}触发了${alert.type.name}警报",
                timestamp = alert.createdAt.toEpochSecond(ZoneOffset.UTC) * 1000
            )
        }
        
        val response = mapOf(
            "list" to alertItems,
            "total" to alertsPage.totalElements
        )
        
        return ResponseEntity.ok(ApiResponse.success(response))
    }
    
    @GetMapping("/realtime")
    fun getRealtimeAlerts(): ResponseEntity<ApiResponse<List<AlertItem>>> {
        val recentAlerts = alertHistoryRepository.findAll()
            .filter { !it.resolved }
            .take(10)
            .map { alert ->
                AlertItem(
                    id = alert.id,
                    userId = alert.user.id,
                    userName = alert.user.name,
                    type = alert.type.name,
                    status = "pending",
                    method = "notification",
                    content = alert.reason ?: "${alert.user.name}触发了${alert.type.name}警报",
                    timestamp = alert.createdAt.toEpochSecond(ZoneOffset.UTC) * 1000
                )
            }
        
        return ResponseEntity.ok(ApiResponse.success(recentAlerts))
    }
    
    @PostMapping("/{id}/handle")
    fun handleAlert(
        @PathVariable id: Long,
        @RequestBody request: HandleAlertRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        val alert = alertHistoryRepository.findById(id)
            .orElseThrow { RuntimeException("警报不存在") }
        
        when (request.action.lowercase()) {
            "resolve" -> {
                // 标记为已解决
                // TODO: 需要通过反射或其他方式更新实体类
            }
            "ignore" -> {
                // 忽略警报
            }
            else -> {
                throw RuntimeException("未知的操作：${request.action}")
            }
        }
        
        return ResponseEntity.ok(ApiResponse.success(Unit, "处理成功"))
    }
    
    @GetMapping("/stats")
    fun getAlertStats(): ResponseEntity<ApiResponse<AlertStats>> {
        val allAlerts = alertHistoryRepository.findAll()
        val total = allAlerts.size.toLong()
        val pending = allAlerts.count { !it.resolved }.toLong()
        val processed = total - pending
        val rate = if (total > 0) (processed.toDouble() / total) * 100 else 0.0
        
        val stats = AlertStats(
            total = total,
            pending = pending,
            processed = processed,
            rate = rate
        )
        
        return ResponseEntity.ok(ApiResponse.success(stats))
    }
}