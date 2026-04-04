package com.livewell.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "checkin_record",
    indexes = [
        Index(name = "idx_user_date", columnList = "user_id, checkinDate"),
        Index(name = "idx_valid", columnList = "isValid")
    ]
)
data class CheckinRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,
    
    @Column(nullable = false)
    val checkinDate: LocalDate,
    
    @Column(nullable = false)
    val checkinTime: LocalDateTime,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val checkinMethod: CheckinMethod = CheckinMethod.MANUAL,
    
    @Column(nullable = false)
    val isValid: Boolean = true,
    
    @Column(nullable = false)
    val consecutiveDays: Int = 1,
    
    @Column(nullable = false)
    val totalDays: Int = 1,
    
    @Column(length = 500)
    val note: String? = null,
    
    @Column(updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class CheckinMethod {
    MANUAL,
    AUTO,
    REMINDER
}