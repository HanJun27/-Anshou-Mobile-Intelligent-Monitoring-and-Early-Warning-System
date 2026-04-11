package com.livewell.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.livewell.MainActivity
import com.livewell.R
import com.livewell.untils.PrefsManager
import com.livewell.untils.UsageStatsHelper
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt
import com.livewell.untils.SecurePrefsManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import android.net.Uri
import com.livewell.model.AlertHistoryRecord
import com.livewell.model.AlertType
import com.livewell.model.AlertStatus
import com.livewell.model.AlertMethod
import com.livewell.untils.SystemStepManager
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.io.File  // ✅ 添加 File 导入



class SleepMonitorService : Service(), SensorEventListener {

    // 数据缓存类
    private data class SleepDataCache(
        val timestamp: Long,
        val stepCount: Int,
        val screenOffTime: Long,
        val motionDetected: Boolean
    )

    private lateinit var prefsManager: PrefsManager
    private lateinit var usageStatsHelper: UsageStatsHelper
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private val tag = "SleepMonitorService"
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var isSleepMonitoring = false
    private var lastStepCount = 0
    private var lastScreenOffTime: Long = 0
    private var possibleSleepStartTime: Long = 0
    private var confirmedSleepStartTime: Long = 0
        get() = prefsManager.getLong("confirmed_sleep_start_time", 0)
        set(value) {
            field = value
            if (value > 0) {
                prefsManager.saveLong("confirmed_sleep_start_time", value)
            } else {
                prefsManager.saveLong("confirmed_sleep_start_time", 0)
            }
        }
    private var lastWakeTime: Long = System.currentTimeMillis()
    private var lastAccelValues = FloatArray(3)
    private lateinit var systemStepManager: SystemStepManager
    private var lastMotionTime: Long = System.currentTimeMillis()

    private var lastAccelMagnitude = 0f
    private var motionlessStartTime: Long = 0
    private var latestWakeUpAlarmScheduled = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // ✅ 耗电优化相关变量
    private var currentSamplingRate = 500000 // 当前采样率 (微秒)，默认 2Hz
    private var stepCheckCounter = 0 // 步数检查计数器
    private val STEP_CHECK_INTERVAL = 24 // 每 2 分钟检查一次步数 (2 分钟 / 5 秒 = 24 次)
    private var screenReceiverRegistered = false // 屏幕广播接收器是否已注册
    private var sleepDataCache = mutableListOf<SleepDataCache>() // 睡眠数据缓存
    private var lastBatchSaveTime = 0L // 上次批量保存时间
    private val BATCH_SAVE_INTERVAL = 2 * 60 * 1000L // ✅ 每 2 分钟批量保存一次（原来是 5 分钟）

    companion object {
        private const val NOTIFICATION_ID = 3001
        private const val CHANNEL_ID = "sleep_monitor_channel"
        private const val SCREEN_OFF_CHECK_DELAY = 5 * 60 * 1000L

        fun start(context: Context) {
            val intent = Intent(context, SleepMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        try {
            prefsManager = PrefsManager(this)
            usageStatsHelper = UsageStatsHelper(this)
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            systemStepManager = SystemStepManager.getInstance(this)

            // ✅ 新增：记录服务启动日志
            Log.i(tag, "==========================================")
            Log.i(tag, "睡眠监测服务启动 - ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            Log.i(tag, "==========================================")
            
            UnifiedNotificationService.sleepMonitorRunning = true
            UnifiedNotificationService.sleepMonitorInfo = "睡眠监测服务运行中"
            
            // ✅ 恢复之前的状态（服务重启时）
            restoreServiceState()

            createNotificationChannel()

            // ✅ 使用统一通知渠道作为前台服务通知，避免显示多个独立通知
            Log.d(tag, "正在调用 startForeground()...")
            startForeground(
                UnifiedNotificationService.NOTIFICATION_ID,
                createUnifiedServiceNotification()
            )
            Log.i(tag, "✅ startForeground() 调用成功，前台通知已显示")

            // ✅ 注册屏幕状态广播接收器（代替轮询）
            registerScreenReceiver()
            
            // ✅ 初始化批量保存时间
            lastBatchSaveTime = System.currentTimeMillis()
            
            // ✅ 启动定期活动快照任务（每 5 分钟）
            startPeriodicActivitySnapshot()

            // ✅ 检查是否在睡眠时段，决定启动哪些传感器
            if (isWithinSleepTimeWindow()) {
                Log.i(tag, "当前在睡眠时段，启动完整监测")
                // ✅ 屏幕监测已改用广播，不需要启动定时器
                startAccelerometerMonitoring()
                scheduleStepCheck() // ✅ 新增：定时步数检查
                scheduleLatestWakeUpAlarm() // ✅ 确保设置最晚起床检查闹钟
            } else {
                Log.i(tag, "当前非睡眠时段，进入低功耗模式")
                enterLowPowerMode()
                scheduleSleepWindowAlarm() // ✅ 添加：设置睡眠窗口启动闹钟
            }
            
            Log.i(tag, "✅ 睡眠监测服务初始化完成")
            // ✅ 写入文件日志
            com.livewell.untils.AppLogger.i(tag, "🚀 睡眠监测服务已启动")
        } catch (e: Exception) {
            Log.e(tag, "❌ 睡眠监测服务启动失败", e)
            throw e  // 重新抛出，让系统知道启动失败
        }
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // ✅ 使用统一通知渠道，不再创建独立的服务通知渠道
            val channel = NotificationChannel(
                UnifiedNotificationService.CHANNEL_ID,
                "安守 - 统一服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示所有服务的运行状态"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建统一的前台服务通知（不显示独立通知）
     */
    private fun createUnifiedServiceNotification(): android.app.Notification {
        // 返回一个最小化的通知，实际内容在 UnifiedNotificationService 中统一管理
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, UnifiedNotificationService.CHANNEL_ID)
            .setContentTitle("安守")
            .setContentText("睡眠监测服务运行中")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ✅ 处理从低功耗模式唤醒的广播
        when (intent?.action) {
            "ACTION_START_SLEEP_MONITORING" -> {
                Log.i(tag, "收到睡眠窗口启动广播，启动完整监测")
                startFullMonitoring()
            }
            "ACTION_CHECK_LATEST_WAKEUP" -> {
                Log.i(tag, "收到最晚起床检查闹钟")
                checkIfUserIsStillSleeping()
            }
            "ACTION_RETRY_EMAIL" -> {
                val report = intent.getStringExtra("retry_report")
                if (!report.isNullOrEmpty()) {
                    Log.i(tag, "🔄 收到邮件重试广播")
                    sendEmailReport(report)
                }
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        executor.shutdown()
        sensorManager.unregisterListener(this)
        
        // ✅ 注销屏幕广播接收器
        unregisterScreenReceiver()
        
        // ✅ 保存最后的缓存数据
        if (sleepDataCache.isNotEmpty()) {
            saveBatchData()
        }
        
        // ✅ 新增：记录服务停止日志
        Log.w(tag, "睡眠监测服务停止")
        
        UnifiedNotificationService.sleepMonitorRunning = false
        UnifiedNotificationService.sleepMonitorInfo = ""
        //UnifiedNotificationService.updateNotification(this)
        serviceScope.cancel()
        super.onDestroy()
    }


    /**
     * 注册屏幕状态广播接收器（代替轮询）
     */
    private fun registerScreenReceiver() {
        // ✅ 防止重复注册
        if (screenReceiverRegistered) {
            Log.w(tag, "屏幕广播接收器已注册，跳过")
            return
        }
        
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(tag, "📱 屏幕关闭广播")
                        handleScreenOff()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d(tag, "📱 屏幕亮起广播")
                        handleScreenOn()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        
        screenReceiverRegistered = true
        Log.i(tag, "✅ 屏幕广播接收器已注册")
    }
    
    /**
     * 处理屏幕关闭事件
     */
    private fun handleScreenOff() {
        // 屏幕关闭，记录时间
        if (lastScreenOffTime == 0L) {
            lastScreenOffTime = System.currentTimeMillis()
            Log.d(tag, "屏幕关闭时间：${Date(lastScreenOffTime)}")
            
            // ✅ 关键事件：立即保存缓存数据
            if (sleepDataCache.isNotEmpty()) {
                tryBatchSaveData()
            }
        }

        // 检查是否满足入睡候选条件
        if (isWithinSleepTimeWindow()) {
            checkSleepCandidate()
        }
    }
    
    /**
     * 处理屏幕亮起事件
     */
    private fun handleScreenOn() {
        // 屏幕亮起，记录亮起时间（用于醒来判定）
        if (lastScreenOffTime > 0) {
            val screenOffDuration = System.currentTimeMillis() - lastScreenOffTime
            Log.d(tag, "屏幕从关闭到亮起，持续了：${screenOffDuration / 1000}秒")
        }
        lastScreenOffTime = 0

        // ✅ 如果已经确认入睡，则判定醒来（修正逻辑）
        // 现在屏幕亮起会立即触发 checkWakeUp，结合步数和加速度综合判定
        if (confirmedSleepStartTime > 0) {
            Log.d(tag, "检测到屏幕亮起，触发醒来判定")
            checkWakeUp()
        }
    }

    private var consecutiveSleepChecks = 0 // ✅ 新增：连续入睡检查计数器
    
    /**
     * 检查是否满足入睡候选条件
     * 条件：屏幕关闭 + 步数无增长 + (根据模式可选加速度) 持续阈值时间
     */
    private fun checkSleepCandidate() {
        if (confirmedSleepStartTime > 0) return // 已经确认入睡
        if (!isWithinSleepTimeWindow()) {
            Log.i(tag, "当前时间不在睡眠窗口内，跳过入睡判定")
            return
        }

        val currentSteps = getCurrentStepCount()
        val inactiveThreshold = prefsManager.getInactiveThreshold() * 60 * 1000L // 转为毫秒
        val stepThreshold = prefsManager.getSleepStepThreshold()
        val mode = prefsManager.getSleepMode()

        // ✅ 添加详细日志
        com.livewell.untils.AppLogger.d(tag, "🔍 入睡检测 - 步数=$currentSteps, 阈值=$stepThreshold, 模式=$mode")
        com.livewell.untils.AppLogger.d(tag, "   屏幕关闭时长=${(System.currentTimeMillis() - lastScreenOffTime) / 1000}秒")

        // 计算步数增长
        val stepIncrease = currentSteps - lastStepCount

        // ✅ 只在非零步数增长时才重置（避免传感器误差）
        if (stepIncrease > stepThreshold && stepIncrease > 0) {
            // 有步数增长，重置候选时间和计数器
            possibleSleepStartTime = 0
            motionlessStartTime = 0
            consecutiveSleepChecks = 0 // ✅ 重置计数器
            Log.d(tag, "检测到步数增长$stepIncrease，重置入睡候选")
            // ✅ 写入文件日志
            com.livewell.untils.AppLogger.d(tag, "❌ 步数增加${stepIncrease}步，取消入睡判定")
            return
        }

        // 更新步数记录（即使用户动了也要更新）
        lastStepCount = currentSteps

        // 检查屏幕关闭持续时间
        val screenOffDuration = System.currentTimeMillis() - lastScreenOffTime

        if (screenOffDuration < inactiveThreshold) {
            com.livewell.untils.AppLogger.d(tag, "⏱️ 屏幕关闭时长不足：${screenOffDuration / 1000}秒 < ${inactiveThreshold / 1000}秒")
            return // 屏幕关闭时间还不够
        }
        
        com.livewell.untils.AppLogger.d(tag, "✅ 屏幕关闭时长满足：${screenOffDuration / 1000}秒 >= ${inactiveThreshold / 1000}秒")

        // 根据模式决定是否使用加速度计验证
        if (mode == "balanced") {
            com.livewell.untils.AppLogger.d(tag, "🔬 均衡模式：需要加速度计验证")
            // 均衡模式：需要加速度计验证
            if (motionlessStartTime == 0L) {
                // 开始加速度监测
                startAccelerometerMonitoring()
                motionlessStartTime = System.currentTimeMillis()
                com.livewell.untils.AppLogger.d(tag, "   启动加速度监测，等待无体动...")
            } else {
                // 检查无体动持续时间
                val motionlessDuration = System.currentTimeMillis() - lastMotionTime
                com.livewell.untils.AppLogger.d(tag, "   无体动时长=${motionlessDuration / 1000}秒, 阈值=${inactiveThreshold / 1000}秒")
                if (motionlessDuration >= inactiveThreshold) {
                    // ✅ 新增：需要连续 2 次检查都满足条件才确认入睡
                    consecutiveSleepChecks++
                    Log.d(tag, "✅ 第${consecutiveSleepChecks}次连续检查通过")
                    // ✅ 写入文件日志
                    com.livewell.untils.AppLogger.d(tag, "🔍 入睡检测第${consecutiveSleepChecks}次通过")
                    if (consecutiveSleepChecks >= 2) {
                        Log.i(tag, "✅ 连续${consecutiveSleepChecks}次检查满足入睡条件，确认入睡")
                        confirmSleep()
                    } else {
                        Log.d(tag, "第${consecutiveSleepChecks}次检查满足条件，继续观察")
                    }
                }
            }
        } else {
            com.livewell.untils.AppLogger.d(tag, "⚡ 省电模式：仅基于屏幕和步数判定")
            // 省电模式：仅基于屏幕和步数判定
            if (possibleSleepStartTime == 0L) {
                possibleSleepStartTime = lastScreenOffTime
                com.livewell.untils.AppLogger.d(tag, "   记录可能入睡时间：${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(possibleSleepStartTime))}")
            }

            val totalInactiveTime = System.currentTimeMillis() - possibleSleepStartTime
            com.livewell.untils.AppLogger.d(tag, "   总无活动时长=${totalInactiveTime / 1000}秒, 阈值=${inactiveThreshold / 1000}秒")
            if (totalInactiveTime >= inactiveThreshold) {
                // ✅ 同样需要连续验证
                consecutiveSleepChecks++
                if (consecutiveSleepChecks >= 2) {
                    Log.i(tag, "✅ 省电模式：连续${consecutiveSleepChecks}次检查满足入睡条件，确认入睡")
                    confirmSleep()
                }
            }
        }
    }

    /**
     * 开始加速度计监测
     */
    private fun startAccelerometerMonitoring() {
        if (accelerometer != null) {
            // ✅ 使用当前动态调整的采样率
            sensorManager.registerListener(this, accelerometer, currentSamplingRate)
    
            // 如果开启陀螺仪增强，也注册
            if (prefsManager.isGyroEnabled() && gyroscope != null) {
                sensorManager.registerListener(this, gyroscope, currentSamplingRate)
            }
    
            Log.i(tag, "✅ 传感器监测已启动，采样率：${1000000 / currentSamplingRate}Hz")
        }
    }
        
    /**
     * ✅ 动态调整采样率
     */
    private fun adjustSamplingRate() {
        val mode = prefsManager.getSleepMode()
            
        // ✅ 省电模式且已入睡：使用更低频率
        if (mode == "power_save" && confirmedSleepStartTime > 0) {
            setSamplingRate(2000000) // 0.5Hz
            Log.d(tag, "省电模式 + 已入睡：0.5Hz 低频采样")
            return
        }
            
        when {
            // 还未入睡：高频采样 (2Hz)
            confirmedSleepStartTime <= 0 -> {
                setSamplingRate(500000) // 2Hz
                Log.d(tag, "入睡候选期：2Hz 高频采样")
            }
                
            // 已确认入睡但睡眠时长 < 4 小时：中频采样 (1Hz)
            System.currentTimeMillis() - confirmedSleepStartTime < 4 * 60 * 60 * 1000L -> {
                setSamplingRate(1000000) // 1Hz
                Log.d(tag, "深睡期：1Hz 中频采样")
            }
                
            // 睡眠时长 >= 4 小时且接近起床时间：恢复高频
            else -> {
                val wakeHour = prefsManager.getWakeUpHour()
                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    
                if (currentHour >= wakeHour - 1) {
                    setSamplingRate(500000) // 2Hz
                    Log.d(tag, "接近起床时间：恢复 2Hz 高频采样")
                } else {
                    setSamplingRate(2000000) // 0.5Hz
                    Log.d(tag, "浅睡期：0.5Hz 低频采样")
                }
            }
        }
    }
        
    /**
     * 设置采样率
     */
    private fun setSamplingRate(microseconds: Int) {
        if (currentSamplingRate != microseconds) {
            val oldRate = currentSamplingRate
            currentSamplingRate = microseconds
                
            // ✅ 记录日志但不立即重新注册，等待下次自然唤醒时再应用
            Log.i(tag, "📊 采样率将从 ${1000000 / oldRate}Hz 调整为 ${1000000 / microseconds}Hz（下次传感器刷新时生效）")
        }
    }

    /**
     * 停止传感器监测
     */
    private fun stopSensorMonitoring() {
        sensorManager.unregisterListener(this)
        Log.i(tag, "传感器监测已停止")
    }
    
    /**
     * ✅ 定时步数检查（每 2 分钟）
     */
    private fun scheduleStepCheck() {
        executor.scheduleAtFixedRate({
            checkStepCount()
        }, 0, 5, TimeUnit.SECONDS) // 每 5 秒唤醒一次，但只每 2 分钟检查步数
    }
    
    /**
     * 检查步数（每 2 分钟一次）
     */
    private fun checkStepCount() {
        stepCheckCounter++
        
        // ✅ 只在计数达到阈值时检查步数（2 分钟）
        if (stepCheckCounter >= STEP_CHECK_INTERVAL) {
            stepCheckCounter = 0
            
            val currentSteps = getCurrentStepCount()
            val stepIncrease = currentSteps - lastStepCount
            
            Log.d(tag, "⏰ 2 分钟步数检查：当前=$currentSteps, 增长=$stepIncrease")
            
            // 缓存数据用于批量保存
            cacheSleepData(currentSteps)
            
            // 如果在入睡候选期，步数异常增长需要重置状态
            if (possibleSleepStartTime > 0 && confirmedSleepStartTime <= 0) {
                val stepThreshold = prefsManager.getSleepStepThreshold()
                if (stepIncrease > stepThreshold) {
                    Log.w(tag, "检测到步数增长过大，重置入睡候选状态")
                    possibleSleepStartTime = 0
                    motionlessStartTime = 0
                }
            }
            
            lastStepCount = currentSteps
            
            // ✅ 尝试批量保存数据
            tryBatchSaveData()
            
            // ✅ 定期调整采样率
            adjustSamplingRate()
        }
    }
    
    /**
     * 缓存睡眠数据
     */
    private fun cacheSleepData(stepCount: Int) {
        val cache = SleepDataCache(
            timestamp = System.currentTimeMillis(),
            stepCount = stepCount,
            screenOffTime = lastScreenOffTime,
            motionDetected = (System.currentTimeMillis() - lastMotionTime) < 30000
        )
        sleepDataCache.add(cache)
        
        Log.d(tag, "📊 缓存睡眠数据，当前缓存数：${sleepDataCache.size}")
    }
    
    /**
     * 尝试批量保存数据
     */
    private fun tryBatchSaveData() {
        val now = System.currentTimeMillis()
        
        // ✅ 每 5 分钟或缓存满 10 条时保存
        if (now - lastBatchSaveTime > BATCH_SAVE_INTERVAL || sleepDataCache.size >= 10) {
            saveBatchData()
        }
    }
    
    /**
     * 批量保存数据
     */
    private fun saveBatchData() {
        if (sleepDataCache.isEmpty()) return
        
        serviceScope.launch(Dispatchers.IO) {
            try {
                val dataSize = sleepDataCache.size
                val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                
                // ✅ 1. 保存到 JSON 文件
                val sleepDir = File(getExternalFilesDir(null), "sleep_data")
                if (!sleepDir.exists()) {
                    sleepDir.mkdirs()
                }
                
                val fileName = "sleep_${dateStr}.json"
                val file = File(sleepDir, fileName)
                
                // 读取已有数据（如果有）
                val existingJson = if (file.exists()) {
                    JSONObject(file.readText())
                } else {
                    JSONObject()
                }
                
                // 追加新数据
                val recordsArray = if (existingJson.has("records")) {
                    existingJson.getJSONArray("records")
                } else {
                    org.json.JSONArray()
                }
                
                // 添加缓存数据
                sleepDataCache.forEach { cache ->
                    val record = JSONObject().apply {
                        put("timestamp", cache.timestamp)
                        put("stepCount", cache.stepCount)
                        put("screenOffTime", cache.screenOffTime)
                        put("motionDetected", cache.motionDetected)
                    }
                    recordsArray.put(record)
                }
                
                existingJson.put("records", recordsArray)
                existingJson.put("lastUpdated", System.currentTimeMillis())
                
                // 写入文件
                file.writeText(existingJson.toString(2))
                
                // ✅ 2. 同时保存到 PrefsManager（用于快速访问）
                // prefsManager.saveSleepDataBatch(sleepDataCache)  // ✅ 暂时注释，该方法不存在
                
                // 清空缓存
                sleepDataCache.clear()
                lastBatchSaveTime = System.currentTimeMillis()
                
                Log.i(tag, "💾 批量保存睡眠数据成功，文件：${file.absolutePath}, 条数：$dataSize")
            } catch (e: Exception) {
                Log.e(tag, "批量保存数据失败：${e.message}", e)
                // ❗ 失败时不清空缓存，下次重试
            }
        }
    }
    
    /**
     * ✅ 进入低功耗模式（非睡眠时段）
     */
    private fun enterLowPowerMode() {
        // 关闭所有与睡眠相关的传感器
        stopSensorMonitoring()
        
        // ✅ 注销屏幕广播接收器以节省电量
        unregisterScreenReceiver()
        
        // ✅ 重置步数检查计数器，停止定时器
        stepCheckCounter = 0
        
        // ✅ 重置屏幕状态，避免下次启动时使用旧数据
        lastScreenOffTime = 0
        
        Log.i(tag, "💤 进入低功耗模式，关闭所有传感器和定时器")
        
        // ✅ 设置在睡眠窗口开始前启动的闹钟
        scheduleSleepWindowAlarm()
    }
    
    /**
     * 设置睡眠窗口启动闹钟
     */
    private fun scheduleSleepWindowAlarm() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val sleepHour = prefsManager.getSleepHour()
            val sleepMinute = prefsManager.getSleepMinute()
            
            // 计算今天的睡眠时间
            val sleepTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, sleepHour)
                set(Calendar.MINUTE, sleepMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                
                // 如果今天的时间已过，设为明天
                if (timeInMillis < System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            
            // ✅ 提前 5 分钟启动
            val preWakeTime = sleepTime.timeInMillis - 5 * 60 * 1000L
            val now = System.currentTimeMillis()
            val delayMillis = preWakeTime - now
            
            if (delayMillis > 0) {
                Log.i(tag, "设置睡眠窗口启动闹钟，延迟：${delayMillis / 1000 / 60}分钟")
                
                val intent = Intent(this, SleepMonitorService::class.java).apply {
                    action = "ACTION_START_SLEEP_MONITORING"
                }
                
                val pendingIntent = PendingIntent.getService(
                    this, 2001, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        preWakeTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        preWakeTime,
                        pendingIntent
                    )
                }
            } else {
                Log.w(tag, "睡眠时间即将到来，立即启动监测")
                startFullMonitoring()
            }
        } catch (e: Exception) {
            Log.e(tag, "设置睡眠窗口闹钟失败：${e.message}")
        }
    }
    
    /**
     * 启动完整监测（从低功耗模式唤醒）
     */
    private fun startFullMonitoring() {
        Log.i(tag, "☀️ 从低功耗模式唤醒，启动完整监测")
        
        // ✅ 重新注册屏幕广播接收器
        if (!screenReceiverRegistered) {
            registerScreenReceiver()
        }
        
        // ✅ 重置步数检查计数器
        stepCheckCounter = 0
        
        // ✅ 初始化批量保存时间
        lastBatchSaveTime = System.currentTimeMillis()
        
        // ✅ 启动传感器和定时器
        startAccelerometerMonitoring()
        scheduleStepCheck()
        
        Log.i(tag, "✅ 完整监测已启动")
    }
    
    /**
     * 注销屏幕广播接收器
     */
    private fun unregisterScreenReceiverIfNeeded() {
        if (screenReceiverRegistered) {
            try {
                // 注意：实际应该在 onDestroy 中统一注销
                // 这里只是标记，不真正注销，避免重复注销导致崩溃
                Log.d(tag, "准备在 onDestroy 中注销屏幕广播接收器")
            } catch (e: Exception) {
                Log.e(tag, "注销屏幕广播接收器失败：${e.message}")
            }
        }
    }
    
    /**
     * ✅ 真正的广播接收器注销（在 onDestroy 中调用）
     */
    private var screenReceiver: BroadcastReceiver? = null
    
    private fun unregisterScreenReceiver() {
        if (screenReceiver != null && screenReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
                screenReceiver = null
                screenReceiverRegistered = false
                Log.i(tag, "✅ 屏幕广播接收器已注销")
            } catch (e: Exception) {
                Log.e(tag, "注销屏幕广播接收器失败：${e.message}")
            }
        }
    }

    /**
     * 确认入睡
     */
    private fun confirmSleep() {
        val sleepTime = System.currentTimeMillis()
        confirmedSleepStartTime = sleepTime
        possibleSleepStartTime = 0
        motionlessStartTime = 0
        consecutiveSleepChecks = 0 // ✅ 重置计数器

        Log.i(tag, "确认入睡时间：${Date(sleepTime)}")
        // ✅ 写入文件日志
        com.livewell.untils.AppLogger.i(tag, "✅ 确认入睡：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(sleepTime))}")
        
        // ✅ 记录睡前数据快照（用于解决跨天问题）
        recordPreSleepDataSnapshot(sleepTime)
                
        // ✅ 保存状态到 PrefsManager（用于服务重启恢复）
        prefsManager.saveLong("confirmed_sleep_start_time", confirmedSleepStartTime)
        prefsManager.saveLong("latest_wake_up_alarm_scheduled", if (latestWakeUpAlarmScheduled) 1 else 0)  // ✅ 用 Long 代替 Boolean
        prefsManager.saveLong("last_step_count", lastStepCount.toLong())  // ✅ 用 Long 代替 Int
                
        // ✅ 新增：保存到缓存（用于 checkIfUserIsStillSleeping 恢复）
        prefsManager.saveLong("cached_sleep_start_time", confirmedSleepStartTime)
                
        // ✅ 新增：设置最晚起床检查闹钟
        scheduleLatestWakeUpAlarm()
        
        // ✅ 确认入睡后立即调整采样率
        adjustSamplingRate()

        // 停止传感器监测以省电
        if (prefsManager.getSleepMode() == "balanced") {
            stopSensorMonitoring()
            Log.i(tag, "均衡模式：已确认入睡，停止传感器监测")
        } else {
            Log.i(tag, "省电模式：已确认入睡，保持低频监测")
        }
    }

    /**
     * 设置最晚起床检查闹钟
     */
    private fun scheduleLatestWakeUpAlarm() {
        if (latestWakeUpAlarmScheduled) {
            Log.w(tag, "最晚起床闹钟已设置，跳过")
            return
        }

        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val presetHour = prefsManager.getWakeUpHour()
            val presetMinute = prefsManager.getWakeUpMinute()

            // 计算今天的预设起床时间
            val todayWakeTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, presetHour)
                set(Calendar.MINUTE, presetMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                // 如果今天的时间已过，设为明天
                if (timeInMillis < System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            // ✅ 读取用户配置的容忍时长
            val toleranceHours = prefsManager.getWakeUpToleranceHours()
            val latestWakeTime = todayWakeTime.timeInMillis + toleranceHours * 60 * 60 * 1000L

            val now = System.currentTimeMillis()
            val delayMillis = latestWakeTime - now

            // 如果检查时间已经过去，立即检查
            if (delayMillis <= 0) {
                Log.w(tag, "最晚起床时间已过，立即检查")
                checkIfUserIsStillSleeping()
                return
            }

            Log.i(
                tag,
                "设置最晚起床检查闹钟：${toleranceHours}小时后，延迟：${delayMillis / 1000 / 60}分钟"
            )

            val intent = Intent(this, SleepMonitorService::class.java).apply {
                action = "ACTION_CHECK_LATEST_WAKEUP"
            }

            val pendingIntent = PendingIntent.getService(
                this, 1001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 使用 RTC_WAKEUP 唤醒设备进行检查
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    latestWakeTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    latestWakeTime,
                    pendingIntent
                )
            }

            latestWakeUpAlarmScheduled = true
            Log.i(
                tag,
                "最晚起床检查闹钟已设置：${android.icu.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(latestWakeTime)}"
            )

        } catch (e: Exception) {
            Log.e(tag, "设置最晚起床闹钟失败", e)
        }
    }
    
    /**
     * ✅ 设置睡眠窗口启动闹钟（在非睡眠时段使用）
     */



    /**
     * 检查是否醒来
     */
    private fun checkWakeUp() {
        // ✅ 保护判断
        if (confirmedSleepStartTime <= 0) {
            Log.w(tag, "confirmedSleepStartTime 无效，跳过醒来判定")
            return
        }

        val currentSteps = getCurrentStepCount()
        val stepIncrease = currentSteps - lastStepCount
        val stepThreshold = prefsManager.getAwakeStepThreshold()
        
        // ✅ 获取屏幕状态
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenOn = powerManager.isInteractive
        val screenOnDuration = if (lastScreenOffTime > 0) {
            System.currentTimeMillis() - lastScreenOffTime
        } else {
            0L
        }
        
        // ✅ 获取加速度数据（仅均衡模式）
        val mode = prefsManager.getSleepMode()
        val motionDetected = if (mode == "balanced") {
            val timeSinceLastMotion = System.currentTimeMillis() - lastMotionTime
            timeSinceLastMotion < 30000  // 30 秒内有体动
        } else {
            false
        }
        
        // ✅ 添加详细日志
        com.livewell.untils.AppLogger.d(tag, "🔍 醒来检测 - 步数增加=$stepIncrease, 屏幕=${if (isScreenOn) "亮" else "灭"}, 模式=$mode")
        com.livewell.untils.AppLogger.d(tag, "   步数阈值=${stepThreshold * 0.5f}, 屏幕时长=${screenOnDuration / 1000}秒, 有体动=$motionDetected")
        
        // ✅ 三重判定条件（降低阈值提高灵敏度）
        val wakeUpByStep = stepIncrease > stepThreshold * 0.5f  // ✅ 降低 50%
        val wakeUpByScreen = isScreenOn && screenOnDuration > 3000  // ✅ 3 秒即可
        val wakeUpByMotion = motionDetected  // 30 秒内有明显体动
        
        Log.d(tag, "   判定结果 - 步数=$wakeUpByStep, 屏幕=$wakeUpByScreen, 体动=$wakeUpByMotion")
        // ✅ 写入文件日志
        com.livewell.untils.AppLogger.d(tag, "   判定结果 - 步数=$wakeUpByStep, 屏幕=$wakeUpByScreen, 体动=$wakeUpByMotion")
        
        // ✅ 任一条件满足即判定为醒来
        val isWakeUp = wakeUpByStep || wakeUpByScreen || wakeUpByMotion
        
        // ✅ 新增：如果超过预设起床时间 + 容忍时长，强制判定为醒来
        val toleranceHours = prefsManager.getWakeUpToleranceHours()
        val presetWakeTime = getTodayPresetWakeTime()
        if (System.currentTimeMillis() > presetWakeTime + toleranceHours * 60 * 60 * 1000L) {
            Log.i(tag, "⏰ 已超过预设起床时间 +${toleranceHours}小时，强制判定为醒来")
            // ✅ 写入文件日志
            com.livewell.untils.AppLogger.i(tag, "⏰ 超过预设起床时间+${toleranceHours}h，强制醒来")
            forceWakeUp()
            return
        }
        
        if (isWakeUp) {
            val wakeTime = System.currentTimeMillis()
            val sleepDuration = wakeTime - confirmedSleepStartTime
            
            // ✅ 再判断睡眠时长（可调整为 2 小时）
            if (sleepDuration < 2 * 60 * 60 * 1000L) {
                Log.i(tag, "睡眠时长过短 (${sleepDuration / 1000 / 60}分钟)，视为误判，继续监测")
                // ✅ 写入文件日志
                com.livewell.untils.AppLogger.w(tag, "⚠️ 睡眠时长仅${sleepDuration / 1000 / 60}分钟，视为误判")
                confirmedSleepStartTime = 0
                latestWakeUpAlarmScheduled = false
                lastStepCount = currentSteps
                return
            }
            
            // ✅ 记录唤醒原因
            val wakeUpReason = buildString {
                if (wakeUpByStep) append("步数增长 (${stepIncrease}步) ")
                if (wakeUpByScreen) append("屏幕亮起 (${screenOnDuration / 1000}秒) ")
                if (wakeUpByMotion) append("检测到体动 ")
            }
            
            Log.i(tag, "✅ 醒来判定成功 - 原因：$wakeUpReason")
            // ✅ 写入文件日志
            com.livewell.untils.AppLogger.i(tag, "✅ 检测到醒来：${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(wakeTime))}, 原因=$wakeUpReason, 时长=${sleepDuration / 1000 / 60}分钟")
            
            // 记录睡眠数据
            if (confirmedSleepStartTime > 0) {
                recordSleepData(confirmedSleepStartTime, wakeTime, sleepDuration)
                com.livewell.untils.AppLogger.i(tag, "💾 睡眠记录已保存：入睡=${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(confirmedSleepStartTime))}, 醒来=${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(wakeTime))}, 时长=${sleepDuration / 1000 / 60}分钟")
            } else {
                Log.w(tag, "⚠️ 无法记录睡眠数据：confirmedSleepStartTime = 0")
                com.livewell.untils.AppLogger.w(tag, "⚠️ 无法记录睡眠数据：confirmedSleepStartTime 无效")
            }
            
            // 重置状态
            confirmedSleepStartTime = 0
            latestWakeUpAlarmScheduled = false
            lastStepCount = currentSteps
            lastMotionTime = System.currentTimeMillis()
            consecutiveSleepChecks = 0 // ✅ 重置入睡检查计数器
            
            // ✅ 清除 PrefsManager 中的状态（服务重启恢复用）
            prefsManager.saveLong("confirmed_sleep_start_time", 0)
            prefsManager.saveLong("latest_wake_up_alarm_scheduled", 0)  // ✅ 用 Long 代替 Boolean
            prefsManager.saveLong("cached_sleep_start_time", 0)  // ✅ 新增：清除缓存
            
            Log.i(tag, "✅ 醒来判定成功 - 原因：$wakeUpReason, 醒来时间：${Date(wakeTime)}, 睡眠时长：${sleepDuration / 1000 / 60}分钟")
            // ✅ 写入文件日志
            com.livewell.untils.AppLogger.i(tag, "✅ 检测到醒来：${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(wakeTime))}, 睡眠时长=${sleepDuration / 1000 / 60}分钟")
            
            // 检查是否超过正常起床时间
            checkWakeUpAbnormal(wakeTime)
        } else {
            Log.d(tag, "未满足醒来条件 - 步数增长:$stepIncrease, 屏幕:${if (isScreenOn) "亮 ${screenOnDuration/1000}s" else "关"}, 体动:${if (motionDetected) "有" else "无"}")
        }
    }

    /**
     * ✅ 恢复之前的状态（服务重启时）
     */
    private fun restoreServiceState() {
        // 从 PrefsManager 恢复状态
        val lastConfirmedSleepStart = prefsManager.getLong("confirmed_sleep_start_time", 0)
        val latestWakeUpAlarmScheduledValue = prefsManager.getLong("latest_wake_up_alarm_scheduled", 0) != 0L  // ✅ 用 Long 代替 Boolean
        val lastStepCountValue = prefsManager.getLong("last_step_count", 0).toInt()  // ✅ 用 Long 代替 Int
        
        if (lastConfirmedSleepStart > 0) {
            confirmedSleepStartTime = lastConfirmedSleepStart
            latestWakeUpAlarmScheduled = latestWakeUpAlarmScheduledValue
            lastStepCount = lastStepCountValue
            
            Log.i(tag, "✅ 恢复之前的入睡状态：${Date(lastConfirmedSleepStart)}, 步数：$lastStepCountValue")
        } else {
            Log.d(tag, "没有需要恢复的睡眠状态")
        }
    }
    
    /**
     * ✅ 强制唤醒（超过最晚起床时间）
     */
    private fun forceWakeUp() {
        if (confirmedSleepStartTime <= 0) {
            Log.w(tag, "confirmedSleepStartTime 无效，无法强制唤醒")
            return
        }
        
        val wakeTime = System.currentTimeMillis()
        val sleepDuration = wakeTime - confirmedSleepStartTime
        
        // 记录睡眠数据
        recordSleepData(confirmedSleepStartTime, wakeTime, sleepDuration)
        
        // 重置状态
        confirmedSleepStartTime = 0
        latestWakeUpAlarmScheduled = false
        lastStepCount = getCurrentStepCount()
        lastMotionTime = System.currentTimeMillis()
        consecutiveSleepChecks = 0
        
        Log.i(tag, "⏰ 强制唤醒 - 醒来时间：${Date(wakeTime)}, 睡眠时长：${sleepDuration / 1000 / 60}分钟")
        
        // 检查是否超过正常起床时间并发送警报
        checkWakeUpAbnormal(wakeTime)
    }
    
    /**
     * ✅ 获取今天的预设起床时间（毫秒）
     */
    private fun getTodayPresetWakeTime(): Long {
        val presetHour = prefsManager.getWakeUpHour()
        val presetMinute = prefsManager.getWakeUpMinute()
        
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, presetHour)
            set(Calendar.MINUTE, presetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    
    /**
     * 检查起床是否异常（超过预设起床时间 +2 小时）
     */
    private fun checkWakeUpAbnormal(wakeTime: Long) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = wakeTime
        val wakeHour = calendar.get(Calendar.HOUR_OF_DAY)
        val wakeMinute = calendar.get(Calendar.MINUTE)

        val presetHour = prefsManager.getWakeUpHour()
        val presetMinute = prefsManager.getWakeUpMinute()

        // ✅ 读取用户配置的容忍时长
        val toleranceHours = prefsManager.getWakeUpToleranceHours()

        // ✅ 添加详细日志，帮助诊断睡眠时长问题
        com.livewell.untils.AppLogger.i(tag, "🔍 起床异常检测开始")
        com.livewell.untils.AppLogger.i(tag, "   醒来时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(wakeTime))}")
        com.livewell.untils.AppLogger.i(tag, "   confirmedSleepStartTime：$confirmedSleepStartTime")
        if (confirmedSleepStartTime > 0) {
            com.livewell.untils.AppLogger.i(tag, "   入睡时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(confirmedSleepStartTime))}")
            val sleepDuration = wakeTime - confirmedSleepStartTime
            com.livewell.untils.AppLogger.i(tag, "   睡眠时长：${sleepDuration / 1000 / 60}分钟 (${sleepDuration / 1000 / 3600.0}小时)")
        } else {
            com.livewell.untils.AppLogger.w(tag, "   ⚠️ confirmedSleepStartTime 无效，无法计算睡眠时长")
        }

        // 计算预设起床时间的毫秒值
        val presetCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, presetHour)
            set(Calendar.MINUTE, presetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // 如果实际起床时间早于预设时间，说明是今天
            if (wakeTime < timeInMillis) {
                add(Calendar.DAY_OF_YEAR, -1)
            }
        }

        // ✅ 如果实际起床时间晚于预设时间 + 容忍时长
        val toleranceMillis = toleranceHours * 60 * 60 * 1000L
        val latestWakeTime = presetCalendar.timeInMillis + toleranceMillis

        if (wakeTime > latestWakeTime) {
            sendWakeUpAbnormalReport(wakeTime, presetHour, presetMinute, toleranceHours)
        } else {
            Log.d(tag, "用户在容忍时间内起床，未触发警报")
        }
    }

    /**
     * 发送起床异常报告
     */
    private fun sendWakeUpAbnormalReport(
        wakeTime: Long,
        presetHour: Int,
        presetMinute: Int,
        toleranceHours: Int
    ) {
        // ✅ 检查 confirmedSleepStartTime 是否有效
        val sleepDuration = if (confirmedSleepStartTime > 0) {
            wakeTime - confirmedSleepStartTime
        } else {
            0L  // 如果没有有效的入睡时间，设为 0
        }
        
        val avgSleepTime = prefsManager.getAverageSleepTime()
        val durationDiff = sleepDuration - avgSleepTime

        val report = StringBuilder()
        report.append("【${getString(R.string.app_name)}】起床异常报告\n")
        report.append(
            "时间：${
                SimpleDateFormat(
                    "yyyy-MM-dd HH:mm",
                    Locale.getDefault()
                ).format(Date())
            }\n"
        )
        report.append("================================\n\n")

        report.append(
            "⏰ 实际起床时间：${
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(
                    Date(
                        wakeTime
                    )
                )
            }\n"
        )
        report.append("📅 预设起床时间：${String.format("%02d:%02d", presetHour, presetMinute)}\n")
        report.append("⏳ 容忍时长：${toleranceHours}小时\n")
        
        // ✅ 根据睡眠时长是否有效来显示
        if (sleepDuration > 0) {
            report.append("😴 睡眠时长：${sleepDuration / 1000 / 60}分钟")

            if (Math.abs(durationDiff) > 30 * 60 * 1000) {
                if (durationDiff > 0) {
                    report.append(" (比平时长${durationDiff / 1000 / 60}分钟) ⚠️\n")
                } else {
                    report.append(" (比平时短${-durationDiff / 1000 / 60}分钟) ⚠️\n")
                }
            } else {
                report.append(" (正常)\n")
            }
        } else {
            report.append("😴 睡眠时长：数据缺失（未检测到入睡时间）\n")
        }

        report.append("\n================================\n")
        report.append("用户起床时间晚于预设时间+${toleranceHours}小时，请关注。")

        // ✅ 向社区后端发送警报（如果是社区守护模式）
        if (prefsManager.isCommunityEnabled()) {
            sendAlertToCommunity("起床时间晚于预设时间+${toleranceHours}小时")
        }

        // 记录发送尝试（在发送前记录）
        val notifyType = prefsManager.getNotifyType()
        // ✅ 获取当前步数和使用时长（考虑跨天）
        val (usageMinutes, currentSteps) = prefsManager.getCompleteDayActivity(this@SleepMonitorService)
                
        prefsManager.addAlertHistory(
            AlertHistoryRecord(
                timestamp = System.currentTimeMillis(),
                type = AlertType.SLEEP,
                status = AlertStatus.PENDING,
                method = when (notifyType) {
                    "phone" -> AlertMethod.SMS
                    "email" -> AlertMethod.EMAIL
                    else -> AlertMethod.NOTIFICATION
                },
                content = "起床异常：${
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(
                        Date(
                            wakeTime
                        )
                    )
                }",
                reason = "起床时间晚于预设时间 +${toleranceHours}小时",
                steps = currentSteps.toInt(),
                usageMinutes = usageMinutes.toInt()
            )
        )

        // 发送报告
        sendReport(report.toString())
        // ✅ 写入文件日志
        com.livewell.untils.AppLogger.i(tag, "📧 已发送起床异常警报")
        
        // ✅ 记录睡眠数据用于学习（只有当 confirmedSleepStartTime 有效时）
        if (confirmedSleepStartTime > 0) {
            recordSleepData(confirmedSleepStartTime, wakeTime, sleepDuration)
            Log.i(tag, "睡眠数据已记录：入睡=${confirmedSleepStartTime}, 醒来=${wakeTime}, 时长=${sleepDuration}ms")
        } else {
            Log.w(tag, "无法记录睡眠数据：confirmedSleepStartTime 无效")
        }
    }

    /**
     * 发送报告（复用之前的发送逻辑）
     */
// 删除第 363 行附近的重复 sendReport 方法定义
// 只保留一个完整的 sendReport 方法

    private fun sendReport(report: String) {
        when (prefsManager.getNotifyType()) {
            "phone" -> {
                val contact = prefsManager.getEmergencyContact()
                if (!contact.isNullOrEmpty()) {
                    // 补充步数和使用时长信息
                    val additionalInfo = generateGuardianStatusInfo()
                    val fullContent = appendGuardianStatusInfo(report, additionalInfo)
                    sendSmsReport(contact, fullContent)
                    updateLastSleepRecordStatus(AlertStatus.SUCCESS)
                }
            }

            "email" -> {
                // 补充步数和使用时长信息
                val additionalInfo = generateGuardianStatusInfo()
                val fullContent = appendGuardianStatusInfo(report, additionalInfo)
                sendEmailReport(fullContent)
                // ✅ 注意：邮件发送是异步的，状态会在 sendEmailReport 的回调中更新
            }

            else -> {
                // 默认处理
            }
        }
    }

    // 更新最后一条睡眠记录状态
    private fun updateLastSleepRecordStatus(status: AlertStatus) {
        val history = prefsManager.getAlertHistory()
        val lastSleepRecord = history.firstOrNull { record ->
            record.type == AlertType.SLEEP && record.status == AlertStatus.PENDING
        }
        
        if (lastSleepRecord != null) {
            // ✅ 从列表中移除旧记录
            history.remove(lastSleepRecord)
            
            // ✅ 添加更新后的新记录
            val updatedRecord = lastSleepRecord.copy(status = status)
            history.add(0, updatedRecord)
            
            // ✅ 直接保存整个更新后的列表，而不是再次调用 addAlertHistory
            saveAlertHistory(history)
            
            Log.i(tag, "更新睡眠警报状态：${status}")
        } else {
            Log.w(tag, "未找到待处理的睡眠警报记录")
        }
    }
    
    /**
     * 保存完整的警报历史记录
     */
    private fun saveAlertHistory(history: MutableList<AlertHistoryRecord>) {
        val json = prefsManager.getGson().toJson(history)
        prefsManager.saveAlertHistoryDirectly(history)
    }

    /**
     * 记录睡眠数据用于学习
     */
    private fun recordSleepData(sleepTime: Long, wakeTime: Long, duration: Long) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        val record = "${dateFormat.format(Date(sleepTime))}," +
                "$sleepTime,$wakeTime,$duration," +
                "${timeFormat.format(Date(sleepTime))},${timeFormat.format(Date(wakeTime))}"

        prefsManager.saveSleepRecord(record)
        
        Log.i(tag, "✅ 睡眠记录已保存：")
        Log.i(tag, "   原始数据：$record")
        Log.i(tag, "   入睡时间：${dateFormat.format(Date(sleepTime))} ${timeFormat.format(Date(sleepTime))}")
        Log.i(tag, "   醒来时间：${dateFormat.format(Date(wakeTime))} ${timeFormat.format(Date(wakeTime))}")
        Log.i(tag, "   睡眠时长：${duration / 1000 / 60} 分钟")
        
        // ✅ 写入文件日志
        com.livewell.untils.AppLogger.i(tag, "💾 睡眠记录：${dateFormat.format(Date(sleepTime))} ${timeFormat.format(Date(sleepTime))}-${timeFormat.format(Date(wakeTime))}, 时长=${duration / 1000 / 60}分钟")
    }

    /**
     * 获取当前步数
     */
    private fun getCurrentStepCount(): Int {
        if (systemStepManager.hasStepCounter()) {
            return systemStepManager.getTodaySteps()
        }

        // 后备方案：返回 0
        return 0
    }

    /**
     * 生成守护对象状态信息（步数和使用时长）
     */
    private fun generateGuardianStatusInfo(): String {
        // ✅ 使用新的方法获取考虑跨天的数据
        val (todayUsage, stepCount) = prefsManager.getCompleteDayActivity(this)
        val stepThreshold = prefsManager.getStepThreshold()
        
        val info = StringBuilder()
        info.append("\n\n========== 守护对象状态参考 ==========\n")
        info.append("📱 今日使用时长：${todayUsage}分钟\n")
        
        if (prefsManager.isStepMonitorEnabled()) {
            info.append("👣 今日步数：${stepCount}步")
            if (stepCount < stepThreshold) {
                info.append(" (低于阈值${stepThreshold}步) ⚠️\n")
            } else {
                info.append(" (正常) ✅\n")
            }
        } else {
            info.append("👣 步数监测：未开启\n")
        }
        
        info.append("======================================\n")
        info.append("请结合以上信息判断守护对象的安全状况。\n")
        
        return info.toString()
    }

    /**
     * 在报告末尾追加守护对象状态信息
     */
    private fun appendGuardianStatusInfo(originalReport: String, additionalInfo: String): String {
        val report = StringBuilder()
        report.append(originalReport)
        report.append(additionalInfo)
        return report.toString()
    }


    private fun sendSmsReport(phoneNumber: String, report: String) {
        try {
            val smsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$phoneNumber")).apply {
                putExtra("sms_body", report)
            }
            smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(smsIntent)
            Log.i(tag, "短信报告已发送")
        } catch (e: Exception) {
            Log.e(tag, "短信发送失败：${e.message}")
        }
    }


    // 3. 时间窗口验证函数
    private fun isWithinSleepTimeWindow(): Boolean {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentTimeInMinutes = currentHour * 60 + currentMinute

        val sleepStartHour = prefsManager.getSleepHour()   // 默认 23:00
        val sleepStartMinute = prefsManager.getSleepMinute() // 获取分钟
        val sleepEndHour = prefsManager.getWakeUpHour()    // 默认 06:00
        val sleepEndMinute = prefsManager.getWakeUpMinute() // 获取分钟
        
        val sleepStartTimeInMinutes = sleepStartHour * 60 + sleepStartMinute
        val sleepEndTimeInMinutes = sleepEndHour * 60 + sleepEndMinute

        // 处理跨天情况
        return if (sleepStartTimeInMinutes > sleepEndTimeInMinutes) {
            // 跨天：如 23:00 - 06:00
            currentTimeInMinutes >= sleepStartTimeInMinutes || 
            currentTimeInMinutes < sleepEndTimeInMinutes
        } else {
            // 不跨天：如 01:00 - 06:00
            currentTimeInMinutes >= sleepStartTimeInMinutes && 
            currentTimeInMinutes < sleepEndTimeInMinutes
        }
    }


    /**
     * ✅ 新增：检查用户是否还在睡觉（超过最晚起床时间）
     * 
     * 修复逻辑：
     * 1. 如果有 confirmedSleepStartTime，优先使用
     * 2. 如果没有，尝试从缓存恢复
     * 3. 如果还是没有，使用睡眠窗口开始时间反推（保守估计）
     */
    private fun checkIfUserIsStillSleeping() {
        val now = System.currentTimeMillis()
        var effectiveSleepStart = confirmedSleepStartTime
        
        // ✅ 第一步：检查是否有有效的入睡时间
        if (effectiveSleepStart <= 0) {
            Log.w(tag, "⚠️ 未检测到有效的入睡状态")
            
            // ✅ 尝试从缓存恢复
            val cachedSleepStart = prefsManager.getLong("cached_sleep_start_time", 0)
            if (cachedSleepStart > 0 && cachedSleepStart < now) {
                effectiveSleepStart = cachedSleepStart
                Log.i(tag, "✅ 从缓存恢复入睡时间：${Date(cachedSleepStart)}")
            } else {
                // ✅ 实在没有，使用睡眠窗口开始时间作为估算（保守策略）
                // 假设用户至少在睡眠窗口开始时就睡了
                val sleepWindowStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, prefsManager.getSleepHour())
                    set(Calendar.MINUTE, prefsManager.getSleepMinute())
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    // 如果是跨天睡眠（如 23:00-6:00），需要特殊处理
                    if (timeInMillis > now) {
                        add(Calendar.DAY_OF_YEAR, -1)
                    }
                }.timeInMillis
                
                effectiveSleepStart = sleepWindowStart
                Log.w(tag, "⚠️ 使用睡眠窗口开始时间作为估算：${Date(sleepWindowStart)}")
            }
        }
        
        val sleepDuration = now - effectiveSleepStart
        Log.i(tag, "检查起床状态 - 当前睡眠时间：${sleepDuration / 1000 / 60}分钟")
        
        // ✅ 读取用户配置的容忍时长
        val toleranceHours = prefsManager.getWakeUpToleranceHours()
        val toleranceMillis = toleranceHours * 60 * 60 * 1000L
        
        if (sleepDuration >= toleranceMillis) {
            Log.w(tag, "用户超过最晚起床时间仍未起床，触发警报！")
            sendOverSleepAlert(sleepDuration, toleranceHours)
        } else {
            Log.d(tag, "用户尚未超过最晚起床时间，继续监测")
        }
    }

    /**
     * 发送超时睡眠警报
     */
    private fun sendOverSleepAlert(sleepDuration: Long, toleranceHours: Int) {
        val presetHour = prefsManager.getWakeUpHour()
        val presetMinute = prefsManager.getWakeUpMinute()
    
        val report = buildOverSleepReport(sleepDuration, presetHour, presetMinute, toleranceHours)
            
        // ✅ 获取当前步数和使用时长（考虑跨天）
        val (usageMinutes, currentSteps) = prefsManager.getCompleteDayActivity(this)
    
        // 记录警报历史
        val notifyType = prefsManager.getNotifyType()
        prefsManager.addAlertHistory(
            AlertHistoryRecord(
                timestamp = System.currentTimeMillis(),
                type = AlertType.SLEEP,
                status = AlertStatus.PENDING,
                method = when (notifyType) {
                    "phone" -> AlertMethod.SMS
                    "email" -> AlertMethod.EMAIL
                    else -> AlertMethod.NOTIFICATION
                },
                content = "已持续睡眠 ${sleepDuration / 1000 / 60} 分钟",
                reason = "超过预设起床时间 +${toleranceHours}小时仍未起床",
                steps = currentSteps,  // ✅ 记录步数
                usageMinutes = usageMinutes.toInt()  // ✅ 转换为 Int
            )
        )
    
        // 发送报告
        sendReport(report)
    
        // ✅ 记录睡眠数据用于学习（只有当 confirmedSleepStartTime 有效时）
        val wakeTime = System.currentTimeMillis()
        if (confirmedSleepStartTime > 0) {
            recordSleepData(confirmedSleepStartTime, wakeTime, sleepDuration)
            Log.i(tag, "睡眠数据已记录：入睡=${confirmedSleepStartTime}, 醒来=${wakeTime}, 时长=${sleepDuration}ms")
            com.livewell.untils.AppLogger.i(tag, "💾 强制唤醒记录：入睡=${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(confirmedSleepStartTime))}, 醒来=${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(wakeTime))}")
        } else {
            Log.w(tag, "无法记录睡眠数据：confirmedSleepStartTime 无效")
            com.livewell.untils.AppLogger.w(tag, "⚠️ 强制唤醒：无法记录睡眠数据，confirmedSleepStartTime 无效")
        }
    
        // 重置睡眠状态（防止重复报警）
        confirmedSleepStartTime = 0
        latestWakeUpAlarmScheduled = false
    
        Log.i(tag, "超时睡眠警报已发送")
        // ✅ 写入文件日志
        com.livewell.untils.AppLogger.i(tag, "📧 已发送超时睡眠警报：${sleepDuration / 1000 / 60}分钟")
    }

    /**
     * 构建超时睡眠报告
     */
    private fun buildOverSleepReport(
        sleepDuration: Long,
        presetHour: Int,
        presetMinute: Int,
        toleranceHours: Int
    ): String {
        val avgSleepTime = prefsManager.getAverageSleepTime()
        val durationDiff = sleepDuration - avgSleepTime

        val report = StringBuilder()
        report.append("【${getString(R.string.app_name)}】超时睡眠警报\n")
        report.append(
            "时间：${
                SimpleDateFormat(
                    "yyyy-MM-dd HH:mm",
                    Locale.getDefault()
                ).format(Date())
            }\n"
        )
        report.append("================================\n\n")

        report.append("⏰ 预设起床时间：${String.format("%02d:%02d", presetHour, presetMinute)}\n")
        report.append("⏳ 容忍时长：${toleranceHours}小时\n")
        report.append("😴 实际睡眠时长：${sleepDuration / 1000 / 60}分钟 (${sleepDuration / 1000 / 3600}小时${(sleepDuration % 3600000) / 60000}分钟)\n")

        if (avgSleepTime > 0 && Math.abs(durationDiff) > 30 * 60 * 1000) {
            if (durationDiff > 0) {
                report.append("📊 比平时长${durationDiff / 1000 / 60}分钟 ⚠️\n")
            } else {
                report.append("📊 比平时短${-durationDiff / 1000 / 60}分钟\n")
            }
        } else {
            report.append("📊 平均睡眠时长：${avgSleepTime / 1000 / 60}分钟\n")
        }

        report.append("\n================================\n")
        report.append("用户超过预设起床时间${toleranceHours}小时仍未起床，请确认安全状况！")

        return report.toString()
    }

    // ========== SensorEventListener 回调 ==========

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // 计算加速度大小[citation:8]
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                // 合成加速度 = sqrt(x² + y² + z²)
                val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

                // 减去重力加速度（约 9.81 m/s²）得到人体运动分量[citation:4]
                val motionMagnitude = kotlin.math.abs(magnitude - 9.81f)

                val threshold = prefsManager.getAccelThreshold()

                if (motionMagnitude > threshold) {
                    // 检测到体动
                    lastMotionTime = System.currentTimeMillis()
                    motionlessStartTime = 0
                    
                    // ✅ 关键事件：立即保存缓存数据
                    if (sleepDataCache.isNotEmpty()) {
                        tryBatchSaveData()
                    }
                }

                lastAccelMagnitude = motionMagnitude
            }

            Sensor.TYPE_GYROSCOPE -> {
                // 陀螺仪作为辅助，检测旋转运动[citation:10]
                val x = Math.abs(event.values[0])
                val y = Math.abs(event.values[1])
                val z = Math.abs(event.values[2])

                // 角速度超过阈值视为有活动
                if (x > 0.5f || y > 0.5f || z > 0.5f) {
                    lastMotionTime = System.currentTimeMillis()
                    motionlessStartTime = 0
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 不需要实现
    }

    private fun sendAlertToCommunity(reason: String) {
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val serverUrl = prefsManager.getCommunityServerUrl()
                    val apiKey = prefsManager.getCommunityApiKey()
                    val userId = prefsManager.getCommunityUserId()

                    if (serverUrl.isEmpty() || apiKey.isEmpty() || userId.isEmpty()) {
                        Log.e(tag, "社区服务器配置不完整，无法发送警报")
                        return@withContext
                    }

                    // 构建警报复数据
                    val alertJson = JSONObject()
                    alertJson.put("userId", userId)
                    alertJson.put("alertType", "sleep")
                    alertJson.put("alertLevel", "warning")
                    alertJson.put("triggerTime", System.currentTimeMillis())
                    alertJson.put("resolved", false)
                    alertJson.put("reason", reason)

                    // 添加紧急联系人信息
                    val contact = prefsManager.getEmergencyContact()
                    if (!contact.isNullOrEmpty()) {
                        alertJson.put("emergencyContact", contact)
                    }

                    // 构建设备状态
                    val deviceJson = JSONObject()
                    deviceJson.put("batteryLevel", -1)
                    deviceJson.put("isCharging", false)
                    deviceJson.put("networkType", "unknown")
                    deviceJson.put("signalStrength", -1)
                    deviceJson.put(
                        "appVersion",
                        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
                    )
                    deviceJson.put("systemVersion", Build.VERSION.RELEASE)
                    alertJson.put("deviceStatus", deviceJson)

                    val jsonData = alertJson.toString()

                    // 发送到社区后端 API
                    val url = URL("$serverUrl/api/community/alert")
                    val connection = url.openConnection() as HttpURLConnection

                    try {
                        connection.requestMethod = "POST"
                        connection.connectTimeout = 15000
                        connection.readTimeout = 15000
                        connection.doOutput = true

                        // 设置请求头
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.setRequestProperty("Authorization", "Bearer $apiKey")
                        connection.setRequestProperty("X-User-ID", userId)

                        // 发送数据
                        connection.outputStream.use { os ->
                            os.write(jsonData.toByteArray())
                        }

                        // 读取响应
                        val responseCode = connection.responseCode
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            val reader = BufferedReader(InputStreamReader(connection.inputStream))
                            val response = reader.readText()
                            Log.i(tag, "✅ 社区警报发送成功：$response")
                            reader.close()
                        } else {
                            Log.e(tag, "❌ 社区警报发送失败，响应码：$responseCode")
                        }
                    } finally {
                        connection.disconnect()
                    }
                } catch (e: Exception) {
                    Log.e(tag, "❌ 发送社区警报异常：${e.message}")
                }
            }
        }
    }

    private fun sendEmailReport(report: String) {
        val securePrefs = SecurePrefsManager(this)
        val fromEmail = securePrefs.getEmailAccount()
        val authCode = securePrefs.getEmailAuthCode()
        val host = securePrefs.getSmtpHost() ?: prefsManager.getEmailSmtpHost()
        val port = securePrefs.getSmtpPort() ?: prefsManager.getEmailSmtpPort()
        val toEmail = prefsManager.getEmailTo()

        Log.i(tag, "====== 开始发送睡眠监测邮件 ======")
        Log.i(tag, "SMTP 主机：$host")
        Log.i(tag, "SMTP 端口：$port")
        Log.i(tag, "发件邮箱：$fromEmail")
        Log.i(tag, "收件邮箱：$toEmail")
        
        // ✅ 新增：详细检查每个字段
        Log.d(tag, "fromEmail 是否为空：${fromEmail.isNullOrEmpty()}")
        Log.d(tag, "authCode 是否为空：${authCode.isNullOrEmpty()}")
        Log.d(tag, "toEmail 是否为空：${toEmail.isNullOrEmpty()}")
        
        // ✅ 新增：记录当前时间，用于诊断频率限制问题
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        Log.i(tag, "⏰ 当前时间：$currentTime")
        
        // ✅ 新增：检查距离上次邮件发送的时间间隔
        val lastEmailTime = prefsManager.getLastAlertTime()
        if (lastEmailTime > 0) {
            val timeDiff = System.currentTimeMillis() - lastEmailTime
            Log.i(tag, "⏱️ 距离上次邮件发送：${timeDiff / 1000}秒 (${timeDiff / 60000}分钟)")
            
            // 如果间隔小于5分钟，可能是频率限制
            if (timeDiff < 5 * 60 * 1000) {
                Log.w(tag, "⚠️ 警告：距离上次邮件发送不足5分钟，可能被SMTP服务器限制")
            }
        }

        if (fromEmail.isNullOrEmpty() || authCode.isNullOrEmpty() || toEmail.isNullOrEmpty()) {
            Log.e(tag, "邮件配置不完整，发送失败")
            Log.e(tag, "请检查设置 → 功能设置 → 睡眠监测设置 → 邮箱配置")
            updateLastSleepRecordStatus(AlertStatus.FAILED)
            return
        }

        val mailSender = MailSender()
        mailSender.sendEmail(
            host = host,
            port = port,
            fromEmail = fromEmail,
            authCode = authCode,
            toEmail = toEmail,
            subject = "【${getString(R.string.app_name)}】起床异常报告",
            content = report,
            callback = object : MailSender.SendCallback {
                override fun onSuccess() {
                    Log.i(tag, "✅ 睡眠监测邮件发送成功")
                    updateLastSleepRecordStatus(AlertStatus.SUCCESS)
                }

                override fun onError(error: String) {
                    Log.e(tag, "❌ 睡眠监测邮件发送失败：$error")
                    // ✅ 写入文件日志
                    com.livewell.untils.AppLogger.e(tag, "❌ 起床异常警报邮件发送失败：$error")
                    
                    // ✅ 重试机制：如果是临时错误，5 分钟后重试
                    val prefs = getSharedPreferences("livewell_prefs", Context.MODE_PRIVATE)
                    val retryCount = prefs.getInt("email_retry_count", 0)
                    if (retryCount < 3 && isRetryableError(error)) {
                        Log.i(tag, "🔄 将在 5 分钟后重试（第 ${retryCount + 1}/3 次）")
                        prefs.edit().putInt("email_retry_count", retryCount + 1).apply()
                        scheduleEmailRetry(report)
                    } else {
                        Log.e(tag, "❌ 达到最大重试次数或不可重试错误，标记为失败")
                        updateLastSleepRecordStatus(AlertStatus.FAILED)
                        prefs.edit().putInt("email_retry_count", 0) // 重置计数器
                    }
                }
            },
            context = this
        )
    }
    
    /**
     * ✅ 判断是否是可重试的错误
     */
    private fun isRetryableError(error: String): Boolean {
        val retryableErrors = listOf(
            "Connection", 
            "timeout", 
            "Network", 
            "Socket", 
            "SSLHandshake",
            "UnknownHost" // 临时 DNS 问题
        )
        return retryableErrors.any { error.contains(it, ignoreCase = true) }
    }
    
    /**
     * ✅ 调度邮件重试
     */
    private fun scheduleEmailRetry(report: String) {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val retryTime = System.currentTimeMillis() + 5 * 60 * 1000L // 5 分钟后
            
            val intent = Intent(this, SleepMonitorService::class.java).apply {
                action = "ACTION_RETRY_EMAIL"
                putExtra("retry_report", report)
            }
            
            val pendingIntent = PendingIntent.getService(
                this, 3001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    retryTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    retryTime,
                    pendingIntent
                )
            }
            
            Log.i(tag, "📅 邮件重试已调度：${android.icu.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(retryTime)}")
        } catch (e: Exception) {
            Log.e(tag, "调度邮件重试失败", e)
        }
    }
    
    // ==================== 睡前快照相关功能 ====================
    
    /**
     * ✅ 启动定期活动快照任务（每 5 分钟）
     */
    private fun startPeriodicActivitySnapshot() {
        executor.scheduleAtFixedRate({
            try {
                if (confirmedSleepStartTime <= 0) {
                    // 只在未入睡时更新
                    val currentSteps = getCurrentStepCount()
                    val currentUsage = UsageStatsHelper(this).getTodayAppUsageMinutes().toInt()
                    
                    prefsManager.saveLastActiveState(
                        steps = currentSteps,
                        usageMinutes = currentUsage,
                        timestamp = System.currentTimeMillis()
                    )
                    
                    Log.d(tag, "📸 活动快照已保存：步数=$currentSteps, 使用时长=${currentUsage}分钟")
                }
            } catch (e: Exception) {
                Log.e(tag, "❌ 保存活动快照失败：${e.message}", e)
            }
        }, 0, 5, java.util.concurrent.TimeUnit.MINUTES)
        
        Log.i(tag, "✅ 定期活动快照任务已启动（每 5 分钟）")
    }
    
    /**
     * ✅ 记录睡前数据快照（解决跨天问题）
     * 在用户入睡时，保存当前的步数和使用时长作为"前一天"的最终数据
     */
    private fun recordPreSleepDataSnapshot(sleepTime: Long) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val yesterday = Calendar.getInstance().apply {
                time = Date(sleepTime)
                add(Calendar.DAY_OF_YEAR, -1)
            }
            val yesterdayStr = dateFormat.format(yesterday.time)
            
            // 优先使用最近的活动快照，而不是实时数据
            val lastActiveState = prefsManager.getLastActiveState()
            val (lastActiveSteps, lastActiveUsage, lastActiveTime) = lastActiveState
            
            // 如果最近 10 分钟内有活动记录，使用它（更准确）
            val useSnapshot = (System.currentTimeMillis() - lastActiveTime) < 10 * 60 * 1000
            
            val stepsToSave = if (useSnapshot && lastActiveSteps > 0) {
                lastActiveSteps
            } else {
                getCurrentStepCount()
            }
            
            val usageToSave = if (useSnapshot && lastActiveUsage > 0) {
                lastActiveUsage
            } else {
                UsageStatsHelper(this).getTodayAppUsageMinutes().toInt()
            }
            
            // 保存睡前快照
            prefsManager.savePreSleepSnapshot(
                date = yesterdayStr,
                steps = stepsToSave,
                usageMinutes = usageToSave,
                sleepTime = sleepTime
            )
            
            // 清理旧快照
            prefsManager.cleanupOldSnapshots(yesterdayStr)
            
            Log.i(tag, "✅ 睡前数据快照已保存：")
            Log.i(tag, "   日期：$yesterdayStr")
            Log.i(tag, "   步数：$stepsToSave 步")
            Log.i(tag, "   使用时长：$usageToSave 分钟")
            Log.i(tag, "   入睡时间：${dateFormat.format(Date(sleepTime))}")
            Log.i(tag, "   数据来源：${if (useSnapshot) "活动快照" else "实时数据"}")
            
            // ✅ 写入文件日志
            com.livewell.untils.AppLogger.i(tag, "睡前快照已保存：日期=$yesterdayStr, 步数=$stepsToSave, 使用=${usageToSave}分钟")
            
        } catch (e: Exception) {
            Log.e(tag, "❌ 保存睡前数据快照失败：${e.message}", e)
        }
    }
}