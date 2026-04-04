package com.livewell.repository

import com.livewell.entity.CommunityDailyReport
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface CommunityDailyReportRepository : JpaRepository<CommunityDailyReport, Long> {
    fun findByUserId(userId: Long): List<CommunityDailyReport>
    fun findByUserIdAndReportDate(userId: Long, date: LocalDate): CommunityDailyReport?
    fun findByReportDate(date: LocalDate): List<CommunityDailyReport>
    fun findByUserIdAndReportDateBetween(
        userId: Long, 
        startDate: LocalDate, 
        endDate: LocalDate
    ): List<CommunityDailyReport>
}