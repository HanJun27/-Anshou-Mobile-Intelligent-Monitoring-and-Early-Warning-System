package com.livewell.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "community_daily_report",
    indexes = [
        Index(name = "idx_user_date", columnList = "user_id, reportDate"),
        Index(name = "idx_created_at", columnList = "createdAt")
    ]
)
data class CommunityDailyReport(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,
    
    @Column(nullable = false)
    val reportDate: LocalDate,
    
    @Column(nullable = false)
    val batteryLevel: Int = 0,
    
    @Column(nullable = false)
    val isCharging: Boolean = false,
    
    @Column(nullable = false, length = 50)
    val networkType: String = "unknown",
    
    @Column(length = 50)
    val appVersion: String = "1.0.0",
    
    @Column(nullable = false)
    val stepCount: Int = 0,
    
    @Column(nullable = false)
    val sleepDuration: Int = 0,
    
    @Column(nullable = false, length = 20)
    val sleepQuality: String = "good",
    
    @Column(nullable = false)
    val calorieBurn: Int = 0,
    
    @Column(nullable = false)
    val screenOnTime: Int = 0,
    
    @Column(nullable = false)
    val appUsageTime: Int = 0,
    
    @Column(nullable = false)
    val unlockCount: Int = 0,
    
    @Column(nullable = false)
    val checkinStatus: Boolean = false,
    
    val checkinTime: LocalDateTime? = null,
    
    @Column(updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)