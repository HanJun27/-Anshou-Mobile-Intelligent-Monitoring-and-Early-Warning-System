package com.livewell.repository

import com.livewell.entity.CheckinRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface CheckinRecordRepository : JpaRepository<CheckinRecord, Long> {
    fun findByUserId(userId: Long): List<CheckinRecord>
    fun findByUserIdAndCheckinDate(userId: Long, date: LocalDate): CheckinRecord?
    fun findByUserIdAndIsValid(userId: Long, isValid: Boolean): List<CheckinRecord>
    fun findTopByUserIdOrderByCheckinDateDesc(userId: Long): CheckinRecord?
}