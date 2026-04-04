package com.livewell.controller

import com.livewell.dto.*
import com.livewell.repository.AlertConfigurationRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/alert-configs")
class AlertConfigurationController(
    private val repository: AlertConfigurationRepository
) {
    
    @GetMapping("/user/{userId}")
    fun getUserAlertConfigs(
        @PathVariable userId: Long,
        @RequestParam(required = false) enabled: Boolean?
    ): ResponseEntity<List<AlertConfigurationDTO>> {
        val configs = if (enabled != null) {
            repository.findByUserIdAndIsEnabled(userId, enabled)
        } else {
            repository.findByUserId(userId)
        }.map { it.toDTO() }
        
        return ResponseEntity.ok(configs)
    }
    
    @PutMapping("/{id}")
    fun updateConfig(
        @PathVariable id: Long,
        @RequestBody request: UpdateAlertConfigurationRequest
    ): ResponseEntity<AlertConfigurationDTO> {
        val config = repository.findById(id)
            .orElseThrow { RuntimeException("配置不存在") }
        
        // TODO: 实现更新逻辑
        
        return ResponseEntity.ok(config.toDTO())
    }
    
    private fun com.livewell.entity.AlertConfiguration.toDTO(): AlertConfigurationDTO {
        return AlertConfigurationDTO(
            id = this.id,
            alertType = this.alertType.name,
            isEnabled = this.isEnabled,
            notifyMethod = this.notifyMethod.name,
            thresholdValue = this.thresholdValue,
            alertLevel = this.alertLevel.name,
            quietStartTime = this.quietStartTime,
            quietEndTime = this.quietEndTime,
            retryCount = this.retryCount,
            retryInterval = this.retryInterval,
            autoUpgradeTime = this.autoUpgradeTime
        )
    }
}