package com.livewell.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "alert_history",
    indexes = [
        Index(name = "idx_user_id", columnList = "user_id"),
        Index(name = "idx_type", columnList = "type"),
        Index(name = "idx_level", columnList = "level"),
        Index(name = "idx_created_at", columnList = "createdAt")
    ]
)
data class AlertHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,
    
    @Column(nullable = false, length = 50)
    val type: AlertType,
    
    @Column(nullable = false, length = 20)
    val level: AlertLevel,
    
    @Column(length = 500)
    val reason: String? = null,
    
    @Column(nullable = false)
    val resolved: Boolean = false,
    
    val resolvedAt: LocalDateTime? = null,
    
    @Column(updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class AlertType {
    CHECKIN, USAGE_TIME, STEP, SLEEP, EMERGENCY
}

enum class AlertLevel {
    SAFE, WARNING, DANGER
}