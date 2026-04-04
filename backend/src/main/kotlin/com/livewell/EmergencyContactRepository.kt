package com.livewell.repository

import com.livewell.entity.EmergencyContact
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EmergencyContactRepository : JpaRepository<EmergencyContact, Long> {
    fun findByUserId(userId: Long): List<EmergencyContact>
    fun findByUserIdAndIsActive(userId: Long, isActive: Boolean): List<EmergencyContact>
    fun findTopByUserIdOrderByPriorityAsc(userId: Long): EmergencyContact?
}