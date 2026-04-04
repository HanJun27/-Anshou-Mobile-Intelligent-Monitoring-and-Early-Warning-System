package com.livewell.repository

import com.livewell.entity.SystemLog
import com.livewell.entity.LogType
import com.livewell.entity.LogLevel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface SystemLogRepository : JpaRepository<SystemLog, Long> {
    fun findByLogType(type: LogType): List<SystemLog>
    fun findByLevel(level: LogLevel): List<SystemLog>
    fun findByCreatedAtAfter(startTime: LocalDateTime): List<SystemLog>
    fun findByUserId(userId: Long): List<SystemLog>
}