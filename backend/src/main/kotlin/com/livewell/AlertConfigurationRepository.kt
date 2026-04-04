package com.livewell.repository

import com.livewell.entity.AlertConfiguration
import com.livewell.entity.AlertType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AlertConfigurationRepository : JpaRepository<AlertConfiguration, Long> {
    fun findByUserId(userId: Long): List<AlertConfiguration>
    fun findByUserIdAndIsEnabled(userId: Long, isEnabled: Boolean): List<AlertConfiguration>
    fun findByUserIdAndAlertType(userId: Long, type: AlertType): AlertConfiguration?
}