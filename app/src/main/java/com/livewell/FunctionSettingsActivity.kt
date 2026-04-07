package com.livewell

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.*
import android.widget.NumberPicker
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.livewell.service.AccessibilityKeepAliveService
import com.livewell.service.CheckinService
import com.livewell.service.SilentMusicService
import com.livewell.untils.PrefsManager
import com.livewell.untils.SystemStepManager
import com.livewell.untils.UsageStatsHelper

/**
 * 功能设置全屏 Activity
 * 从 MainActivity.showSmartSettingsDialog() 迁移的完整逻辑
 */
class FunctionSettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FunctionSettings"
    }

    private lateinit var prefs: PrefsManager
    private lateinit var usageStatsHelper: UsageStatsHelper
    
    // ========== 统计数据 ==========
    private lateinit var tvAppUsage: TextView
    private lateinit var tvStepCount: TextView
    
    // ========== 导航卡片 ==========
    private lateinit var cardUsageSettings: MaterialCardView
    private lateinit var cardExercise: MaterialCardView
    private lateinit var cardSleep: MaterialCardView
    
    // ========== 自动报警模式 ==========
    private lateinit var switchAutoAlarm: SwitchMaterial
    private lateinit var radioGroupAlarmMode: RadioGroup
    private lateinit var radioHybrid: RadioButton
    private lateinit var radioTimeOnly: RadioButton
    private lateinit var radioStepsOnly: RadioButton
    
    // ========== 警报确认机制 ==========
    private lateinit var switchConfirmMechanism: SwitchMaterial
    private lateinit var btnDecreaseDuration: ImageButton
    private lateinit var btnIncreaseDuration: ImageButton
    private lateinit var tvDuration: TextView
    
    // ========== 每日报警检查时间 ==========
    private lateinit var btnHourUp: ImageButton
    private lateinit var btnHourDown: ImageButton
    private lateinit var tvHour: TextView
    private lateinit var btnMinuteUp: ImageButton
    private lateinit var btnMinuteDown: ImageButton
    private lateinit var tvMinute: TextView
    
    // ========== 保活功能 ==========
    private lateinit var switchSilentMusic: SwitchMaterial  // 后台保活增强
    private lateinit var switchProMode: SwitchMaterial       // 终极保活模式
    
    // ========== 状态变量 ==========
    private var alertDuration: Int = 5
    private var alertCheckHour: Int = 20
    private var alertCheckMinute: Int = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_function_settings)
        
        // 初始化工具类
        prefs = PrefsManager(this)
        usageStatsHelper = UsageStatsHelper(this)
        
        // 初始化视图
        initViews()
        
        // 加载设置
        loadSettings()
        
        // 设置监听器
        setupListeners()
    }
    
    /**
     * 初始化所有视图引用
     */
    private fun initViews() {
        // 统计数据
        tvAppUsage = findViewById(R.id.tvAppUsage)
        tvStepCount = findViewById(R.id.tvStepCount)
        
        // 导航卡片
        cardUsageSettings = findViewById(R.id.cardUsageSettings)
        cardExercise = findViewById(R.id.cardExercise)
        cardSleep = findViewById(R.id.cardSleep)
        
        // 自动报警模式
        switchAutoAlarm = findViewById(R.id.switchAutoAlarm)
        radioGroupAlarmMode = findViewById(R.id.radioGroupAlarmMode)
        radioHybrid = findViewById(R.id.radioHybrid)
        radioTimeOnly = findViewById(R.id.radioTimeOnly)
        radioStepsOnly = findViewById(R.id.radioStepsOnly)
        
        // 警报确认机制
        switchConfirmMechanism = findViewById(R.id.switchConfirmMechanism)
        btnDecreaseDuration = findViewById(R.id.btnDecreaseDuration)
        btnIncreaseDuration = findViewById(R.id.btnIncreaseDuration)
        tvDuration = findViewById(R.id.tvDuration)
        
        // 每日报警检查时间
        btnHourUp = findViewById(R.id.btnHourUp)
        btnHourDown = findViewById(R.id.btnHourDown)
        tvHour = findViewById(R.id.tvHour)
        btnMinuteUp = findViewById(R.id.btnMinuteUp)
        btnMinuteDown = findViewById(R.id.btnMinuteDown)
        tvMinute = findViewById(R.id.tvMinute)
        
        // 保活功能
        switchSilentMusic = findViewById(R.id.switchSilentMusic)
        switchProMode = findViewById(R.id.switchProMode)
        
        // 返回按钮
        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }
    
    /**
     * 加载所有设置数据
     */
    private fun loadSettings() {
        // 1. 加载统计数据
        loadStatisticsData()
        
        // 2. 加载警报确认机制
        alertDuration = prefs.getAlertDuration()
        tvDuration.text = alertDuration.toString()
        switchConfirmMechanism.isChecked = prefs.isAlertConfirmEnabled()
        
        // 3. 加载报警检查时间
        alertCheckHour = prefs.getAlertCheckHour()
        alertCheckMinute = prefs.getAlertCheckMinute()
        updateAlertTimeDisplay()
        
        // 4. 加载自动报警模式
        val autoAlarmEnabled = prefs.isAutoAlertModeEnabled()
        switchAutoAlarm.isChecked = autoAlarmEnabled
        
        when (prefs.getAlertCriteria()) {
            PrefsManager.CRITERIA_MIXED -> radioHybrid.isChecked = true
            PrefsManager.CRITERIA_USAGE_ONLY -> radioTimeOnly.isChecked = true
            PrefsManager.CRITERIA_STEP_ONLY -> radioStepsOnly.isChecked = true
        }
        
        updateAlarmModeVisibility(autoAlarmEnabled)
        
        // 5. 加载保活功能
        switchSilentMusic.isChecked = prefs.isSilentMusicEnabled()
        switchProMode.isChecked = prefs.isAccessibilityServiceEnabled()
        updateAccessibilityServiceState()
        
        // 6. 启动/停止无声音乐服务
        updateSilentMusicService()
    }
    
    /**
     * 加载统计数据（使用时长和步数）
     */
    private fun loadStatisticsData() {
        // 强制刷新步数
        try {
            SystemStepManager.getInstance(this).forceRefreshSteps()
            Log.d(TAG, "打开功能设置 - 强制刷新步数")
        } catch (e: Exception) {
            Log.e(TAG, "刷新步数失败", e)
        }
        
        // 获取使用时长
        val appUsageMinutes = usageStatsHelper.getTodayAppUsageMinutes()
        val hours = appUsageMinutes / 60
        val mins = appUsageMinutes % 60
        tvAppUsage.text = String.format("%d.%02d", hours, (mins * 100) / 60)
        
        // 获取步数
        val stepCount = getTodayStepCount()
        val stepThreshold = prefs.getStepThreshold()
        tvStepCount.text = String.format("%,d", stepCount)
        
        // 根据完成情况设置颜色
        if (prefs.isStepMonitorEnabled()) {
            if (stepCount >= stepThreshold) {
                tvStepCount.setTextColor(getColor(R.color.success))
            } else {
                tvStepCount.setTextColor(getColor(R.color.error))
            }
        } else {
            tvStepCount.setTextColor(getColor(R.color.text_secondary))
        }
    }
    
    /**
     * 设置所有监听器
     */
    private fun setupListeners() {
        // 1. 导航卡片点击事件
        setupNavigationCards()
        
        // 2. 自动报警模式
        setupAutoAlarmMode()
        
        // 3. 警报确认机制
        setupConfirmMechanism()
        
        // 4. 报警检查时间
        setupAlertCheckTime()
        
        // 5. 保活功能
        setupKeepAliveFeatures()
    }
    
    /**
     * 设置导航卡片点击事件
     */
    private fun setupNavigationCards() {
        cardUsageSettings.setOnClickListener {
            showUsageSettingsDialog()
        }
        
        cardExercise.setOnClickListener {
            showStepSettingsDialog()
        }
        
        cardSleep.setOnClickListener {
            showSleepSettingsDialog()
        }
    }
    
    /**
     * 设置自动报警模式
     */
    private fun setupAutoAlarmMode() {
        switchAutoAlarm.setOnCheckedChangeListener { _, isChecked ->
            prefs.setAutoAlertModeEnabled(isChecked)
            updateAlarmModeVisibility(isChecked)
        }
        
        radioGroupAlarmMode.setOnCheckedChangeListener { _, checkedId ->
            val criteria = when (checkedId) {
                R.id.radioHybrid -> PrefsManager.CRITERIA_MIXED
                R.id.radioTimeOnly -> PrefsManager.CRITERIA_USAGE_ONLY
                R.id.radioStepsOnly -> PrefsManager.CRITERIA_STEP_ONLY
                else -> PrefsManager.CRITERIA_MIXED
            }
            prefs.setAlertCriteria(criteria)
        }
    }
    
    /**
     * 设置警报确认机制
     */
    private fun setupConfirmMechanism() {
        switchConfirmMechanism.setOnCheckedChangeListener { _, isChecked ->
            prefs.setAlertConfirmEnabled(isChecked)
        }
        
        btnDecreaseDuration.setOnClickListener {
            if (alertDuration > 1) {
                alertDuration--
                tvDuration.text = alertDuration.toString()
            }
        }
        
        btnIncreaseDuration.setOnClickListener {
            if (alertDuration < 30) {
                alertDuration++
                tvDuration.text = alertDuration.toString()
            }
        }
    }
    
    /**
     * 设置报警检查时间（循环轮转）
     */
    private fun setupAlertCheckTime() {
        // 小时调整 - 循环轮转 0-23
        btnHourUp.setOnClickListener {
            alertCheckHour = if (alertCheckHour < 23) alertCheckHour + 1 else 0
            updateAlertTimeDisplay()
        }
        
        btnHourDown.setOnClickListener {
            alertCheckHour = if (alertCheckHour > 0) alertCheckHour - 1 else 23
            updateAlertTimeDisplay()
        }
        
        // 分钟调整 - 循环轮转 0-59（步长1）
        btnMinuteUp.setOnClickListener {
            alertCheckMinute = if (alertCheckMinute < 59) alertCheckMinute + 1 else 0
            updateAlertTimeDisplay()
        }
        
        btnMinuteDown.setOnClickListener {
            alertCheckMinute = if (alertCheckMinute > 0) alertCheckMinute - 1 else 59
            updateAlertTimeDisplay()
        }
    }
    
    /**
     * 设置保活功能
     */
    private fun setupKeepAliveFeatures() {
        // 后台保活增强（无声音乐）
        switchSilentMusic.setOnCheckedChangeListener { _, isChecked ->
            prefs.setSilentMusicEnabled(isChecked)
            updateSilentMusicService()
            Toast.makeText(
                this,
                if (isChecked) "后台保活增强已开启" else "后台保活增强已关闭",
                Toast.LENGTH_SHORT
            ).show()
        }
        
        // 终极保活模式（辅助功能）
        switchProMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.setAccessibilityServiceEnabled(isChecked)
            if (isChecked) {
                // 打开辅助功能设置页面
                AccessibilityKeepAliveService().openAccessibilitySettings()
                Toast.makeText(this, "请在辅助功能设置中开启服务", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "终极保活模式已关闭", Toast.LENGTH_SHORT).show()
            }
            updateAccessibilityServiceState()
        }
    }
    
    /**
     * 更新报警时间显示
     */
    private fun updateAlertTimeDisplay() {
        tvHour.text = String.format("%02d", alertCheckHour)
        tvMinute.text = String.format("%02d", alertCheckMinute)
    }
    
    /**
     * 更新报警模式选项可见性
     */
    private fun updateAlarmModeVisibility(isVisible: Boolean) {
        radioGroupAlarmMode.visibility = if (isVisible) View.VISIBLE else View.GONE
    }
    
    /**
     * 获取今日步数
     */
    private fun getTodayStepCount(): Int {
        return try {
            SystemStepManager.getInstance(this).getTodaySteps(syncIfNeeded = true)
        } catch (e: Exception) {
            Log.e(TAG, "获取步数失败", e)
            0
        }
    }
    
    /**
     * 更新无声音乐服务状态
     */
    private fun updateSilentMusicService() {
        if (prefs.isSilentMusicEnabled()) {
            SilentMusicService.start(this)
            Log.d(TAG, "无声音乐播放服务已启动")
        } else {
            SilentMusicService.stop(this)
            Log.d(TAG, "无声音乐播放服务已停止")
        }
    }
    
    /**
     * 更新辅助功能服务状态
     */
    private fun updateAccessibilityServiceState() {
        val isEnabled = prefs.isAccessibilityServiceEnabled()
        val isActuallyEnabled = AccessibilityKeepAliveService.isEnabled(this)
        Log.d(TAG, "辅助功能服务状态：设置=$isEnabled, 实际=$isActuallyEnabled")
    }
    
    /**
     * 重启 CheckinService
     */
    private fun restartCheckinService() {
        try {
            stopService(Intent(this, CheckinService::class.java))
            Handler(Looper.getMainLooper()).postDelayed({
                startService(Intent(this, CheckinService::class.java))
            }, 100)
        } catch (e: Exception) {
            Log.e(TAG, "重启服务失败", e)
        }
    }
    
    // ========== 子对话框方法 ==========
    
    /**
     * 显示设备使用时长设置对话框
     */
    private fun showUsageSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_usage_settings, null)
        val switchSmartMode = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchSmartMode)
        val cardThresholds = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardThresholds)
        val tvAppUsageValue = dialogView.findViewById<TextView>(R.id.tvAppUsageValue)
        val btnAppUsageMinus = dialogView.findViewById<Button>(R.id.btnAppUsageMinus)
        val btnAppUsagePlus = dialogView.findViewById<Button>(R.id.btnAppUsagePlus)
        
        var appUsageThreshold = prefs.getAppUsageThreshold()
        
        tvAppUsageValue.text = appUsageThreshold.toString()
        switchSmartMode.isChecked = prefs.isSmartModeEnabled()
        
        cardThresholds.alpha = if (switchSmartMode.isChecked) 0.5f else 1.0f
        cardThresholds.isEnabled = !switchSmartMode.isChecked
        
        switchSmartMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.setSmartModeEnabled(isChecked)
            cardThresholds.alpha = if (isChecked) 0.5f else 1.0f
            cardThresholds.isEnabled = !isChecked
        }
        
        btnAppUsageMinus.setOnClickListener {
            if (appUsageThreshold > 1) {
                appUsageThreshold--
                tvAppUsageValue.text = appUsageThreshold.toString()
            }
        }
        
        btnAppUsagePlus.setOnClickListener {
            if (appUsageThreshold < 120) {
                appUsageThreshold++
                tvAppUsageValue.text = appUsageThreshold.toString()
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("设备使用时长设置")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                prefs.setAppUsageThreshold(appUsageThreshold)
                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示运动监测设置对话框
     */
    private fun showStepSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_step_settings, null)
        val switchStepMonitor = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchStepMonitor)
        val cardStepThreshold = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardStepThreshold)
        val tvStepValue = dialogView.findViewById<TextView>(R.id.tvStepValue)
        val btnStepMinus = dialogView.findViewById<Button>(R.id.btnStepMinus)
        val btnStepPlus = dialogView.findViewById<Button>(R.id.btnStepPlus)
        
        var stepThreshold = prefs.getStepThreshold()
        tvStepValue.text = stepThreshold.toString()
        switchStepMonitor.isChecked = prefs.isStepMonitorEnabled()
        
        cardStepThreshold.alpha = if (switchStepMonitor.isChecked) 1.0f else 0.5f
        cardStepThreshold.isEnabled = switchStepMonitor.isChecked
        
        switchStepMonitor.setOnCheckedChangeListener { _, isChecked ->
            cardStepThreshold.alpha = if (isChecked) 1.0f else 0.5f
            cardStepThreshold.isEnabled = isChecked
        }
        
        btnStepMinus.setOnClickListener {
            if (stepThreshold > 10) {
                stepThreshold -= 10
                tvStepValue.text = stepThreshold.toString()
            }
        }
        
        btnStepPlus.setOnClickListener {
            if (stepThreshold < 1000) {
                stepThreshold += 10
                tvStepValue.text = stepThreshold.toString()
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("运动监测设置")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                prefs.setStepMonitorEnabled(switchStepMonitor.isChecked)
                prefs.setStepThreshold(stepThreshold)
                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示睡眠监测设置对话框
     */
    private fun showSleepSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_sleep_settings, null)
        val switchSleepMonitor = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchSleepMonitor)
        val cardSleepMode = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardSleepMode)
        val cardWakeTime = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardWakeTime)
        val cardThresholds = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardThresholds)
        val radioPowerSaving = dialogView.findViewById<RadioButton>(R.id.radioPowerSaving)
        val radioBalanced = dialogView.findViewById<RadioButton>(R.id.radioBalanced)
        val tvModeDescription = dialogView.findViewById<TextView>(R.id.tvModeDescription)
        val layoutGyro = dialogView.findViewById<LinearLayout>(R.id.layoutGyro)
        val switchGyro = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchGyro)
        val layoutAccelThreshold = dialogView.findViewById<LinearLayout>(R.id.layoutAccelThreshold)
        val tvInactiveValue = dialogView.findViewById<TextView>(R.id.tvInactiveValue)
        val tvStepValue = dialogView.findViewById<TextView>(R.id.tvStepValue)
        val tvAccelValue = dialogView.findViewById<TextView>(R.id.tvAccelValue)
        val btnInactiveMinus = dialogView.findViewById<Button>(R.id.btnInactiveMinus)
        val btnInactivePlus = dialogView.findViewById<Button>(R.id.btnInactivePlus)
        val btnStepMinus = dialogView.findViewById<Button>(R.id.btnStepMinus)
        val btnStepPlus = dialogView.findViewById<Button>(R.id.btnStepPlus)
        val btnAccelMinus = dialogView.findViewById<Button>(R.id.btnAccelMinus)
        val btnAccelPlus = dialogView.findViewById<Button>(R.id.btnAccelPlus)
        val tvAvgSleepTime = dialogView.findViewById<TextView>(R.id.tvAvgSleepTime)
        val btnViewHistory = dialogView.findViewById<Button>(R.id.btnViewHistory)
        
        val isEnabled = prefs.isSleepMonitorEnabled()
        val sleepMode = prefs.getSleepMode()
        val isGyroEnabled = prefs.isGyroEnabled()
        
        switchSleepMonitor.isChecked = isEnabled
        
        if (sleepMode == "power_saving") {
            radioPowerSaving.isChecked = true
            tvModeDescription.text = "省电模式：仅使用屏幕和步数判定，功耗<1%"
            layoutGyro.visibility = View.GONE
            layoutAccelThreshold.visibility = View.GONE
        } else {
            radioBalanced.isChecked = true
            tvModeDescription.text = "均衡模式：使用加速度计检测体动，精准度更高"
            layoutGyro.visibility = View.VISIBLE
            layoutAccelThreshold.visibility = View.VISIBLE
        }
        
        switchGyro.isChecked = isGyroEnabled
        
        fun updateControlsEnabled(enabled: Boolean) {
            cardSleepMode.isEnabled = enabled
            cardWakeTime.isEnabled = enabled
            cardThresholds.isEnabled = enabled
            cardSleepMode.alpha = if (enabled) 1.0f else 0.5f
            cardWakeTime.alpha = if (enabled) 1.0f else 0.5f
            cardThresholds.alpha = if (enabled) 1.0f else 0.5f
        }
        
        updateControlsEnabled(isEnabled)
        
        switchSleepMonitor.setOnCheckedChangeListener { _, isChecked ->
            updateControlsEnabled(isChecked)
            if (!isChecked) {
                stopService(Intent(this, com.livewell.service.SleepMonitorService::class.java))
            }
        }
        
        radioBalanced.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                tvModeDescription.text = "均衡模式：使用加速度计检测体动，精准度更高"
                layoutGyro.visibility = View.VISIBLE
                layoutAccelThreshold.visibility = View.VISIBLE
            }
        }
        
        radioPowerSaving.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                tvModeDescription.text = "省电模式：仅使用屏幕和步数判定，功耗<1%"
                layoutGyro.visibility = View.GONE
                layoutAccelThreshold.visibility = View.GONE
            }
        }
        
        // 点击起床时间卡片打开子界面
        cardWakeTime.setOnClickListener {
            if (switchSleepMonitor.isChecked) {
                showWakeTimeSettingsDialog()
            }
        }
        
        var inactiveThreshold = prefs.getInactiveThreshold()
        var stepThreshold = prefs.getSleepStepThreshold()
        var accelThreshold = prefs.getAccelThreshold()
        
        tvInactiveValue.text = inactiveThreshold.toString()
        tvStepValue.text = stepThreshold.toString()
        tvAccelValue.text = String.format("%.1f", accelThreshold)
        
        btnInactiveMinus.setOnClickListener {
            if (inactiveThreshold > 5) {
                inactiveThreshold -= 5
                tvInactiveValue.text = inactiveThreshold.toString()
            }
        }
        btnInactivePlus.setOnClickListener {
            if (inactiveThreshold < 120) {
                inactiveThreshold += 5
                tvInactiveValue.text = inactiveThreshold.toString()
            }
        }
        btnStepMinus.setOnClickListener {
            if (stepThreshold > 1) {
                stepThreshold--
                tvStepValue.text = stepThreshold.toString()
            }
        }
        btnStepPlus.setOnClickListener {
            if (stepThreshold < 50) {
                stepThreshold++
                tvStepValue.text = stepThreshold.toString()
            }
        }
        btnAccelMinus.setOnClickListener {
            if (accelThreshold > 0.5f) {
                accelThreshold = (accelThreshold * 10 - 1) / 10
                tvAccelValue.text = String.format("%.1f", accelThreshold)
            }
        }
        btnAccelPlus.setOnClickListener {
            if (accelThreshold < 5.0f) {
                accelThreshold = (accelThreshold * 10 + 1) / 10
                tvAccelValue.text = String.format("%.1f", accelThreshold)
            }
        }
        
        val avgSleepTime = prefs.getAverageSleepTime()
        val avgHours = avgSleepTime / (1000 * 60 * 60)
        val avgMinutes = (avgSleepTime % (1000 * 60 * 60)) / (1000 * 60)
        tvAvgSleepTime.text = String.format("平均睡眠时长：%d 小时%d 分钟", avgHours, avgMinutes)
        
        btnViewHistory.setOnClickListener { showSleepHistoryDialog() }
        
        AlertDialog.Builder(this)
            .setTitle("睡眠监测设置")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val enabled = switchSleepMonitor.isChecked
                prefs.setSleepMonitorEnabled(enabled)
                val mode = if (radioBalanced.isChecked) "balanced" else "power_saving"
                prefs.setSleepMode(mode)
                prefs.setGyroEnabled(switchGyro.isChecked)
                prefs.setInactiveThreshold(inactiveThreshold)
                prefs.setSleepStepThreshold(stepThreshold)
                prefs.setAccelThreshold(accelThreshold)
                
                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
                
                if (enabled) {
                    startService(Intent(this, com.livewell.service.SleepMonitorService::class.java))
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示起床时间和阈值设置子界面
     */
    private fun showWakeTimeSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_wake_time_settings, null)
        
        val subHourPicker = dialogView.findViewById<NumberPicker>(R.id.numberPickerHour)
        val subMinutePicker = dialogView.findViewById<NumberPicker>(R.id.numberPickerMinute)
        val subTolerancePicker = dialogView.findViewById<NumberPicker>(R.id.numberPickerTolerance)
        val tvCurrentSettings = dialogView.findViewById<TextView>(R.id.tvCurrentSettings)
        
        // 初始化值
        subHourPicker.minValue = 0
        subHourPicker.maxValue = 23
        subHourPicker.value = prefs.getWakeUpHour()
        subHourPicker.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        
        subMinutePicker.minValue = 0
        subMinutePicker.maxValue = 59
        subMinutePicker.value = prefs.getWakeUpMinute()
        subMinutePicker.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        
        subTolerancePicker.minValue = 1
        subTolerancePicker.maxValue = 6
        subTolerancePicker.value = prefs.getWakeUpToleranceHours()
        subTolerancePicker.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        
        // 更新当前设置显示
        fun updateCurrentSettingsDisplay() {
            val hour = String.format("%02d", subHourPicker.value)
            val minute = String.format("%02d", subMinutePicker.value)
            val tolerance = subTolerancePicker.value
            tvCurrentSettings.text = "当前设置：$hour:$minute 起床，容忍 $tolerance 小时"
        }
        
        updateCurrentSettingsDisplay()
        
        // 监听值变化
        subHourPicker.setOnValueChangedListener { _, _, _ -> updateCurrentSettingsDisplay() }
        subMinutePicker.setOnValueChangedListener { _, _, _ -> updateCurrentSettingsDisplay() }
        subTolerancePicker.setOnValueChangedListener { _, _, _ -> updateCurrentSettingsDisplay() }
        
        // 保存按钮
        AlertDialog.Builder(this)
            .setTitle("起床时间设置")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                // 保存到 PrefsManager
                prefs.setWakeUpHour(subHourPicker.value)
                prefs.setWakeUpMinute(subMinutePicker.value)
                prefs.setWakeUpToleranceHours(subTolerancePicker.value)
                
                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示睡眠历史记录对话框
     */
    private fun showSleepHistoryDialog() {
        val history = prefs.getSleepHistory()
        val lines = history.split("\n").takeLast(10).reversed()
        if (lines.isEmpty() || lines[0].isEmpty()) {
            Toast.makeText(this, "暂无睡眠历史数据", Toast.LENGTH_SHORT).show()
            return
        }
        val sb = StringBuilder()
        sb.append("最近睡眠记录\n\n")
        for (line in lines) {
            val parts = line.split(",")
            if (parts.size >= 6) {
                val date = parts[0]
                val sleepTime = parts[4]
                val wakeTime = parts[5]
                val duration = parts[3].toLong() / (1000 * 60)
                sb.append("$date  $sleepTime-$wakeTime  ${duration}分钟\n")
            }
        }
        AlertDialog.Builder(this)
            .setTitle("睡眠历史")
            .setMessage(sb.toString())
            .setPositiveButton("确定", null)
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 保存所有设置
        prefs.setAutoAlertModeEnabled(switchAutoAlarm.isChecked)
        prefs.setAlertConfirmEnabled(switchConfirmMechanism.isChecked)
        prefs.setAlertDuration(alertDuration)
        prefs.setAlertCheckTime(alertCheckHour, alertCheckMinute)
        prefs.setSilentMusicEnabled(switchSilentMusic.isChecked)
        prefs.setAccessibilityServiceEnabled(switchProMode.isChecked)
        
        // 重启服务以应用新设置
        restartCheckinService()
        
        Log.d(TAG, "功能设置页面关闭，所有设置已保存")
    }
}
