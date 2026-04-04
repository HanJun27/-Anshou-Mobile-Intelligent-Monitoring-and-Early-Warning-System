package com.livewell.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "alert_configuration",
    indexes = [
        Index(name = "idx_user_type", columnList = "user_id, alertType"),
        Index(name = "idx_enabled", columnList = "isEnabled")
    ]
)
data class AlertConfiguration(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val alertType: AlertType,
    
    @Column(nullable = false)
    val isEnabled: Boolean = true,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val notifyMethod: NotifyMethod = NotifyMethod.BOTH,
    
    @Column(nullable = false)
    val thresholdValue: Int = 0,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val alertLevel: AlertLevel = AlertLevel.WARNING,
    
    @Column(length = 10)
    val quietStartTime: String? = null,
    
    @Column(length = 10)
    val quietEndTime: String? = null,
    
    @Column(nullable = false)
    val retryCount: Int = 3,
    
    @Column(nullable = false)
    val retryInterval: Int = 5,
    
    @Column(nullable = false)
    val autoUpgradeTime: Int = 30,
    
    @Column(updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class NotifyMethod {
    SMS,
    EMAIL,
    NOTIFICATION,
    BOTH
}