package com.livewell.dto

import java.time.LocalDate

data class DashboardStats(
    val totalUsers: Long,
    val activeToday: Long,
    val pendingAlerts: Long,
    val successRate: Double,
    val totalAlerts: Long,
    val processedAlerts: Long
)

data class AlertTrend(
    val date: String,
    val checkin: Long,
    val sleep: Long,
    val usage: Long,
    val step: Long
)

data class UserStatusDistribution(
    val safe: Long,
    val warning: Long,
    val danger: Long
)

data class RecentActivity(
    val id: Long,
    val userId: Long,
    val userName: String,
    val type: String,
    val action: String,
    val timestamp: Long,
    val status: String
)

data class FocusUser(
    val id: Long,
    val name: String,
    val status: String,
    val type: String,
    val description: String,
    val typeColor: String,
    val avatarColor: String
)