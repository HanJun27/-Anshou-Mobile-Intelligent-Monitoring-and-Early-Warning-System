package com.livewell.model

data class CommunityHealthData(
    val userId: String,
    val timestamp: Long,
    val deviceStatus: DeviceStatus,
    val healthMetrics: HealthMetrics,
    val activityData: ActivityData,
    val alertEvents: List<AlertEvent>
)

data class DeviceStatus(
    val batteryLevel: Int,
    val isCharging: Boolean,
    val networkType: String,
    val signalStrength: Int,
    val appVersion: String,
    val systemVersion: String
)

data class HealthMetrics(
    val stepCount: Int,
    val sleepDuration: Int,      // 分钟
    val sleepQuality: String,     // poor, fair, good, excellent
    val heartRate: Int?,          // 可选
    val bloodOxygen: Int?,        // 可选
    val calorieBurn: Int
)

data class ActivityData(
    val screenOnTime: Int,        // 分钟
    val appUsageTime: Int,        // 分钟
    val lastActiveTime: Long,
    val isDeviceInUse: Boolean,
    val unlockCount: Int
)

data class AlertEvent(
    val alertId: String,
    val alertType: String,        // checkin, usage_time, step, sleep, emergency
    val alertLevel: String,       // safe, warning, danger
    val triggerTime: Long,
    val resolved: Boolean,
    val reason: String
)

// 位置信息（可选，需要权限）
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long
)