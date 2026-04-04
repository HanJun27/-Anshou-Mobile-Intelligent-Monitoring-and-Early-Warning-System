package com.livewell.dto

data class AlertConfigurationDTO(
    val id: Long,
    val alertType: String,
    val isEnabled: Boolean,
    val notifyMethod: String,
    val thresholdValue: Int,
    val alertLevel: String,
    val quietStartTime: String?,
    val quietEndTime: String?,
    val retryCount: Int,
    val retryInterval: Int,
    val autoUpgradeTime: Int
)

data class UpdateAlertConfigurationRequest(
    val isEnabled: Boolean? = null,
    val notifyMethod: String? = null,
    val thresholdValue: Int? = null,
    val alertLevel: String? = null,
    val quietStartTime: String? = null,
    val quietEndTime: String? = null,
    val retryCount: Int? = null,
    val retryInterval: Int? = null,
    val autoUpgradeTime: Int? = null
)