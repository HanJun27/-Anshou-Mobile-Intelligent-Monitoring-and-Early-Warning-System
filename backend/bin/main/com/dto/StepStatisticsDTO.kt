package com.livewell.dto

import java.time.LocalDate

data class StepStatisticsDTO(
    val id: Long,
    val statisticDate: LocalDate,
    val totalSteps: Int,
    val targetSteps: Int,
    val achievementRate: Float,
    val reachedTarget: Boolean,
    val activeMinutes: Int,
    val caloriesBurned: Int
)

data class CreateStepStatisticsRequest(
    val totalSteps: Int,
    val targetSteps: Int = 6000,
    val activeMinutes: Int = 0,
    val caloriesBurned: Int = 0,
    val distanceKm: Float = 0f
)