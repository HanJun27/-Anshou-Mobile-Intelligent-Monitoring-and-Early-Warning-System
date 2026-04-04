package com.livewell.repository

import com.livewell.entity.StepStatistics
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface StepStatisticsRepository : JpaRepository<StepStatistics, Long> {
    fun findByUserId(userId: Long): List<StepStatistics>
    fun findByUserIdAndStatisticDate(userId: Long, date: LocalDate): StepStatistics?
    fun findByUserIdAndStatisticDateBetween(
        userId: Long, 
        startDate: LocalDate, 
        endDate: LocalDate
    ): List<StepStatistics>
    fun findByReachedTargetTrue(): List<StepStatistics>
}