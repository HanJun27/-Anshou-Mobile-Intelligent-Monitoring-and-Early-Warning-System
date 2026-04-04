package com.livewell.repository

import com.livewell.entity.AlertHistory
import com.livewell.entity.AlertLevel
import com.livewell.entity.AlertType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface AlertHistoryRepository : JpaRepository<AlertHistory, Long> {
    fun findByUserId(userId: Long): List<AlertHistory>
    fun findByType(type: AlertType): List<AlertHistory>
    fun findByLevel(level: AlertLevel): List<AlertHistory>
    fun findByCreatedAtAfter(startTime: LocalDateTime): List<AlertHistory>
    fun findByUserIdAndType(userId: Long, type: AlertType): List<AlertHistory>
}