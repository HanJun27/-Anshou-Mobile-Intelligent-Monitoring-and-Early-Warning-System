package com.livewell.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.livewell.MainActivity
import com.livewell.R
import com.livewell.model.*
import com.livewell.untils.PrefsManager
import com.livewell.untils.SecurePrefsManager
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ... existing code ...

class CommunityReportService : Service() {
    
    private lateinit var prefsManager: PrefsManager
    private lateinit var securePrefs: SecurePrefsManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tag = "CommunityReportService"
    
    companion object {
        private const val NOTIFICATION_ID = 3001
        private const val CHANNEL_ID = "community_report_channel"
        private const val REPORT_INTERVAL_MINUTES: Long = 30L  // 每 30 分钟上报一次
        
        fun start(context: Context) {
            val intent = Intent(context, CommunityReportService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        prefsManager = PrefsManager(this)
        securePrefs = SecurePrefsManager(this)
        
        createNotificationChannel()
        
        startPeriodicReporting()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showNotification()
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "社区数据上报",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "定期向社区平台上报健康数据"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun showNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("安守")
            .setContentText("社区守护模式运行中")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun startPeriodicReporting() {
        serviceScope.launch {
            while (isActive) {
                try {
                    collectAndReportData()
                    delay(TimeUnit.MINUTES.toMillis(REPORT_INTERVAL_MINUTES))
                } catch (e: Exception) {
                    Log.e(tag, "数据上报失败：${e.message}")
                    delay(TimeUnit.MINUTES.toMillis(5))  // 失败后 5 分钟重试
                }
            }
        }
    }
    
    /**
     * 收集数据并上报到社区平台
     */
    private suspend fun collectAndReportData() {
    withContext(Dispatchers.IO) {  // ✅ 改为 IO 调度器
        // 检查是否启用了社区守护模式
        if (!prefsManager.isCommunityEnabled()) {
            Log.d(tag, "社区守护模式未启用")
            return@withContext
        }
        
        // 收集所有数据（现在在后台线程执行）
        val healthData = buildHealthData()
        
        // 发送到社区后端（网络请求）
        sendToCommunityBackend(healthData)
    }
}
    
    /**
     * 构建完整的健康数据包
     */
    private fun buildHealthData(): CommunityHealthData {
        val userId = prefsManager.getCommunityUserId()
        val currentTime = System.currentTimeMillis()
        
        // 设备状态
        val deviceStatus = DeviceStatus(
            batteryLevel = getBatteryLevel(),
            isCharging = isDeviceCharging(),
            networkType = getNetworkType(),
            signalStrength = getSignalStrength(),
            appVersion = getAppVersion(),
            systemVersion = Build.VERSION.RELEASE
        )
        
        // 健康指标
        val healthMetrics = HealthMetrics(
            stepCount = getTodaySteps(),
            sleepDuration = getSleepDuration(),
            sleepQuality = estimateSleepQuality(),
            heartRate = null,  // 预留
            bloodOxygen = null,  // 预留
            calorieBurn = calculateCalorieBurn()
        )
        
        // 活动数据
        val activityData = ActivityData(
            screenOnTime = getScreenOnTime(),
            appUsageTime = getAppUsageTime(),
            lastActiveTime = getLastActiveTime(),
            isDeviceInUse = isDeviceCurrentlyInUse(),
            unlockCount = getUnlockCount()
        )
        
        // 警报事件（最近的）
        val alertEvents = getRecentAlertEvents()
        
        return CommunityHealthData(
            userId = userId,
            timestamp = currentTime,
            deviceStatus = deviceStatus,
            healthMetrics = healthMetrics,
            activityData = activityData,
            alertEvents = alertEvents
        )
    }
    
    /**
     * 发送到社区后端 API
     */
    private suspend fun sendToCommunityBackend(data: CommunityHealthData) {
        withContext(Dispatchers.IO) {
            try {
                val serverUrl = prefsManager.getCommunityServerUrl()
                val apiKey = prefsManager.getCommunityApiKey()
                
                if (serverUrl.isEmpty() || apiKey.isEmpty()) {
                    Log.e(tag, "服务器配置不完整")
                    return@withContext
                }
                
                val jsonData = convertDataToJson(data)
                
                val url = URL("$serverUrl/api/community/health-data")
                val connection = url.openConnection() as HttpURLConnection
                
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.doOutput = true
                    
                    // 设置请求头
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Authorization", "Bearer $apiKey")
                    connection.setRequestProperty("X-User-ID", data.userId)
                    
                    // 发送数据
                    connection.outputStream.use { os ->
                        os.write(jsonData.toByteArray())
                    }
                    
                    // 读取响应
                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))
                        val response = reader.readText()
                        Log.d(tag, "数据上报成功：$response")
                        reader.close()
                    } else {
                        Log.e(tag, "数据上报失败，响应码：$responseCode")
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.e(tag, "网络请求异常：${e.message}")
            }
        }
    }
    
    /**
     * 将数据转换为 JSON
     */
    private fun convertDataToJson(data: CommunityHealthData): String {
        val json = JSONObject()
        json.put("userId", data.userId)
        json.put("timestamp", data.timestamp)
        
        // 设备状态
        val deviceJson = JSONObject()
        deviceJson.put("batteryLevel", data.deviceStatus.batteryLevel)
        deviceJson.put("isCharging", data.deviceStatus.isCharging)
        deviceJson.put("networkType", data.deviceStatus.networkType)
        deviceJson.put("signalStrength", data.deviceStatus.signalStrength)
        deviceJson.put("appVersion", data.deviceStatus.appVersion)
        deviceJson.put("systemVersion", data.deviceStatus.systemVersion)
        json.put("deviceStatus", deviceJson)
        
        // 健康指标
        val healthJson = JSONObject()
        healthJson.put("stepCount", data.healthMetrics.stepCount)
        healthJson.put("sleepDuration", data.healthMetrics.sleepDuration)
        healthJson.put("sleepQuality", data.healthMetrics.sleepQuality)
        data.healthMetrics.heartRate?.let { healthJson.put("heartRate", it) }
        data.healthMetrics.bloodOxygen?.let { healthJson.put("bloodOxygen", it) }
        healthJson.put("calorieBurn", data.healthMetrics.calorieBurn)
        json.put("healthMetrics", healthJson)
        
        // 活动数据
        val activityJson = JSONObject()
        activityJson.put("screenOnTime", data.activityData.screenOnTime)
        activityJson.put("appUsageTime", data.activityData.appUsageTime)
        activityJson.put("lastActiveTime", data.activityData.lastActiveTime)
        activityJson.put("isDeviceInUse", data.activityData.isDeviceInUse)
        activityJson.put("unlockCount", data.activityData.unlockCount)
        json.put("activityData", activityJson)
        
        // 警报事件
        val alertsArray = org.json.JSONArray()
        data.alertEvents.forEach { alert ->
            val alertJson = JSONObject()
            alertJson.put("alertId", alert.alertId)
            alertJson.put("alertType", alert.alertType)
            alertJson.put("alertLevel", alert.alertLevel)
            alertJson.put("triggerTime", alert.triggerTime)
            alertJson.put("resolved", alert.resolved)
            alertJson.put("reason", alert.reason)
            alertsArray.put(alertJson)
        }
        json.put("alertEvents", alertsArray)
        
        return json.toString()
    }
    
    // ========== 数据收集辅助方法 ==========
    
    private fun getBatteryLevel(): Int {
        // TODO: 实现电池电量获取
        return -1
    }
    
    private fun isDeviceCharging(): Boolean {
        // TODO: 实现充电状态检测
        return false
    }
    
    private fun getNetworkType(): String {
        // TODO: 实现网络类型检测
        return "unknown"
    }
    
    private fun getSignalStrength(): Int {
        // TODO: 实现信号强度检测
        return -1
    }
    
    private fun getAppVersion(): String {
        return packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
    }
    
    private fun getTodaySteps(): Int {
        // TODO: 集成现有的步数统计
        return 0
    }
    
    private fun getSleepDuration(): Int {
        // TODO: 从睡眠监测服务获取
        return 0
    }
    
    private fun estimateSleepQuality(): String {
        // TODO: 根据睡眠时长和质量评估
        return "fair"
    }
    
    private fun calculateCalorieBurn(): Int {
        // TODO: 根据活动量计算
        return 0
    }
    
    private fun getScreenOnTime(): Int {
        // TODO: 使用 UsageStats 获取
        return 0
    }
    
    private fun getAppUsageTime(): Int {
        // TODO: 使用 UsageStats 获取
        return 0
    }
    
    private fun getLastActiveTime(): Long {
        // TODO: 返回最后活跃时间戳
        return System.currentTimeMillis()
    }
    
    private fun isDeviceCurrentlyInUse(): Boolean {
        // TODO: 检测设备是否正在使用
        return false
    }
    
    private fun getUnlockCount(): Int {
        // TODO: 统计解锁次数
        return 0
    }
    
    private fun getRecentAlertEvents(): List<AlertEvent> {
        // TODO: 从历史记录中获取最近的警报
        return emptyList()
    }
}