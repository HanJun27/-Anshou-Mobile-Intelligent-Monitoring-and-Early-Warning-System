package com.livewell.dto

import java.time.LocalDate
import java.time.LocalDateTime

data class SleepRecordDTO(
    val id: Long,
    val sleepDate: LocalDate,
    val bedtime: LocalDateTime?,
    val wakeupTime: LocalDateTime?,
    val sleepDuration: Int,
    val sleepQuality: String,
    val reachedTarget: Boolean
)

data class CreateSleepRecordRequest(
    val bedtime: LocalDateTime?,
    val wakeupTime: LocalDateTime?,
    val sleepDuration: Int,
    val sleepQuality: String,
    val deepSleepDuration: Int = 0,
    val lightSleepDuration: Int = 0,
    val awakeCount: Int = 0,
    val note: String? = null
)