package com.livewell.dto

import java.time.LocalDate
import java.time.LocalDateTime

data class CheckinRecordDTO(
    val id: Long,
    val checkinDate: LocalDate,
    val checkinTime: LocalDateTime,
    val checkinMethod: String,
    val isValid: Boolean,
    val consecutiveDays: Int,
    val totalDays: Int
)

data class CreateCheckinRequest(
    val checkinMethod: String = "MANUAL",
    val note: String? = null
)