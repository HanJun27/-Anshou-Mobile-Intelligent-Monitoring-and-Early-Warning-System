package com.livewell.dto

import java.time.LocalDate

data class CommunityDailyReportDTO(
    val id: Long,
    val userId: Long,
    val reportDate: LocalDate,
    val stepCount: Int,
    val sleepDuration: Int,
    val sleepQuality: String,
    val screenOnTime: Int,
    val appUsageTime: Int,
    val checkinStatus: Boolean,
    val batteryLevel: Int,
    val createdAt: LocalDate
)

data class CreateCommunityReportRequest(
    val stepCount: Int,
    val sleepDuration: Int,
    val sleepQuality: String,
    val screenOnTime: Int,
    val appUsageTime: Int,
    val checkinStatus: Boolean,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val networkType: String,
    val calorieBurn: Int,
    val unlockCount: Int
)