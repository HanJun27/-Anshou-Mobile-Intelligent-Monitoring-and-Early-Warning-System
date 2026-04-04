package com.livewell.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import jakarta.validation.constraints.Size

@Entity
@Table(
    name = "users",
    indexes = [
        Index(name = "idx_username", columnList = "username"),
        Index(name = "idx_status", columnList = "status")
    ]
)
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(nullable = false, unique = true, length = 50)
    @Size(min = 3, max = 50)
    val username: String,
    
    @Column(nullable = false, length = 255)
    var password: String,
    
    @Column(nullable = false, length = 100)
    val name: String,
    
    @Column(length = 20)
    val phone: String? = null,
    
    @Column(length = 100)
    val email: String? = null,
    
    @Column(length = 20)
    val avatarColor: String = "#1890ff",
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: UserStatus = UserStatus.ACTIVE,
    
    @Column(updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class UserStatus {
    ACTIVE, INACTIVE, BANNED
}