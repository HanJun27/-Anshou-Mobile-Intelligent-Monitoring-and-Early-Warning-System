package com.livewell.repository

import com.livewell.entity.SleepRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface SleepRecordRepository : JpaRepository<SleepRecord, Long> {
    fun findByUserId(userId: Long): List<SleepRecord>
    fun findByUserIdAndSleepDate(userId: Long, date: LocalDate): SleepRecord?
    fun findByUserIdAndSleepDateBetween(
        userId: Long, 
        startDate: LocalDate, 
        endDate: LocalDate
    ): List<SleepRecord>
}