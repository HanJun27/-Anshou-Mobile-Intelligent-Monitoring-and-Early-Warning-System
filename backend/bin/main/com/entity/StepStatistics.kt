package com.livewell.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "step_statistics",
    indexes = [
        Index(name = "idx_user_date", columnList = "user_id, statisticDate"),
        Index(name = "idx_reached", columnList = "reachedTarget")
    ]
)
data class StepStatistics(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,
    
    @Column(nullable = false)
    val statisticDate: LocalDate,
    
    @Column(nullable = false)
    val totalSteps: Int = 0,
    
    @Column(nullable = false)
    val targetSteps: Int = 6000,
    
    @Column(nullable = false)
    val achievementRate: Float = 0f,
    
    @Column(nullable = false)
    val activeMinutes: Int = 0,
    
    @Column(nullable = false)
    val caloriesBurned: Int = 0,
    
    @Column(nullable = false)
    val distanceKm: Float = 0f,
    
    @Column(nullable = false)
    val reachedTarget: Boolean = false,
    
    @Column(nullable = false)
    val consecutiveDays: Int = 0,
    
    @Column(updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)