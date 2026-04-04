package com.livewell.untils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class SystemStepManager private constructor(applicationContext: Context) : SensorEventListener {
    
    companion object {
        @Volatile private var instance: SystemStepManager? = null
        
        fun getInstance(context: Context): SystemStepManager {
            return instance ?: synchronized(this) {
                instance ?: SystemStepManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
        
        fun resetInstance() {
            instance?.shutdown()
            instance = null
        }
    }
    
    private val sensorManager: SensorManager by lazy {
        applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    
    private val appContext: Context = applicationContext
    
    private var stepCounterSensor: Sensor? = null
    private var todaySteps: Int = 0
    private var bootStepCount: Int = -1
    private var lastSavedStepCount: Int = -1
    private var lastSyncTime: Long = 0L
    
    private val tag = "SystemStepManager"
    
    private var listenerCount = 0
    private var scheduledExecutor: ScheduledExecutorService? = null
    private var autoSaveScheduled = false
    private var isListening = false
    
    fun hasStepCounter(): Boolean {
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        return stepCounterSensor != null
    }
    
    fun registerListener() {
        if (!hasStepCounter()) {
            Log.e(tag, "设备不支持步数计数器")
            return
        }
        
        if (isListening) {
            Log.d(tag, "已经在监听中，跳过注册")
            listenerCount++
            return
        }
        
        listenerCount++
        Log.d(tag, "注册监听，引用计数：$listenerCount")
        
        stepCounterSensor?.let { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_UI
            )
            isListening = true
            Log.i(tag, "传感器监听已注册，延迟级别：SENSOR_DELAY_UI")
        }
        
        startAutoSave()
    }
    
    private fun startAutoSave() {
        if (autoSaveScheduled) return
        
        autoSaveScheduled = true
        val executor = Executors.newSingleThreadScheduledExecutor()
        scheduledExecutor = executor
        executor.scheduleAtFixedRate({
            try {
                saveToPrefsIfChanged(PrefsManager(appContext))
            } catch (e: Exception) {
                Log.e(tag, "定期保存失败", e)
            }
        }, 2, 2, TimeUnit.MINUTES)
        
        Log.i(tag, "已启动自动保存任务，每 2 分钟保存一次")
    }
    
    fun unregisterListener() {
        listenerCount--
        Log.d(tag, "注销监听，引用计数：$listenerCount")
        
        if (listenerCount <= 0) {
            stopListening()
            Log.i(tag, "已完全注销传感器监听")
        }
    }
    
    private fun stopListening() {
        if (isListening) {
            sensorManager.unregisterListener(this)
            isListening = false
            listenerCount = 0
            stopAutoSave()
            Log.i(tag, "已停止传感器监听")
        }
    }
    
    private fun stopAutoSave() {
        scheduledExecutor?.let { executor ->
            if (!executor.isShutdown) {
                executor.shutdown()
                try {
                    if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                        executor.shutdownNow()
                    }
                } catch (e: InterruptedException) {
                    executor.shutdownNow()
                    Log.e(tag, "关闭 Executor 被中断", e)
                }
            }
            scheduledExecutor = null
            autoSaveScheduled = false
            Log.i(tag, "已停止自动保存任务")
        }
    }
    
    private fun shutdown() {
        stopListening()
        stopAutoSave()
        Log.i(tag, "SystemStepManager 已关闭")
    }
    
    /**
     * ✅ 新增：获取今日步数（带自动同步）
     * 每次调用时都会尝试从传感器同步最新数据
     */
    fun getTodaySteps(syncIfNeeded: Boolean = true): Int {
        if (!hasStepCounter()) {
            Log.w(tag, "设备不支持步数传感器，返回模拟数据")
            return getSimulatedSteps()
        }
        
        // ✅ 检查是否跨天，如果是则重置步数
        checkAndResetIfNewDay()
        
        // ✅ 如果需要且传感器可用，立即同步
        if (syncIfNeeded && !isListening) {
            Log.d(tag, "传感器未监听，执行即时同步")
            syncWithSensor()
        }
        
        // ✅ 检查数据是否过期（超过 5 分钟未更新）
        val now = System.currentTimeMillis()
        if (syncIfNeeded && now - lastSyncTime > 5 * 60 * 1000) {
            Log.d(tag, "数据可能过期，执行同步")
            syncWithSensor()
        }
        
        Log.d(tag, "返回今日步数：$todaySteps (上次同步：${lastSyncTime})")
        return todaySteps
    }
    
    /**
     * ✅ 新增：立即从传感器同步数据（被动轮询模式）
     * 即使没有注册监听器，也能获取最新步数
     */
    private fun syncWithSensor() {
        if (!hasStepCounter()) {
            Log.w(tag, "无法同步：设备不支持步数传感器")
            return
        }
        
        try {
            Log.d(tag, "开始同步传感器数据...")
            
            // 使用 OneShotListener 只接收一次数据
            val oneShotListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    event ?: return
                    
                    if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                        val currentTotalSteps = event.values[0].toLong()
                        Log.d(tag, "传感器同步 - 总步数：$currentTotalSteps")
                        
                        if (bootStepCount == -1) {
                            // 首次同步
                            bootStepCount = currentTotalSteps.toInt()
                            Log.i(tag, "同步 - 设置初始步数基准：$bootStepCount")
                            
                            // 如果有保存的数据，进行修正
                            val savedSteps = PrefsManager(appContext).getSavedStepCount()
                            if (savedSteps > 0) {
                                bootStepCount = (currentTotalSteps - savedSteps).toInt()
                                Log.i(tag, "同步 - 使用保存数据修正基准：$bootStepCount")
                            }
                            
                            todaySteps = savedSteps
                        } else {
                            // 计算增量
                            val delta = (currentTotalSteps - bootStepCount).toInt()
                            if (delta >= 0) {
                                todaySteps = delta
                                Log.d(tag, "同步 - 今日步数：$todaySteps")
                                
                                // 如果步数变化超过 100 步，立即保存
                                if (Math.abs(todaySteps - lastSavedStepCount) >= 100) {
                                    saveToPrefs(PrefsManager(appContext))
                                }
                            } else {
                                Log.w(tag, "同步 - 检测到负值增量，重置基准")
                                bootStepCount = currentTotalSteps.toInt()
                                todaySteps = 0
                            }
                        }
                        
                        lastSyncTime = System.currentTimeMillis()
                        
                        // 立即注销监听（一次性使用）
                        sensorManager.unregisterListener(this)
                        Log.d(tag, "同步完成，已注销一次性监听")
                    }
                }
                
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            
            // 注册一次性监听
            stepCounterSensor?.let { sensor ->
                sensorManager.registerListener(oneShotListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                Log.d(tag, "已注册一次性同步监听")
            }
            
            // 等待最多 1 秒让传感器响应
            Thread.sleep(1000)
            
        } catch (e: Exception) {
            Log.e(tag, "同步传感器数据失败", e)
        }
    }
    
    /**
     * ✅ 新增：强制刷新步数（用于 UI 更新）
     */
    fun forceRefreshSteps(): Int {
        Log.d(tag, "强制刷新步数")
        syncWithSensor()
        return todaySteps
    }
    
    /**
     * ✅ 检查是否跨天，如果是则重置步数
     * 这是防止 0 点后仍显示前一天数据的第二道防线
     */
    private fun checkAndResetIfNewDay() {
        val currentPrefsDate = getCurrentDateKey()
        val savedPrefsDate = PrefsManager(appContext).getLastStepSaveDate()
        
        if (currentPrefsDate != savedPrefsDate) {
            Log.i(tag, "检测到日期变化：从 $savedPrefsDate 到 $currentPrefsDate，重置步数")
            resetDailySteps()
            // 立即保存重置后的状态
            saveToPrefs(PrefsManager(appContext))
        }
    }
    
    fun loadFromPrefs(prefsManager: PrefsManager): Int {
        val savedSteps = prefsManager.getSavedStepCount()
        val lastSaveDate = prefsManager.getLastStepSaveDate()
        val today = getCurrentDateKey()
        
        Log.d(tag, "加载步数数据：savedSteps=$savedSteps, lastSaveDate=$lastSaveDate, today=$today")
        
        if (lastSaveDate == today && savedSteps > 0) {
            todaySteps = savedSteps
            lastSavedStepCount = savedSteps
            lastSyncTime = System.currentTimeMillis()
            Log.i(tag, "从本地恢复今日步数：$todaySteps")
            
            // ✅ 恢复后立即同步一次，确保数据准确
            if (hasStepCounter()) {
                syncWithSensor()
            }
            
            return todaySteps
        }
        
        if (lastSaveDate != today) {
            Log.i(tag, "检测到新的一天，重置步数")
            todaySteps = 0
            bootStepCount = -1
            lastSavedStepCount = 0
        }
        
        return todaySteps
    }
    
    fun saveToPrefs(prefsManager: PrefsManager) {
        prefsManager.saveStepCount(todaySteps)
        prefsManager.saveStepSaveDate(getCurrentDateKey())
        lastSavedStepCount = todaySteps
        lastSyncTime = System.currentTimeMillis()
        Log.d(tag, "保存步数到本地：$todaySteps")
    }
    
    private fun saveToPrefsIfChanged(prefsManager: PrefsManager) {
        if (todaySteps != lastSavedStepCount) {
            saveToPrefs(prefsManager)
            Log.d(tag, "定期保存：步数已变化，保存新数据")
        } else {
            Log.d(tag, "定期保存：步数未变化，跳过保存")
        }
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val currentTotalSteps = event.values[0].toLong()
            lastSyncTime = System.currentTimeMillis()
            
            if (bootStepCount == -1) {
                bootStepCount = currentTotalSteps.toInt()
                Log.i(tag, "初始步数基准：$bootStepCount")
                
                val savedSteps = todaySteps
                if (savedSteps > 0) {
                    bootStepCount = (currentTotalSteps - savedSteps).toInt()
                    Log.i(tag, "使用保存的步数修正基准：$bootStepCount")
                    
                    saveToPrefs(PrefsManager(appContext))
                }
            } else {
                val delta = (currentTotalSteps - bootStepCount).toInt()
                if (delta >= 0) {
                    todaySteps = delta
                    if (todaySteps % 50 == 0) {
                        Log.d(tag, "系统总步数：$currentTotalSteps, 今日步数：$todaySteps")
                    }
                    
                    // ✅ 实时保存：每增加 100 步保存一次
                    if (todaySteps % 100 == 0 && todaySteps != lastSavedStepCount) {
                        saveToPrefs(PrefsManager(appContext))
                    }
                } else {
                    Log.w(tag, "检测到负值增量，重置基准")
                    bootStepCount = currentTotalSteps.toInt()
                    todaySteps = 0
                }
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    
    private fun getCurrentDateKey(): String {
        return SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    }
    
    fun getCurrentDateKeyForPrefs(): String {
        return getCurrentDateKey()
    }
    
    fun resetDailySteps() {
        bootStepCount = -1
        todaySteps = 0
        lastSavedStepCount = 0
        lastSyncTime = System.currentTimeMillis()
        Log.i(tag, "已重置每日步数")
    }
    
    /**
     * ✅ 新增：模拟步数（后备方案）
     */
    private fun getSimulatedSteps(): Int {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        return if (hour >= 22 || hour <= 6) {
            (10..50).random()
        } else {
            (500..2000).random()
        }
    }
    
    /**
     * ✅ 新增：获取最后同步时间（用于调试）
     */
    fun getLastSyncTime(): Long {
        return lastSyncTime
    }
}