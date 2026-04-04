package com.livewell.dto

import java.time.LocalDateTime

data class AlertHistoryDTO(
    val id: Long,
    val userId: Long,
    val type: String,
    val level: String,
    val reason: String?,
    val resolved: Boolean,
    val resolvedAt: LocalDateTime?,
    val createdAt: LocalDateTime
)

data class CreateAlertRequest(
    val userId: Long,
    val type: String,
    val level: String,
    val reason: String? = null
)

data class UpdateAlertStatusRequest(
    val resolved: Boolean,
    val resolvedAt: LocalDateTime? = null
)