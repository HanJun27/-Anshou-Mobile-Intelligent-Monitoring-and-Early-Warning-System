package com.livewell.dto

data class HandleAlertRequest(
    val action: String
)

data class AlertItem(
    val id: Long,
    val userId: Long,
    val userName: String,
    val type: String,
    val status: String,
    val method: String,
    val content: String,
    val timestamp: Long
)

data class AlertStats(
    val total: Long,
    val pending: Long,
    val processed: Long,
    val rate: Double
)