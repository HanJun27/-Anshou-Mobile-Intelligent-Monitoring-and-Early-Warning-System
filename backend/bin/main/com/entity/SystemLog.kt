package com.livewell.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "system_log",
    indexes = [
        Index(name = "idx_user_type", columnList = "user_id, logType"),
        Index(name = "idx_level", columnList = "level"),
        Index(name = "idx_created_at", columnList = "createdAt")
    ]
)
data class SystemLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User? = null,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val logType: LogType,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val level: LogLevel = LogLevel.INFO,
    
    @Column(nullable = false, length = 500)
    val message: String,
    
    @Column(length = 2000)
    val details: String? = null,
    
    @Column(length = 50)
    val ipAddress: String? = null,
    
    @Column(length = 200)
    val deviceInfo: String? = null,
    
    @Column(updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class LogType {
    LOGIN,
    LOGOUT,
    CHECKIN,
    ALERT_TRIGGER,
    DATA_SYNC,
    SYSTEM_ERROR,
    USER_ACTION
}

enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}