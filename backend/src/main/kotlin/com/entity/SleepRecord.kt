package com.livewell.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "sleep_record",
    indexes = [
        Index(name = "idx_user_date", columnList = "user_id, sleepDate"),
        Index(name = "idx_quality", columnList = "sleepQuality")
    ]
)
data class SleepRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,
    
    @Column(nullable = false)
    val sleepDate: LocalDate,
    
    val bedtime: LocalDateTime? = null,
    
    val wakeupTime: LocalDateTime? = null,
    
    @Column(nullable = false)
    val sleepDuration: Int = 0,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val sleepQuality: SleepQuality = SleepQuality.GOOD,
    
    @Column(nullable = false)
    val deepSleepDuration: Int = 0,
    
    @Column(nullable = false)
    val lightSleepDuration: Int = 0,
    
    @Column(nullable = false)
    val awakeCount: Int = 0,
    
    @Column(nullable = false)
    val reachedTarget: Boolean = true,
    
    @Column(nullable = false)
    val targetDuration: Int = 480,
    
    @Column(length = 500)
    val note: String? = null,
    
    @Column(updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class SleepQuality {
    POOR,
    FAIR,
    GOOD,
    EXCELLENT
}