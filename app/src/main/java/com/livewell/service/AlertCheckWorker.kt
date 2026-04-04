package com.livewell.service

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.livewell.untils.PrefsManager
import com.livewell.untils.UsageStatsHelper
import java.text.SimpleDateFormat
import java.util.*
import com.livewell.untils.SystemStepManager

class AlertCheckWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    private val prefsManager = PrefsManager(applicationContext)
    private val usageStatsHelper = UsageStatsHelper(applicationContext)

    override fun doWork(): Result {
        return try {
            performSafetyCheck()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun performSafetyCheck() {
        // ✅ 修复：使用今日使用时长（而不是开机时间）
        val todayUsage = usageStatsHelper.getTodayAppUsageMinutes()
        val usageThreshold = prefsManager.getAppUsageThreshold()

        val stepCount = getTodayStepCount()
        val stepThreshold = prefsManager.getStepThreshold()

        val usageAbnormal = todayUsage < usageThreshold
        val stepAbnormal = prefsManager.isStepMonitorEnabled() && stepCount < stepThreshold

        // 自动报警模式检查
        if (prefsManager.isAutoAlertModeEnabled()) {
            val criteria = prefsManager.getAlertCriteria()
            val shouldAlert = when (criteria) {
                PrefsManager.CRITERIA_USAGE_ONLY -> usageAbnormal
                PrefsManager.CRITERIA_STEP_ONLY -> stepAbnormal
                PrefsManager.CRITERIA_MIXED -> usageAbnormal || stepAbnormal
                else -> false
            }
            
            if (shouldAlert) {
                triggerAlert()
            }
            return
        }

        // 默认模式：检查签到 + 使用时长
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastCheckin = prefsManager.getLastCheckinDate()

        if (lastCheckin != today && (usageAbnormal || stepAbnormal)) {
            triggerAlert()
        }
    }

    private fun getTodayStepCount(): Int {
    val stepManager = SystemStepManager.getInstance(applicationContext)
    
    if (stepManager.hasStepCounter()) {
        return try {
            // Deleted:kotlinx.coroutines.runBlocking {  // ❌ 移除阻塞
            stepManager.getTodaySteps()  // ✅ 直接同步读取
            // Deleted:}
        } catch (e: Exception) {
            getSimulatedStepCount()
        }
    }
    
    return getSimulatedStepCount()
}

    private fun triggerAlert() {
        val intent = android.content.Intent(applicationContext, CheckinService::class.java).apply {
            action = "TRIGGER_ALERT"
        }
        applicationContext.startService(intent)
    }


    private fun getSimulatedStepCount(): Int {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    return if (hour >= 22 || hour <= 6) {
        (10..50).random()
    } else {
        (500..2000).random()
    }
}

}