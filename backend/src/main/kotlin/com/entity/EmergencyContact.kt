package com.livewell.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "emergency_contact",
    indexes = [
        Index(name = "idx_user_id", columnList = "user_id"),
        Index(name = "idx_is_active", columnList = "isActive")
    ]
)
data class EmergencyContact(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,
    
    @Column(nullable = false, length = 100)
    val name: String,
    
    @Column(nullable = false, length = 20)
    val phoneNumber: String,
    
    @Column(length = 100)
    val relation: String = "",
    
    @Column(length = 200)
    val email: String = "",
    
    @Column(nullable = false)
    val priority: Int = 1,
    
    @Column(nullable = false)
    val isActive: Boolean = true,
    
    val lastNotifiedAt: LocalDateTime? = null,
    
    @Column(updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    var updatedAt: LocalDateTime = LocalDateTime.now()
)