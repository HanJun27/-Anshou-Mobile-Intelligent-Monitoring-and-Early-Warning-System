package com.livewell.controller

import com.livewell.dto.*
import com.livewell.entity.AlertType
import com.livewell.repository.AlertHistoryRepository
import com.livewell.repository.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneOffset

@RestController
@RequestMapping("/dashboard")
class DashboardController(
    private val userRepository: UserRepository,
    private val alertHistoryRepository: AlertHistoryRepository
) {
    
    @GetMapping("/stats")
    fun getDashboardStats(): ResponseEntity<ApiResponse<DashboardStats>> {
        val totalUsers = userRepository.count()
        val activeUsers = userRepository.findAll().count { it.status.name == "ACTIVE" }.toLong()
        val totalAlerts = alertHistoryRepository.count()
        val unresolvedAlerts = alertHistoryRepository.findAll().count { !it.resolved }.toLong()
        val processedAlerts = totalAlerts - unresolvedAlerts
        val successRate = if (totalAlerts > 0) (processedAlerts.toDouble() / totalAlerts) * 100 else 0.0
        
        val stats = DashboardStats(
            totalUsers = totalUsers,
            activeToday = activeUsers,
            pendingAlerts = unresolvedAlerts,
            successRate = successRate,
            totalAlerts = totalAlerts,
            processedAlerts = processedAlerts
        )
        
        return ResponseEntity.ok(ApiResponse.success(stats))
    }
    
    @GetMapping("/alert-trend")
    fun getAlertTrend(
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?
    ): ResponseEntity<ApiResponse<List<AlertTrend>>> {
        val start = startDate?.let { LocalDate.parse(it) } ?: LocalDate.now().minusDays(7)
        val end = endDate?.let { LocalDate.parse(it) } ?: LocalDate.now()
        
        val trendData = mutableListOf<AlertTrend>()
        var current = start
        
        while (!current.isAfter(end)) {
            val checkinCount = alertHistoryRepository.findAll()
                .count { it.type == AlertType.CHECKIN && it.createdAt.toLocalDate() == current }.toLong()
            val sleepCount = alertHistoryRepository.findAll()
                .count { it.type == AlertType.SLEEP && it.createdAt.toLocalDate() == current }.toLong()
            val usageCount = alertHistoryRepository.findAll()
                .count { it.type == AlertType.USAGE_TIME && it.createdAt.toLocalDate() == current }.toLong()
            val stepCount = alertHistoryRepository.findAll()
                .count { it.type == AlertType.STEP && it.createdAt.toLocalDate() == current }.toLong()
            
            trendData.add(
                AlertTrend(
                    date = current.toString(),
                    checkin = checkinCount,
                    sleep = sleepCount,
                    usage = usageCount,
                    step = stepCount
                )
            )
            current = current.plusDays(1)
        }
        
        return ResponseEntity.ok(ApiResponse.success(trendData))
    }
    
    @GetMapping("/user-status")
    fun getUserStatusDistribution(): ResponseEntity<ApiResponse<UserStatusDistribution>> {
        val allUsers = userRepository.findAll()
        val safe = allUsers.count { it.status.name == "ACTIVE" }.toLong()
        val warning = allUsers.count { it.status.name == "INACTIVE" }.toLong()
        val danger = allUsers.count { it.status.name == "BANNED" }.toLong()
        
        val distribution = UserStatusDistribution(
            safe = safe,
            warning = warning,
            danger = danger
        )
        
        return ResponseEntity.ok(ApiResponse.success(distribution))
    }
    
    @GetMapping("/activities")
    fun getRecentActivities(
        @RequestParam(required = false) limit: Int?
    ): ResponseEntity<ApiResponse<List<RecentActivity>>> {
        val activities = alertHistoryRepository.findAll()
            .sortedByDescending { it.createdAt }
            .take(limit ?: 10)
            .map { alert ->
                RecentActivity(
                    id = alert.id,
                    userId = alert.user.id,
                    userName = alert.user.name,
                    type = alert.type.name,
                    action = "${alert.user.name}触发了${alert.type.name}警报",
                    timestamp = alert.createdAt.toEpochSecond(ZoneOffset.UTC) * 1000,
                    status = if (alert.resolved) "normal" else "warning"
                )
            }
        
        return ResponseEntity.ok(ApiResponse.success(activities))
    }
    
    @GetMapping("/focus-users")
    fun getFocusUsers(): ResponseEntity<ApiResponse<List<FocusUser>>> {
        val focusUsers = userRepository.findAll()
            .filter { it.status.name != "ACTIVE" }
            .take(5)
            .map { user ->
                FocusUser(
                    id = user.id,
                    name = user.name,
                    status = "warning",
                    type = "重点关注",
                    description = "${user.name}连续 3 天未签到",
                    typeColor = "orange",
                    avatarColor = user.avatarColor
                )
            }
        
        return ResponseEntity.ok(ApiResponse.success(focusUsers))
    }
    
    @GetMapping
    fun getDashboardData(): ResponseEntity<ApiResponse<Map<String, Any>>> {
        val totalUsers = userRepository.count()
        val activeUsers = userRepository.findAll().count { it.status.name == "ACTIVE" }.toLong()
        val totalAlerts = alertHistoryRepository.count()
        val unresolvedAlerts = alertHistoryRepository.findAll().count { !it.resolved }.toLong()
        
        return ResponseEntity.ok(
            ApiResponse.success(
                mapOf(
                    "totalUsers" to totalUsers,
                    "activeUsers" to activeUsers,
                    "totalAlerts" to totalAlerts,
                    "unresolvedAlerts" to unresolvedAlerts,
                    "lastUpdateTime" to java.time.LocalDateTime.now().toString()
                )
            )
        )
    }
}