package com.livewell.untils


import com.livewell.model.AlertHistoryRecord
import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

class PrefsManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("livewell_prefs", Context.MODE_PRIVATE)
    
    companion object {
        // 原有常量
        private const val KEY_LAST_CHECKIN_DATE = "last_checkin_date"
        private const val KEY_EMERGENCY_CONTACT = "emergency_contact"
        private const val KEY_ALERT_MESSAGE = "alert_message"
        private const val KEY_CHECKIN_TIME = "checkin_time"
        private const val KEY_LAST_ALERT_TIME = "last_alert_time"
        
        // 邮件相关常量
        private const val KEY_EMERGENCY_EMAIL = "emergency_email"
        private const val KEY_EMAIL_SUBJECT = "email_subject"
        private const val KEY_EMAIL_CONTENT = "email_content"
        private const val KEY_NOTIFY_TYPE = "notify_type"
        
        // 模式相关常量
        private const val KEY_APP_MODE = "app_mode"
        private const val KEY_LAST_EMAIL_CHECK = "last_email_check"
        private const val KEY_APP_USAGE_THRESHOLD = "app_usage_threshold"
        private const val KEY_ALERT_CONFIRM_ENABLED = "alert_confirm_enabled"
        private const val KEY_ALERT_DURATION = "alert_duration"
        private const val KEY_SMART_MODE_ENABLED = "smart_mode_enabled"
        private const val KEY_HISTORY_DATA = "history_data"
        
        // 广播接收器
        private const val KEY_USER_CONFIRMED = "user_confirmed"
        private const val KEY_LAST_CONFIRM_TIME = "last_confirm_time"
        private const val KEY_SNOOZE_REQUESTED = "snooze_requested"
        private const val KEY_SNOOZE_TIME = "snooze_time"
        private const val KEY_PURE_USAGE_MODE = "pure_usage_mode"
        
        // 运动监测
        private const val KEY_STEP_THRESHOLD = "step_threshold"
        private const val KEY_STEP_MONITOR_ENABLED = "step_monitor_enabled"
        
        // 通知方式常量
        const val NOTIFY_PHONE = "phone"
        const val NOTIFY_EMAIL = "email"
        const val NOTIFY_BOTH = "both"
        
        // 模式常量
        const val MODE_RECEIVER = "receiver"  // 守护他人（接收警报）
        const val MODE_GUARDIAN = "guardian"  // 被守护（发送警报）
        const val MODE_MIXED = "mixed"
        const val MODE_COMMUNITY = "community"  // 新增：社区守护模式
        
        // 睡眠监测
         private const val KEY_SLEEP_STEP_THRESHOLD = "sleep_step_threshold"
        private const val KEY_SLEEP_MONITOR_ENABLED = "sleep_monitor_enabled"
        private const val KEY_SLEEP_MODE = "sleep_mode"
        private const val KEY_WAKE_UP_TOLERANCE_HOURS = "wake_up_tolerance_hours"
        private const val KEY_GYRO_ENABLED = "gyro_enabled"
        private const val KEY_SLEEP_START_HOUR = "sleep_start_hour"
        private const val KEY_SLEEP_START_MINUTE = "sleep_start_minute"
        private const val KEY_SLEEP_END_HOUR = "sleep_end_hour"
        private const val KEY_SLEEP_END_MINUTE = "sleep_end_minute"
        private const val KEY_INACTIVE_THRESHOLD = "inactive_threshold"
        private const val KEY_ACCEL_THRESHOLD = "accel_threshold"
        private const val KEY_AWAKE_STEP_THRESHOLD = "awake_step_threshold"
        private const val KEY_SLEEP_DATA = "sleep_data"
        
        // 测试数据
        private const val KEY_TEST_SIGNED = "test_signed"
        private const val KEY_TEST_BOOT_TIME = "test_boot_time"
        private const val KEY_TEST_APP_USAGE = "test_app_usage"
        private const val KEY_TEST_STEP_COUNT = "test_step_count"
        private const val KEY_TEST_WAKE_HOUR = "test_wake_hour"
        private const val KEY_TEST_WAKE_MINUTE = "test_wake_minute"
        private const val KEY_TEST_MODE = "test_mode"
        
        // 后台保活
        private const val KEY_BATTERY_OPTIMIZATION_REQUESTED = "battery_optimization_requested"
        private const val KEY_AUTO_START_REQUESTED = "auto_start_requested"
        private const val KEY_SETTINGS_GUIDE_SHOWN = "settings_guide_shown"
        private const val KEY_SETTINGS_GUIDE_STEP = "settings_guide_step"
        private const val KEY_LAST_ALIVE_CHECK = "last_alive_check"
        private const val KEY_NEXT_REMINDER_TIME = "next_reminder_time"
        
        // 无声音乐播放保活
        private const val KEY_SILENT_MUSIC_ENABLED = "silent_music_enabled"
        
        // 辅助功能保活服务
        private const val KEY_ACCESSIBILITY_SERVICE_ENABLED = "accessibility_service_enabled"
        
        // 权限相关
        private const val KEY_ACCESSIBILITY_PERMISSION = "accessibility_permission"
        private const val KEY_NOTIFICATION_PERMISSION = "notification_permission"
        private const val KEY_CLIPBOARD_PERMISSION = "clipboard_permission"
        private const val KEY_OVERLAY_PERMISSION = "overlay_permission"
        private const val KEY_PERMISSIONS_REMIND_LATER = "permissions_remind_later"
        
        // 开发者界面
        private const val KEY_BACKGROUND_START_TIME = "background_start_time"
        private const val KEY_BACKGROUND_TOTAL_TIME = "background_total_time"
        private const val KEY_TIMER_RUNNING = "timer_running"
        
        // 守护模式设置备份（用于模式切换）
        private const val KEY_GUARDIAN_SMART_MODE = "guardian_smart_mode"
        private const val KEY_GUARDIAN_ALERT_CONFIRM = "guardian_alert_confirm"
        private const val KEY_GUARDIAN_ALERT_DURATION = "guardian_alert_duration"
        private const val KEY_GUARDIAN_USAGE_THRESHOLD = "guardian_usage_threshold"
        private const val KEY_GUARDIAN_STEP_THRESHOLD = "guardian_step_threshold"
        
        // 历史记录
        private const val KEY_ALERT_HISTORY = "alert_history"
        private const val MAX_HISTORY_COUNT = 50
        
        // 守护功能
        private const val KEY_GUARDIAN_TARGETS = "guardian_targets"
        private const val KEY_GUARDIAN_ALERTS = "guardian_alerts"
        private const val KEY_CURRENT_TARGET_ID = "current_target_id"
        
        // 报警时间
        private const val KEY_ALERT_CHECK_HOUR = "alert_check_hour"
        private const val KEY_ALERT_CHECK_MINUTE = "alert_check_minute"
        
        // 自动报警模式
        private const val KEY_AUTO_ALERT_MODE_ENABLED = "auto_alert_mode_enabled"
        private const val KEY_ALERT_CRITERIA = "alert_criteria"
        
        // 测试邮件
        private const val KEY_TEST_EMAIL_ENABLED = "test_email_enabled"
        private const val KEY_LAST_TEST_EMAIL_TIME = "last_test_email_time"
        private const val KEY_TEST_EMAIL_INTERVAL = "test_email_interval"
        
        // ✅ 步数相关常量
        private const val KEY_STEP_COUNT = "step_count"
        private const val KEY_STEP_SAVE_DATE = "step_save_date"
        
        // 评判标准
        const val CRITERIA_USAGE_ONLY = "usage_only"
        const val CRITERIA_STEP_ONLY = "step_only"
        const val CRITERIA_MIXED = "mixed"

        // 社区守护模式相关常量（新增）
        private const val KEY_COMMUNITY_SERVER_URL = "community_server_url"
        private const val KEY_COMMUNITY_API_KEY = "community_api_key"
        private const val KEY_COMMUNITY_USER_ID = "community_user_id"
        private const val KEY_COMMUNITY_ENABLED = "community_enabled"
        
        // 个人信息相关常量（新增）
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_AGE = "user_age"
        private const val KEY_USER_GENDER = "user_gender"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_ADDRESS = "user_address"
        private const val KEY_USER_EMERGENCY_CONTACT = "user_emergency_contact"
        private const val KEY_USER_EMERGENCY_PHONE = "user_emergency_phone"
        private const val KEY_USER_BLOOD_TYPE = "user_blood_type"
        private const val KEY_USER_ALLERGIES = "user_allergies"
        private const val KEY_USER_MEDICAL_HISTORY = "user_medical_history"
        private const val KEY_USER_HEIGHT = "user_height"
        private const val KEY_USER_WEIGHT = "user_weight"
        private const val KEY_USER_COMMUNITY_CODE = "user_community_code"
        
        // 时光胶囊相关常量（新增）
        private const val KEY_TIME_CAPSULE_ENABLED = "time_capsule_enabled"
        private const val KEY_EMERGENCY_EMAIL_ENABLED = "emergency_email_enabled"
        private const val KEY_PASSWORD_BOOK_ENABLED = "password_book_enabled"
        private const val KEY_INACTIVE_THRESHOLD_DAYS = "inactive_threshold_days"
        private const val KEY_EMERGENCY_EMAIL_TEXT = "emergency_email_text"
        private const val KEY_PASSWORD_BOOK_QUESTION = "password_book_question"
        private const val KEY_PASSWORD_BOOK_ANSWER = "password_book_answer"
        private const val KEY_PASSWORD_BOOK_CONTENT = "password_book_content"
        private const val KEY_PASSWORD_BOOK_ATTACHMENTS = "password_book_attachments"
        // ✅ 新增：时光胶囊访问密码（保护设置界面）
        private const val KEY_TIME_CAPSULE_ACCESS_PASSWORD = "time_capsule_access_password"
        private const val KEY_COPIED_FROM_EMAIL = "copied_from_email"
        private const val KEY_COPIED_FROM_BOOK = "copied_from_book"
        
    }
    
    // 原有方法
    fun saveLastCheckinDate(date: String) {
        prefs.edit().putString(KEY_LAST_CHECKIN_DATE, date).apply()
    }
    
    fun getLastCheckinDate(): String? {
        return prefs.getString(KEY_LAST_CHECKIN_DATE, null)
    }
    
    fun saveEmergencyContact(contact: String) {
        prefs.edit().putString(KEY_EMERGENCY_CONTACT, contact).apply()
    }
    
    fun getEmergencyContact(): String? {
        return prefs.getString(KEY_EMERGENCY_CONTACT, null)
    }
    
    fun saveAlertMessage(message: String) {
        prefs.edit().putString(KEY_ALERT_MESSAGE, message).apply()
    }
    
    fun getAlertMessage(): String {
        return prefs.getString(KEY_ALERT_MESSAGE, "紧急情况！请立即联系我！") ?: "紧急情况！请立即联系我！"
    }
    
    fun saveCheckinTime(time: Long) {
        prefs.edit().putLong(KEY_CHECKIN_TIME, time).apply()
    }
    
    fun getCheckinTime(): Long {
        return prefs.getLong(KEY_CHECKIN_TIME, 0)
    }
    
    fun saveLastAlertTime(time: Long) {
        prefs.edit().putLong(KEY_LAST_ALERT_TIME, time).apply()
    }
    
    fun getLastAlertTime(): Long {
        return prefs.getLong(KEY_LAST_ALERT_TIME, 0)
    }
    
    // 邮件相关方法
    fun saveNotifyType(type: String) {
        prefs.edit().putString(KEY_NOTIFY_TYPE, type).apply()
    }
    
    fun getNotifyType(): String {
        return prefs.getString(KEY_NOTIFY_TYPE, "phone") ?: "phone"
    }
    
    fun saveEmergencyEmail(email: String) {
        prefs.edit().putString(KEY_EMERGENCY_EMAIL, email).apply()
    }
    
    fun getEmergencyEmail(): String? {
        return prefs.getString(KEY_EMERGENCY_EMAIL, null)
    }
    
    fun saveEmailSubject(subject: String) {
        prefs.edit().putString(KEY_EMAIL_SUBJECT, subject).apply()
    }
    
    fun getEmailSubject(): String {
        return prefs.getString(KEY_EMAIL_SUBJECT, "安全警报") ?: "安全警报"
    }
    
    fun saveEmailContent(content: String) {
        prefs.edit().putString(KEY_EMAIL_CONTENT, content).apply()
    }
    
    fun getEmailContent(): String {
        return prefs.getString(KEY_EMAIL_CONTENT, "这是来自'活着呢'应用的紧急警报") ?: "这是来自'活着呢'应用的紧急警报"
    }
    
    fun setEmailEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("email_enabled", enabled).apply()
    }
    
    fun isEmailEnabled(): Boolean {
        return prefs.getBoolean("email_enabled", false)
    }
    
    fun setEmailTo(email: String) {
        prefs.edit().putString("email_to", email).apply()
    }
    
    fun getEmailTo(): String? {
        return prefs.getString("email_to", null)
    }
    
    fun setEmailSmtpHost(host: String) {
        prefs.edit().putString("email_host", host).apply()
    }
    
    fun getEmailSmtpHost(): String {
        return prefs.getString("email_host", "smtp.qq.com") ?: "smtp.qq.com"
    }
    
    fun setEmailSmtpPort(port: String) {
        prefs.edit().putString("email_port", port).apply()
    }
    
    fun getEmailSmtpPort(): String {
        return prefs.getString("email_port", "465") ?: "465"  // ✅ 默认端口改为 465 (SSL)
    }
    
    // 模式相关方法
    fun saveAppMode(mode: String) {
        prefs.edit().putString(KEY_APP_MODE, mode).apply()
    }
    
    fun getAppMode(): String {
        return prefs.getString(KEY_APP_MODE, MODE_GUARDIAN) ?: MODE_GUARDIAN
    }
    
    /**
     * ✅ 检查是否已经明确设置了应用模式（不是默认值）
     */
    fun hasExplicitlySetAppMode(): Boolean {
        return prefs.contains(KEY_APP_MODE)
    }
    
    fun saveLastEmailCheckTime(time: Long) {
        prefs.edit().putLong(KEY_LAST_EMAIL_CHECK, time).apply()
    }
    
    fun getLastEmailCheckTime(): Long {
        return prefs.getLong(KEY_LAST_EMAIL_CHECK, 0)
    }
    
    /**
     * 重置邮件检查时间（用于重新检查所有邮件）
     */
    fun resetEmailCheckTime() {
        prefs.edit().putLong(KEY_LAST_EMAIL_CHECK, 0).apply()
    }


fun getAppUsageThreshold(): Int {
    return prefs.getInt(KEY_APP_USAGE_THRESHOLD, 30) // 默认5分钟
}

fun setAppUsageThreshold(minutes: Int) {
    prefs.edit().putInt(KEY_APP_USAGE_THRESHOLD, minutes).apply()
}
// 确认机制设置
fun isAlertConfirmEnabled(): Boolean {
    return prefs.getBoolean(KEY_ALERT_CONFIRM_ENABLED, true) // 默认开启
}

fun setAlertConfirmEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_ALERT_CONFIRM_ENABLED, enabled).apply()
}

fun getAlertDuration(): Int {
    return prefs.getInt(KEY_ALERT_DURATION, 5) // 默认5分钟
}

fun setAlertDuration(minutes: Int) {
    prefs.edit().putInt(KEY_ALERT_DURATION, minutes).apply()
}

// 智能模式设置
fun isSmartModeEnabled(): Boolean {
    return prefs.getBoolean(KEY_SMART_MODE_ENABLED, true) // 默认开启
}

fun setSmartModeEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_SMART_MODE_ENABLED, enabled).apply()
}

// ========== 被守护模式设置保存与恢复 ==========

/**
 * 保存当前被守护模式的所有设置
 */
fun saveGuardianModeSettings() {
    prefs.edit().apply {
        // 保存各个开关状态
        putBoolean(KEY_GUARDIAN_SMART_MODE, isSmartModeEnabled())
        putBoolean(KEY_GUARDIAN_ALERT_CONFIRM, isAlertConfirmEnabled())
        putInt(KEY_GUARDIAN_ALERT_DURATION, getAlertDuration())
        putInt(KEY_GUARDIAN_USAGE_THRESHOLD, getAppUsageThreshold())
        putInt(KEY_GUARDIAN_STEP_THRESHOLD, getStepThreshold())
        apply()
    }
}

/**
 * 恢复之前保存的被守护模式设置
 */
fun restoreGuardianModeSettings() {
    prefs.edit().apply {
        // 恢复各个开关状态
        putBoolean(KEY_SMART_MODE_ENABLED, prefs.getBoolean(KEY_GUARDIAN_SMART_MODE, true))
        putBoolean(KEY_ALERT_CONFIRM_ENABLED, prefs.getBoolean(KEY_GUARDIAN_ALERT_CONFIRM, true))
        putInt(KEY_ALERT_DURATION, prefs.getInt(KEY_GUARDIAN_ALERT_DURATION, 5))
        putInt(KEY_APP_USAGE_THRESHOLD, prefs.getInt(KEY_GUARDIAN_USAGE_THRESHOLD, 30))
        putInt(KEY_STEP_THRESHOLD, prefs.getInt(KEY_GUARDIAN_STEP_THRESHOLD, 100))
        apply()
    }
}

/**
 * 关闭所有被守护模式相关的设置
 */
fun disableAllGuardianSettings() {
    prefs.edit().apply {
        putBoolean(KEY_SMART_MODE_ENABLED, false)
        putBoolean(KEY_ALERT_CONFIRM_ENABLED, false)
        putInt(KEY_ALERT_DURATION, 0)
        putInt(KEY_APP_USAGE_THRESHOLD, 0)
        putInt(KEY_STEP_THRESHOLD, 0)
        apply()
    }
}

// 历史数据存储（用于智能模式）
fun saveHistoryData(data: String) {
    prefs.edit().putString(KEY_HISTORY_DATA, data).apply()
}

fun getHistoryData(): String {
    return prefs.getString(KEY_HISTORY_DATA, "") ?: ""
}

// 用户确认状态
fun setUserConfirmed(confirmed: Boolean) {
    prefs.edit().putBoolean(KEY_USER_CONFIRMED, confirmed).apply()
}

fun isUserConfirmed(): Boolean {
    return prefs.getBoolean(KEY_USER_CONFIRMED, false)
}

fun setLastConfirmTime(time: Long) {
    prefs.edit().putLong(KEY_LAST_CONFIRM_TIME, time).apply()
}

fun getLastConfirmTime(): Long {
    return prefs.getLong(KEY_LAST_CONFIRM_TIME, 0)
}

// 稍后提醒状态
fun setSnoozeRequested(requested: Boolean) {
    prefs.edit().putBoolean(KEY_SNOOZE_REQUESTED, requested).apply()
}

fun isSnoozeRequested(): Boolean {
    return prefs.getBoolean(KEY_SNOOZE_REQUESTED, false)
}

fun setSnoozeTime(time: Long) {
    prefs.edit().putLong(KEY_SNOOZE_TIME, time).apply()
}

fun getSnoozeTime(): Long {
    return prefs.getLong(KEY_SNOOZE_TIME, 0)
}

// 清除确认状态（每天重置）
fun resetDailyConfirmStatus() {
    setUserConfirmed(false)
    setSnoozeRequested(false)
}

// 纯设备使用统计模式（默认关闭）
fun setPureUsageMode(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_PURE_USAGE_MODE, enabled).apply()
}

fun isPureUsageMode(): Boolean {
    return prefs.getBoolean(KEY_PURE_USAGE_MODE, false)
}

// 步数阈值设置
fun setStepThreshold(steps: Int) {
    prefs.edit().putInt(KEY_STEP_THRESHOLD, steps).apply()
}

fun getStepThreshold(): Int {
    return prefs.getInt(KEY_STEP_THRESHOLD, 100) // 默认100步
}

// 步数监测开关
fun setStepMonitorEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_STEP_MONITOR_ENABLED, enabled).apply()
}

fun isStepMonitorEnabled(): Boolean {
    return prefs.getBoolean(KEY_STEP_MONITOR_ENABLED, true) // 默认开启
}

// 睡眠监测开关
fun setSleepMonitorEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_SLEEP_MONITOR_ENABLED, enabled).apply()
}

fun isSleepMonitorEnabled(): Boolean {
    return prefs.getBoolean(KEY_SLEEP_MONITOR_ENABLED, false)
}

// 睡眠监测模式
fun setSleepMode(mode: String) {
    prefs.edit().putString(KEY_SLEEP_MODE, mode).apply()
}

fun getSleepMode(): String {
    return prefs.getString(KEY_SLEEP_MODE, "balanced") ?: "balanced" // 默认均衡模式
}

// 陀螺仪开关（作为可选增强）
fun setGyroEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_GYRO_ENABLED, enabled).apply()
}

fun isGyroEnabled(): Boolean {
    return prefs.getBoolean(KEY_GYRO_ENABLED, false) // 默认关闭
}

// 预设起床时间
fun setWakeUpTime(hour: Int, minute: Int) {
    prefs.edit().putInt(KEY_SLEEP_END_HOUR, hour).apply()
    prefs.edit().putInt(KEY_SLEEP_END_MINUTE, minute).apply()
}

fun getWakeUpHour(): Int {
    return prefs.getInt(KEY_SLEEP_END_HOUR, 6) // 默认6:00
}

fun setWakeUpHour(hour: Int) {
    prefs.edit().putInt(KEY_SLEEP_END_HOUR, hour).apply()
}

fun getWakeUpMinute(): Int {
    return prefs.getInt(KEY_SLEEP_END_MINUTE, 0)
}

fun setWakeUpMinute(minute: Int) {
    prefs.edit().putInt(KEY_SLEEP_END_MINUTE, minute).apply()
}

/**
 * ✅ 新增：获取起床容忍时长（小时）
 */
fun getWakeUpToleranceHours(): Int {
    return prefs.getInt(KEY_WAKE_UP_TOLERANCE_HOURS, 2)  // ✅ 正确
}

fun setWakeUpToleranceHours(hours: Int) {
    prefs.edit().putInt(KEY_WAKE_UP_TOLERANCE_HOURS, hours).apply()  // ✅ 正确
}

// 预设入睡时间（用于参考）
fun setSleepTime(hour: Int, minute: Int) {
    prefs.edit().putInt(KEY_SLEEP_START_HOUR, hour).apply()
    prefs.edit().putInt(KEY_SLEEP_START_MINUTE, minute).apply()
}

fun getSleepHour(): Int {
    return prefs.getInt(KEY_SLEEP_START_HOUR, 23) // 默认23:00
}

fun getSleepMinute(): Int {
    return prefs.getInt(KEY_SLEEP_START_MINUTE, 0)
}

// 无活动判定时间（分钟）
fun setInactiveThreshold(minutes: Int) {
    prefs.edit().putInt(KEY_INACTIVE_THRESHOLD, minutes).apply()
}

fun getInactiveThreshold(): Int {
    return prefs.getInt(KEY_INACTIVE_THRESHOLD, 30) // 默认30分钟
}

// 步数增长阈值（入睡判定）// 修改后
// 睡眠步数阈值
fun setSleepStepThreshold(steps: Int) {
    prefs.edit().putInt(KEY_SLEEP_STEP_THRESHOLD, steps).apply()
}

fun getSleepStepThreshold(): Int {
    return prefs.getInt(KEY_SLEEP_STEP_THRESHOLD, 10)
}

// 加速度变化阈值（m/s²）
fun setAccelThreshold(threshold: Float) {
    prefs.edit().putFloat(KEY_ACCEL_THRESHOLD, threshold).apply()
}

fun getAccelThreshold(): Float {
    return prefs.getFloat(KEY_ACCEL_THRESHOLD, 1.5f) // 默认1.5 m/s²
}

// 醒来步数阈值
fun setAwakeStepThreshold(steps: Int) {
    prefs.edit().putInt(KEY_AWAKE_STEP_THRESHOLD, steps).apply()
}

fun getAwakeStepThreshold(): Int {
    return prefs.getInt(KEY_AWAKE_STEP_THRESHOLD, 10) // 默认10步
}

// 睡眠历史数据存储
fun saveSleepRecord(record: String) {
    val history = getSleepHistory()
    val updatedHistory = if (history.isEmpty()) record else "$history\n$record"
    // 只保留最近30条记录
    val lines = updatedHistory.split("\n")
    val last30 = if (lines.size > 30) lines.takeLast(30) else lines
    prefs.edit().putString(KEY_SLEEP_DATA, last30.joinToString("\n")).apply()
}

fun getSleepHistory(): String {
    return prefs.getString(KEY_SLEEP_DATA, "") ?: ""
}

// 获取平均睡眠时间（用于学习）
fun getAverageSleepTime(): Long {
    val history = getSleepHistory()
    if (history.isEmpty()) return 8 * 60 * 60 * 1000L // 默认8小时
    
    var totalDuration = 0L
    var count = 0
    val lines = history.split("\n").takeLast(7) // 用最近7天
    
    for (line in lines) {
        val parts = line.split(",")
        if (parts.size >= 3) {
            try {
                val sleepTime = parts[1].toLong()
                val wakeTime = parts[2].toLong()
                totalDuration += (wakeTime - sleepTime)
                count++
            } catch (e: Exception) {
                // 忽略解析错误
            }
        }
    }
    
    return if (count > 0) totalDuration / count else 8 * 60 * 60 * 1000L
}

fun setTestData(isSigned: Boolean, bootTime: Float, appUsage: Int, stepCount: Int, wakeHour: Int, wakeMinute: Int) {
    prefs.edit().putBoolean(KEY_TEST_SIGNED, isSigned).apply()
    prefs.edit().putFloat(KEY_TEST_BOOT_TIME, bootTime).apply()
    prefs.edit().putInt(KEY_TEST_APP_USAGE, appUsage).apply()
    prefs.edit().putInt(KEY_TEST_STEP_COUNT, stepCount).apply()
    prefs.edit().putInt(KEY_TEST_WAKE_HOUR, wakeHour).apply()
    prefs.edit().putInt(KEY_TEST_WAKE_MINUTE, wakeMinute).apply()
    prefs.edit().putBoolean(KEY_TEST_MODE, true).apply()
}

fun clearTestData() {
    prefs.edit().putBoolean(KEY_TEST_MODE, false).apply()
}

fun isTestMode(): Boolean {
    return prefs.getBoolean(KEY_TEST_MODE, false)
}

fun getTestSigned(): Boolean {
    return prefs.getBoolean(KEY_TEST_SIGNED, false)
}

fun getTestBootTime(): Float {
    return prefs.getFloat(KEY_TEST_BOOT_TIME, 0f)
}

fun getTestAppUsage(): Int {
    return prefs.getInt(KEY_TEST_APP_USAGE, 0)
}

fun getTestStepCount(): Int {
    return prefs.getInt(KEY_TEST_STEP_COUNT, 0)
}

fun getTestWakeHour(): Int {
    return prefs.getInt(KEY_TEST_WAKE_HOUR, 7)
}

fun getTestWakeMinute(): Int {
    return prefs.getInt(KEY_TEST_WAKE_MINUTE, 0)
}

fun setBatteryOptimizationRequested(requested: Boolean) {
    prefs.edit().putBoolean(KEY_BATTERY_OPTIMIZATION_REQUESTED, requested).apply()
}

fun isBatteryOptimizationRequested(): Boolean {
    return prefs.getBoolean(KEY_BATTERY_OPTIMIZATION_REQUESTED, false)
}

// 记录是否已请求过自启动权限
fun setAutoStartRequested(requested: Boolean) {
    prefs.edit().putBoolean(KEY_AUTO_START_REQUESTED, requested).apply()
}

fun isAutoStartRequested(): Boolean {
    return prefs.getBoolean(KEY_AUTO_START_REQUESTED, false)
}

// 记录是否已显示过设置引导
fun setSettingsGuideShown(shown: Boolean) {
    prefs.edit().putBoolean(KEY_SETTINGS_GUIDE_SHOWN, shown).apply()
}

fun isSettingsGuideShown(): Boolean {
    return prefs.getBoolean(KEY_SETTINGS_GUIDE_SHOWN, false)
}

// 更新最后存活时间（用于自检）
fun updateLastAliveTime() {
    prefs.edit().putLong(KEY_LAST_ALIVE_CHECK, System.currentTimeMillis()).apply()
}

fun getLastAliveTime(): Long {
    return prefs.getLong(KEY_LAST_ALIVE_CHECK, 0)
}

fun setNextReminderTime(time: Long) {
    prefs.edit().putLong(KEY_NEXT_REMINDER_TIME, time).apply()
}

fun getNextReminderTime(): Long {
    return prefs.getLong(KEY_NEXT_REMINDER_TIME, 0)
}

    // 计时器相关方法
    fun setBackgroundStartTime(time: Long) {
        prefs.edit().putLong(KEY_BACKGROUND_START_TIME, time).apply()
    }

    fun getBackgroundStartTime(): Long {
        return prefs.getLong(KEY_BACKGROUND_START_TIME, 0)
    }

    fun setBackgroundTotalTime(time: Long) {
        prefs.edit().putLong(KEY_BACKGROUND_TOTAL_TIME, time).apply()
    }

    fun getBackgroundTotalTime(): Long {
        return prefs.getLong(KEY_BACKGROUND_TOTAL_TIME, 0)
    }

    fun setTimerRunning(running: Boolean) {
        prefs.edit().putBoolean(KEY_TIMER_RUNNING, running).apply()
    }

    fun isTimerRunning(): Boolean {
        return prefs.getBoolean(KEY_TIMER_RUNNING, false)
    }

    // 重置计时器
    fun resetBackgroundTimer() {
        setBackgroundStartTime(0)
        setBackgroundTotalTime(0)
        setTimerRunning(false)
    }

fun saveLong(key: String, value: Long) {
    prefs.edit().putLong(key, value).apply()
}

fun getLong(key: String, default: Long = 0L): Long {
    return prefs.getLong(key, default)
}

fun saveString(key: String, value: String) {
    prefs.edit().putString(key, value).apply()
}

fun getString(key: String, default: String? = null): String? {
    return prefs.getString(key, default)
}

private val gson = com.google.gson.Gson() // 需要添加 Gson 依赖

// 添加历史记录
fun addAlertHistory(record: AlertHistoryRecord) {
    val history = getAlertHistory()
    history.add(0, record) // 新记录添加到开头
    
    // 限制记录数量
    while (history.size > MAX_HISTORY_COUNT) {
        history.removeAt(history.size - 1)
    }
    
    val json = gson.toJson(history)
    prefs.edit().putString(KEY_ALERT_HISTORY, json).apply()
}

// 获取历史记录
fun getAlertHistory(): MutableList<AlertHistoryRecord> {
    val json = prefs.getString(KEY_ALERT_HISTORY, null) ?: return mutableListOf()
    return try {
        val type = object : com.google.gson.reflect.TypeToken<MutableList<AlertHistoryRecord>>() {}.type
        gson.fromJson(json, type)
    } catch (e: Exception) {
        mutableListOf()
    }
}

// 清除历史记录
fun clearAlertHistory() {
    prefs.edit().remove(KEY_ALERT_HISTORY).apply()
}


// 报警时间设置
fun setAlertCheckTime(hour: Int, minute: Int) {
    prefs.edit().putInt(KEY_ALERT_CHECK_HOUR, hour).apply()
    prefs.edit().putInt(KEY_ALERT_CHECK_MINUTE, minute).apply()
}

fun getAlertCheckHour(): Int {
    return prefs.getInt(KEY_ALERT_CHECK_HOUR, 23) // 默认 23 点
}

fun getAlertCheckMinute(): Int {
    return prefs.getInt(KEY_ALERT_CHECK_MINUTE, 0) // 默认 0 分
}

// ✅ 新增：自动报警模式方法
fun isAutoAlertModeEnabled(): Boolean {
    return prefs.getBoolean(KEY_AUTO_ALERT_MODE_ENABLED, false)
}

fun setAutoAlertModeEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_AUTO_ALERT_MODE_ENABLED, enabled).apply()
}

fun getAlertCriteria(): String {
    return prefs.getString(KEY_ALERT_CRITERIA, CRITERIA_MIXED) ?: CRITERIA_MIXED
}

fun setAlertCriteria(criteria: String) {
    prefs.edit().putString(KEY_ALERT_CRITERIA, criteria).apply()
}

// ✅ 测试邮件开关
fun isTestEmailEnabled(): Boolean {
    return prefs.getBoolean(KEY_TEST_EMAIL_ENABLED, false)
}

fun setTestEmailEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_TEST_EMAIL_ENABLED, enabled).apply()
}

// ✅ 上次发送时间
fun getLastTestEmailTime(): Long {
    return prefs.getLong(KEY_LAST_TEST_EMAIL_TIME, 0)
}

fun setLastTestEmailTime(time: Long) {
    prefs.edit().putLong(KEY_LAST_TEST_EMAIL_TIME, time).apply()
}

// ✅ 发送间隔（默认 30 分钟）
fun getTestEmailInterval(): Int {
    return prefs.getInt(KEY_TEST_EMAIL_INTERVAL, 30)
}

fun setTestEmailInterval(minutes: Int) {
    prefs.edit().putInt(KEY_TEST_EMAIL_INTERVAL, minutes).apply()
}


fun saveStepCount(steps: Int) {
    prefs.edit().putInt(KEY_STEP_COUNT, steps).apply()
}

fun getSavedStepCount(): Int {
    return prefs.getInt(KEY_STEP_COUNT, 0)
}

fun saveStepSaveDate(dateKey: String) {
    prefs.edit().putString(KEY_STEP_SAVE_DATE, dateKey).apply()
}

fun getLastStepSaveDate(): String {
    return prefs.getString(KEY_STEP_SAVE_DATE, "") ?: ""
}

// 无声音乐播放保活设置
fun isSilentMusicEnabled(): Boolean {
    return prefs.getBoolean(KEY_SILENT_MUSIC_ENABLED, true) // ✅ 默认开启
}

fun setSilentMusicEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_SILENT_MUSIC_ENABLED, enabled).apply()
}

// 辅助功能保活服务设置
fun isAccessibilityServiceEnabled(): Boolean {
    return prefs.getBoolean(KEY_ACCESSIBILITY_SERVICE_ENABLED, false)
}

fun setAccessibilityServiceEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_ACCESSIBILITY_SERVICE_ENABLED, enabled).apply()
}

// ========== 权限相关方法 ==========

/**
 * 设置无障碍权限是否已授予
 */
fun setAccessibilityPermissionGranted(granted: Boolean) {
    prefs.edit().putBoolean(KEY_ACCESSIBILITY_PERMISSION, granted).apply()
}

/**
 * 检查无障碍权限是否已授予
 */
fun isAccessibilityPermissionGranted(): Boolean {
    return prefs.getBoolean(KEY_ACCESSIBILITY_PERMISSION, false)
}

/**
 * 设置通知权限是否已授予
 */
fun setNotificationPermissionGranted(granted: Boolean) {
    prefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION, granted).apply()
}

/**
 * 检查通知权限是否已授予
 */
fun isNotificationPermissionGranted(): Boolean {
    return prefs.getBoolean(KEY_NOTIFICATION_PERMISSION, false)
}

/**
 * 设置剪贴板权限是否已授予
 */
fun setClipboardPermissionGranted(granted: Boolean) {
    prefs.edit().putBoolean(KEY_CLIPBOARD_PERMISSION, granted).apply()
}

/**
 * 检查剪贴板权限是否已授予
 */
fun isClipboardPermissionGranted(): Boolean {
    return prefs.getBoolean(KEY_CLIPBOARD_PERMISSION, false)
}

/**
 * 设置悬浮窗权限是否已授予
 */
fun setOverlayPermissionGranted(granted: Boolean) {
    prefs.edit().putBoolean(KEY_OVERLAY_PERMISSION, granted).apply()
}

/**
 * 检查悬浮窗权限是否已授予
 */
fun isOverlayPermissionGranted(): Boolean {
    return prefs.getBoolean(KEY_OVERLAY_PERMISSION, false)
}

/**
 * 设置稍后提醒权限
 */
fun setPermissionsRemindLater(remindLater: Boolean) {
    prefs.edit().putBoolean(KEY_PERMISSIONS_REMIND_LATER, remindLater).apply()
}

/**
 * 检查是否选择了稍后提醒权限
 */
fun isPermissionsRemindLater(): Boolean {
    return prefs.getBoolean(KEY_PERMISSIONS_REMIND_LATER, false)
}

 // ========== 社区守护模式相关方法（新增） ==========
    
    fun saveCommunityServerUrl(url: String) {
        prefs.edit().putString(KEY_COMMUNITY_SERVER_URL, url).apply()
    }
    
    fun getCommunityServerUrl(): String {
        return prefs.getString(KEY_COMMUNITY_SERVER_URL, "") ?: ""
    }
    
    fun saveCommunityApiKey(key: String) {
        prefs.edit().putString(KEY_COMMUNITY_API_KEY, key).apply()
    }
    
    fun getCommunityApiKey(): String {
        return prefs.getString(KEY_COMMUNITY_API_KEY, "") ?: ""
    }
    
    fun saveCommunityUserId(id: String) {
        prefs.edit().putString(KEY_COMMUNITY_USER_ID, id).apply()
    }
    
    fun getCommunityUserId(): String {
        return prefs.getString(KEY_COMMUNITY_USER_ID, "") ?: ""
    }
    
    fun setCommunityEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_COMMUNITY_ENABLED, enabled).apply()
    }
    
    fun isCommunityEnabled(): Boolean {
        return prefs.getBoolean(KEY_COMMUNITY_ENABLED, false)
    }
    
    // ========== 个人信息相关方法（新增） ==========
    
    fun saveUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }
    
    fun getUserName(): String {
        return prefs.getString(KEY_USER_NAME, "") ?: ""
    }
    
    fun saveUserAge(age: Int) {
        prefs.edit().putInt(KEY_USER_AGE, age).apply()
    }
    
    fun getUserAge(): Int {
        return prefs.getInt(KEY_USER_AGE, 0)
    }
    
    fun saveUserGender(gender: String) {
        prefs.edit().putString(KEY_USER_GENDER, gender).apply()
    }
    
    fun getUserGender(): String {
        return prefs.getString(KEY_USER_GENDER, "") ?: ""
    }
    
    fun saveUserPhone(phone: String) {
        prefs.edit().putString(KEY_USER_PHONE, phone).apply()
    }
    
    fun getUserPhone(): String {
        return prefs.getString(KEY_USER_PHONE, "") ?: ""
    }
    
    fun saveUserEmail(email: String) {
        prefs.edit().putString(KEY_USER_EMAIL, email).apply()
    }
    
    fun getUserEmail(): String {
        return prefs.getString(KEY_USER_EMAIL, "") ?: ""
    }
    
    fun saveUserAddress(address: String) {
        prefs.edit().putString(KEY_USER_ADDRESS, address).apply()
    }
    
    fun getUserAddress(): String {
        return prefs.getString(KEY_USER_ADDRESS, "") ?: ""
    }
    
    fun saveUserEmergencyContact(name: String) {
        prefs.edit().putString(KEY_USER_EMERGENCY_CONTACT, name).apply()
    }
    
    fun getUserEmergencyContact(): String {
        return prefs.getString(KEY_USER_EMERGENCY_CONTACT, "") ?: ""
    }
    
    fun saveUserEmergencyPhone(phone: String) {
        prefs.edit().putString(KEY_USER_EMERGENCY_PHONE, phone).apply()
    }
    
    fun getUserEmergencyPhone(): String {
        return prefs.getString(KEY_USER_EMERGENCY_PHONE, "") ?: ""
    }
    
    fun saveUserBloodType(bloodType: String) {
        prefs.edit().putString(KEY_USER_BLOOD_TYPE, bloodType).apply()
    }
    
    fun getUserBloodType(): String {
        return prefs.getString(KEY_USER_BLOOD_TYPE, "") ?: ""
    }
    
    fun saveUserAllergies(allergies: String) {
        prefs.edit().putString(KEY_USER_ALLERGIES, allergies).apply()
    }
    
    fun getUserAllergies(): String {
        return prefs.getString(KEY_USER_ALLERGIES, "") ?: ""
    }
    
    fun saveUserMedicalHistory(history: String) {
        prefs.edit().putString(KEY_USER_MEDICAL_HISTORY, history).apply()
    }
    
    fun getUserMedicalHistory(): String {
        return prefs.getString(KEY_USER_MEDICAL_HISTORY, "") ?: ""
    }
    
    fun saveUserHeight(height: Float) {
        prefs.edit().putFloat(KEY_USER_HEIGHT, height).apply()
    }
    
    fun getUserHeight(): Float {
        return prefs.getFloat(KEY_USER_HEIGHT, 0f)
    }
    
    fun saveUserWeight(weight: Float) {
        prefs.edit().putFloat(KEY_USER_WEIGHT, weight).apply()
    }
    
    fun getUserWeight(): Float {
        return prefs.getFloat(KEY_USER_WEIGHT, 0f)
    }
    
    fun saveUserCommunityCode(code: String) {
        prefs.edit().putString(KEY_USER_COMMUNITY_CODE, code).apply()
    }
    
    fun getUserCommunityCode(): String {
        return prefs.getString(KEY_USER_COMMUNITY_CODE, "") ?: ""
    }
    
    // 获取完整的个人信息 JSON（用于上传）
    fun getUserProfileJson(): String {
        return """
            {
                "name": "${getUserName()}",
                "age": ${getUserAge()},
                "gender": "${getUserGender()}",
                "phone": "${getUserPhone()}",
                "email": "${getUserEmail()}",
                "address": "${getUserAddress()}",
                "emergencyContact": "${getUserEmergencyContact()}",
                "emergencyPhone": "${getUserEmergencyPhone()}",
                "bloodType": "${getUserBloodType()}",
                "allergies": "${getUserAllergies()}",
                "medicalHistory": "${getUserMedicalHistory()}",
                "height": ${getUserHeight()},
                "weight": ${getUserWeight()},
                "communityCode": "${getUserCommunityCode()}"
            }
        """.trimIndent()
    }

// ✅ 新增：保存和获取设置引导步骤
fun saveSettingsGuideStep(step: Int) {
    prefs.edit().putInt(KEY_SETTINGS_GUIDE_STEP, step).apply()
}

fun getSettingsGuideStep(): Int {
    return prefs.getInt(KEY_SETTINGS_GUIDE_STEP, 0)
}

// ✅ 以下方法供 SleepMonitorService 调用

/**
 * 获取 Gson 实例（公开）
 */
fun getGson(): com.google.gson.Gson {
    return gson
}

/**
 * 直接保存警报历史记录（公开）
 */
fun saveAlertHistoryDirectly(history: MutableList<AlertHistoryRecord>) {
    val json = gson.toJson(history)
    prefs.edit().putString(KEY_ALERT_HISTORY, json).apply()
}

// ========== 守护功能相关方法 ==========

/**
 * 获取所有守护对象列表
 */
fun getGuardianTargets(): List<com.livewell.model.GuardianTarget> {
    val json = prefs.getString(KEY_GUARDIAN_TARGETS, null) ?: return emptyList()
    val type = object : com.google.gson.reflect.TypeToken<List<com.livewell.model.GuardianTarget>>() {}.type
    return try {
        gson.fromJson(json, type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * 保存守护对象列表
 */
fun saveGuardianTargets(targets: List<com.livewell.model.GuardianTarget>) {
    val json = gson.toJson(targets)
    prefs.edit().putString(KEY_GUARDIAN_TARGETS, json).apply()
}

/**
 * 添加守护对象
 */
fun addGuardianTarget(target: com.livewell.model.GuardianTarget) {
    val targets = getGuardianTargets().toMutableList()
    targets.add(target)
    saveGuardianTargets(targets)
}

/**
 * 删除守护对象
 */
fun removeGuardianTarget(id: String) {
    val targets = getGuardianTargets().toMutableList()
    targets.removeAll { it.id == id }
    saveGuardianTargets(targets)
    
    // 同时删除相关的警报
    val alerts = getGuardianAlerts("").filter { it.targetId != id }
    saveAllGuardianAlerts(alerts)
}

/**
 * 更新守护对象
 */
fun updateGuardianTarget(target: com.livewell.model.GuardianTarget) {
    val targets = getGuardianTargets().toMutableList()
    val index = targets.indexOfFirst { it.id == target.id }
    if (index >= 0) {
        targets[index] = target
        saveGuardianTargets(targets)
    }
}

/**
 * 获取当前选中的守护对象 ID
 */
fun getCurrentTargetId(): String? {
    return prefs.getString(KEY_CURRENT_TARGET_ID, null)
}

/**
 * 设置当前选中的守护对象
 */
fun setCurrentTargetId(targetId: String?) {
    prefs.edit().putString(KEY_CURRENT_TARGET_ID, targetId).apply()
}

/**
 * 获取指定守护对象的警报列表
 */
fun getGuardianAlerts(targetId: String): List<com.livewell.model.GuardianAlert> {
    val allAlerts = getAllGuardianAlerts()
    return allAlerts.filter { it.targetId == targetId }.sortedByDescending { it.timestamp }
}

/**
 * 获取所有警报列表
 */
private fun getAllGuardianAlerts(): List<com.livewell.model.GuardianAlert> {
    val json = prefs.getString(KEY_GUARDIAN_ALERTS, null) ?: return emptyList()
    val type = object : com.google.gson.reflect.TypeToken<List<com.livewell.model.GuardianAlert>>() {}.type
    return try {
        gson.fromJson(json, type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * 保存所有警报列表
 */
private fun saveAllGuardianAlerts(alerts: List<com.livewell.model.GuardianAlert>) {
    val json = gson.toJson(alerts)
    prefs.edit().putString(KEY_GUARDIAN_ALERTS, json).apply()
}

/**
 * 添加警报记录
 */
fun addGuardianAlert(alert: com.livewell.model.GuardianAlert) {
    val alerts = getAllGuardianAlerts().toMutableList()
    alerts.add(0, alert) // 新警报添加到开头
    
    // 限制最多保存 100 条警报
    if (alerts.size > 100) {
        alerts.removeAt(alerts.lastIndex)
    }
    
    saveAllGuardianAlerts(alerts)
}

/**
 * 标记警报为已读
 */
fun markAlertAsRead(alertId: String) {
    val alerts = getAllGuardianAlerts().toMutableList()
    val index = alerts.indexOfFirst { it.id == alertId }
    if (index >= 0) {
        alerts[index] = alerts[index].copy(isRead = true)
        saveAllGuardianAlerts(alerts)
    }
}

/**
 * 清除已读警报
 */
fun clearReadAlerts(targetId: String? = null) {
    val alerts = getAllGuardianAlerts().toMutableList()
    val filteredAlerts = if (targetId.isNullOrEmpty()) {
        alerts.filter { !it.isRead }
    } else {
        alerts.filter { !(it.isRead && it.targetId == targetId) }
    }
    saveAllGuardianAlerts(filteredAlerts)
}

/**
 * 获取未读警报数量
 */
fun getUnreadAlertsCount(): Int {
    return getAllGuardianAlerts().count { !it.isRead }
}

// ========== 时光胶囊相关方法（新增） ==========

/**
 * 时光胶囊总开关
 */
fun setTimeCapsuleEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_TIME_CAPSULE_ENABLED, enabled).apply()
}

fun isTimeCapsuleEnabled(): Boolean {
    return prefs.getBoolean(KEY_TIME_CAPSULE_ENABLED, false)
}

/**
 * 紧急邮件发送功能开关
 */
fun setEmergencyEmailEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_EMERGENCY_EMAIL_ENABLED, enabled).apply()
}

fun isEmergencyEmailEnabled(): Boolean {
    return prefs.getBoolean(KEY_EMERGENCY_EMAIL_ENABLED, false)
}

/**
 * 密码书功能开关
 */
fun setPasswordBookEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_PASSWORD_BOOK_ENABLED, enabled).apply()
}

fun isPasswordBookEnabled(): Boolean {
    return prefs.getBoolean(KEY_PASSWORD_BOOK_ENABLED, false)
}

/**
 * 无活动阈值天数（默认 3 天）
 */
fun setInactiveThresholdDays(days: Int) {
    prefs.edit().putInt(KEY_INACTIVE_THRESHOLD_DAYS, days).apply()
}

fun getInactiveThresholdDays(): Int {
    return prefs.getInt(KEY_INACTIVE_THRESHOLD_DAYS, 3)
}

/**
 * 紧急邮件预设文本
 */
fun setEmergencyEmailText(text: String) {
    prefs.edit().putString(KEY_EMERGENCY_EMAIL_TEXT, text).apply()
}

fun getEmergencyEmailText(): String {
    return prefs.getString(KEY_EMERGENCY_EMAIL_TEXT, "当你收到这封信，我大概正在某个你找不到的地方，安静地看云。天很高，风很轻，万物都慢了下来。不必急着来找我，我只是把一些来不及说的话，托付给了某个普通的日子——那天阳光穿过窗帘，落在纸上，我以为这样的午后还会有很多。请替我收下它，就像收下一颗多年前种下的种子，它沉默地等过四季，终于在这一天破土，开出一朵小小的花。花开得迟了，但它认得你。") ?: ""
}

/**
 * 密码书问题
 */
fun setPasswordBookQuestion(question: String) {
    prefs.edit().putString(KEY_PASSWORD_BOOK_QUESTION, question).apply()
}

fun getPasswordBookQuestion(): String {
    return prefs.getString(KEY_PASSWORD_BOOK_QUESTION, "") ?: ""
}

/**
 * 密码书答案
 */
fun setPasswordBookAnswer(answer: String) {
    prefs.edit().putString(KEY_PASSWORD_BOOK_ANSWER, answer).apply()
}

fun getPasswordBookAnswer(): String {
    return prefs.getString(KEY_PASSWORD_BOOK_ANSWER, "") ?: ""
}

/**
 * 密码书内容
 */
fun setPasswordBookContent(content: String) {
    prefs.edit().putString(KEY_PASSWORD_BOOK_CONTENT, content).apply()
}

fun getPasswordBookContent(): String {
    return prefs.getString(KEY_PASSWORD_BOOK_CONTENT, "当你收到这封信，我大概正在某个你找不到的地方，安静地看云。天很高，风很轻，万物都慢了下来。不必急着来找我，我只是把一些来不及说的话，托付给了某个普通的日子——那天阳光穿过窗帘，落在纸上，我以为这样的午后还会有很多。请替我收下它，就像收下一颗多年前种下的种子，它沉默地等过四季，终于在这一天破土，开出一朵小小的花。花开得迟了，但它认得你。") ?: ""
}

/**
 * 密码书附件列表（JSON 格式）
 */
fun setPasswordBookAttachments(attachments: List<String>) {
    val json = gson.toJson(attachments)
    prefs.edit().putString(KEY_PASSWORD_BOOK_ATTACHMENTS, json).apply()
}

/**
 * 设置时光胶囊访问密码（用于保护设置界面）
 */
fun setTimeCapsuleAccessPassword(password: String) {
    prefs.edit().putString(KEY_TIME_CAPSULE_ACCESS_PASSWORD, password).apply()
}

fun getPasswordBookAttachments(): List<String> {
    val json = prefs.getString(KEY_PASSWORD_BOOK_ATTACHMENTS, null) ?: return emptyList()
    return try {
        val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
        gson.fromJson(json, type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * 获取时光胶囊访问密码
 */
fun getTimeCapsuleAccessPassword(): String {
    return prefs.getString(KEY_TIME_CAPSULE_ACCESS_PASSWORD, "") ?: ""
}

/**
 * 添加附件
 */
fun addPasswordBookAttachment(path: String) {
    val attachments = getPasswordBookAttachments().toMutableList()
    attachments.add(path)
    setPasswordBookAttachments(attachments)
}

/**
 * 移除附件
 */
fun removePasswordBookAttachment(path: String) {
    val attachments = getPasswordBookAttachments().toMutableList()
    attachments.remove(path)
    setPasswordBookAttachments(attachments)
}

/**
 * 文本套用标记 - 从紧急邮件复制
 */
fun setCopiedFromEmail(copied: Boolean) {
    prefs.edit().putBoolean(KEY_COPIED_FROM_EMAIL, copied).apply()
}

fun isCopiedFromEmail(): Boolean {
    return prefs.getBoolean(KEY_COPIED_FROM_EMAIL, false)
}

/**
 * 文本套用标记 - 从密码书复制
 */
fun setCopiedFromBook(copied: Boolean) {
    prefs.edit().putBoolean(KEY_COPIED_FROM_BOOK, copied).apply()
}

fun isCopiedFromBook(): Boolean {
    return prefs.getBoolean(KEY_COPIED_FROM_BOOK, false)
}

// ========== 功能设置界面相关方法 ==========

/**
 * 自动报警模式开关
 */
fun isAutoAlarmEnabled(): Boolean {
    return prefs.getBoolean("auto_alarm_enabled", true)
}

fun setAutoAlarmEnabled(enabled: Boolean) {
    prefs.edit().putBoolean("auto_alarm_enabled", enabled).apply()
}

/**
 * 报警模式（hybrid/time/steps）
 */
fun getAlarmMode(): String {
    return prefs.getString("alarm_mode", "hybrid") ?: "hybrid"
}

fun saveAlarmMode(mode: String) {
    prefs.edit().putString("alarm_mode", mode).apply()
}

/**
 * 每日报警检查小时
 */
fun getAlarmCheckHour(): Int {
    return prefs.getInt(KEY_ALERT_CHECK_HOUR, 20)
}

fun saveAlarmCheckHour(hour: Int) {
    prefs.edit().putInt(KEY_ALERT_CHECK_HOUR, hour).apply()
}

/**
 * 每日报警检查分钟
 */
fun getAlarmCheckMinute(): Int {
    return prefs.getInt(KEY_ALERT_CHECK_MINUTE, 30)
}

fun saveAlarmCheckMinute(minute: Int) {
    prefs.edit().putInt(KEY_ALERT_CHECK_MINUTE, minute).apply()
}

/**
 * 警报持续时长（分钟）
 */
fun saveAlertDuration(minutes: Int) {
    prefs.edit().putInt(KEY_ALERT_DURATION, minutes).apply()
}

/**
 * 终极保活模式开关
 */
fun isProModeEnabled(): Boolean {
    return prefs.getBoolean("pro_mode_enabled", false)
}

fun setProModeEnabled(enabled: Boolean) {
    prefs.edit().putBoolean("pro_mode_enabled", enabled).apply()
}

// ==================== 睡前快照相关方法 ====================

/**
 * ✅ 保存睡前活动数据快照
 */
fun savePreSleepSnapshot(date: String, steps: Int, usageMinutes: Int, sleepTime: Long) {
    prefs.edit()
        .putString("pre_sleep_steps_$date", steps.toString())
        .putString("pre_sleep_usage_$date", usageMinutes.toString())
        .putLong("pre_sleep_time_$date", sleepTime)
        .apply()
    
    android.util.Log.i("PrefsManager", "✅ 睡前快照已保存：日期=$date, 步数=$steps, 使用时长=${usageMinutes}分钟")
}

/**
 * ✅ 获取指定日期的睡前快照
 */
fun getPreSleepSnapshot(date: String): Triple<Int, Int, Long>? {
    val steps = getString("pre_sleep_steps_$date")?.toIntOrNull() ?: return null
    val usage = getString("pre_sleep_usage_$date")?.toIntOrNull() ?: return null
    val time = getLong("pre_sleep_time_$date", 0)
    
    if (time == 0L) return null
    
    return Triple(steps, usage, time)
}

/**
 * ✅ 删除指定日期的睡前快照
 */
fun removePreSleepSnapshot(date: String) {
    prefs.edit()
        .remove("pre_sleep_steps_$date")
        .remove("pre_sleep_usage_$date")
        .remove("pre_sleep_time_$date")
        .apply()
}

/**
 * ✅ 获取所有偏好设置（用于调试）
 */
fun getAllPrefs(): Map<String, *> {
    return prefs.all
}

/**
 * ✅ 清理 7 天前的旧快照
 */
fun cleanupOldSnapshots(currentDate: String) {
    try {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        calendar.time = dateFormat.parse(currentDate) ?: Date()
        
        var cleanedCount = 0
        
        // 删除 7-30 天前的快照
        for (i in 7..30) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val oldDate = dateFormat.format(calendar.time)
            
            val hadSnapshot = getLong("pre_sleep_time_$oldDate", 0) > 0
            if (hadSnapshot) {
                removePreSleepSnapshot(oldDate)
                cleanedCount++
            }
        }
        
        if (cleanedCount > 0) {
            android.util.Log.i("PrefsManager", "✅ 已清理 $cleanedCount 个旧快照")
        }
        
    } catch (e: Exception) {
        android.util.Log.e("PrefsManager", "❌ 清理旧快照失败：${e.message}", e)
    }
}

/**
 * ✅ 保存最近的活动状态（每 5 分钟更新）
 */
fun saveLastActiveState(steps: Int, usageMinutes: Int, timestamp: Long) {
    prefs.edit()
        .putLong("last_active_steps", steps.toLong())
        .putLong("last_active_usage", usageMinutes.toLong())
        .putLong("last_active_time", timestamp)
        .apply()
}

/**
 * ✅ 获取最近的活动状态
 */
fun getLastActiveState(): Triple<Int, Int, Long> {
    val steps = getLong("last_active_steps", 0).toInt()
    val usage = getLong("last_active_usage", 0).toInt()
    val time = getLong("last_active_time", 0)
    return Triple(steps, usage, time)
}

/**
 * ✅ 获取考虑跨天睡眠的完整日活动数据
 * @param context Context，用于获取实时数据
 * @return Pair<步数, 使用时长>
 */
fun getCompleteDayActivity(context: android.content.Context): Pair<Int, Int> {
    return try {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        // 获取昨天的日期
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        
        android.util.Log.i("PrefsManager", "🔍 查询完整日活动数据：")
        android.util.Log.i("PrefsManager", "   今天日期：$today")
        android.util.Log.i("PrefsManager", "   查询昨天快照：$yesterdayStr")
        
        // 检查是否有昨天的睡前快照
        val snapshot = getPreSleepSnapshot(yesterdayStr)
        
        if (snapshot != null) {
            android.util.Log.i("PrefsManager", "   ✅ 找到昨天快照：步数=${snapshot.first}, 使用=${snapshot.second}")
        } else {
            android.util.Log.w("PrefsManager", "   ❌ 未找到昨天快照！")
        }
        
        // 获取当前实时数据
        val currentSteps = com.livewell.untils.SystemStepManager.getInstance(context).getTodaySteps(syncIfNeeded = true)
        val currentUsage = com.livewell.untils.UsageStatsHelper(context).getTodayAppUsageMinutes()
        
        if (snapshot != null) {
            val (preSleepSteps, preSleepUsage, preSleepTime) = snapshot
            
            // 有睡前快照，计算午夜后的增量
            val stepsSinceMidnight = maxOf(0, currentSteps - preSleepSteps)
            val usageSinceMidnight = maxOf(0, currentUsage.toInt() - preSleepUsage)
            
            android.util.Log.d("PrefsManager", "📊 跨天活动数据（使用睡前快照）：")
            android.util.Log.d("PrefsManager", "  昨天睡前：步数=$preSleepSteps, 使用=$preSleepUsage")
            android.util.Log.d("PrefsManager", "  今天当前：步数=$currentSteps, 使用=$currentUsage")
            android.util.Log.d("PrefsManager", "  午夜后：步数=$stepsSinceMidnight, 使用=$usageSinceMidnight")
            
            Pair(stepsSinceMidnight, usageSinceMidnight)
        } else {
            // 没有睡前快照，检查是否有跨天未入睡的情况
            val lastActiveState = getLastActiveState()
            val (_, _, lastActiveTime) = lastActiveState
            
            if (lastActiveTime > 0) {
                val hoursSinceLastActive = (System.currentTimeMillis() - lastActiveTime) / (1000 * 60 * 60)
                
                if (hoursSinceLastActive < 2) {
                    // 用户最近 2 小时内还有活动，可能是跨天未睡
                    android.util.Log.w("PrefsManager", "⚠️ 检测到跨天未入睡，使用原始数据（可能不准确）")
                }
            }
            
            // 降级方案：返回原始数据
            Pair(currentSteps, currentUsage.toInt())
        }
        
    } catch (e: Exception) {
        android.util.Log.e("PrefsManager", "❌ 获取完整日活动数据失败：${e.message}", e)
        // 降级方案：返回原始数据
        val currentSteps = com.livewell.untils.SystemStepManager.getInstance(context).getTodaySteps(syncIfNeeded = true)
        val currentUsage = com.livewell.untils.UsageStatsHelper(context).getTodayAppUsageMinutes()
        Pair(currentSteps, currentUsage.toInt())
    }
}

/**
 * ✅ 检查数据完整性
 * @return true=数据完整, false=数据不完整
 */
fun checkDataIntegrity(context: android.content.Context): Boolean {
    try {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        
        val hasPreSleepSnapshot = getLong("pre_sleep_time_$yesterdayStr", 0) > 0
        val currentSteps = com.livewell.untils.SystemStepManager.getInstance(context).getTodaySteps(syncIfNeeded = true)
        val currentUsage = com.livewell.untils.UsageStatsHelper(context).getTodayAppUsageMinutes()
        val hasTodayData = currentSteps > 0 || currentUsage > 0
        
        if (!hasPreSleepSnapshot && hasTodayData) {
            android.util.Log.w("PrefsManager", "⚠️ 数据完整性检查失败：缺少昨天睡前快照")
            return false
        }
        
        return true
        
    } catch (e: Exception) {
        android.util.Log.e("PrefsManager", "❌ 数据完整性检查失败：${e.message}", e)
        return false
    }
}

}