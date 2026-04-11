package com.livewell.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.livewell.MainActivity
import com.livewell.R
import com.livewell.untils.PrefsManager
import com.livewell.untils.SecurePrefsManager
import com.livewell.untils.SmsSender
import com.livewell.untils.UsageStatsHelper
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.livewell.receiver.AlertConfirmReceiver
import android.os.Handler
import android.os.Looper
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



class CheckinService : Service() {
    
    private lateinit var prefsManager: PrefsManager
    private lateinit var usageStatsHelper: UsageStatsHelper
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private lateinit var systemStepManager: SystemStepManager
    private val tag = "CheckinService"
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "checkin_alerts_channel"
        
        fun start(context: Context) {
            val intent = Intent(context, CheckinService::class.java)
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
    usageStatsHelper = UsageStatsHelper(this)
    systemStepManager = SystemStepManager.getInstance(this)
    
    createAlertNotificationChannel()
    // createUnifiedNotificationChannel() - 已删除，UnifiedNotificationService 会自己创建
    
    UnifiedNotificationService.checkinServiceRunning = true
    UnifiedNotificationService.checkinInfo = "安全守护运行中"
    
    // ✅ 使用统一通知渠道作为前台服务通知，避免显示多个独立通知
    startForeground(UnifiedNotificationService.NOTIFICATION_ID, createUnifiedServiceNotification())
    
    if (systemStepManager.hasStepCounter()) {
        systemStepManager.registerListener()
        systemStepManager.loadFromPrefs(prefsManager)
    }
    
    // ✅ 在主线程立即设置闹钟（非耗时操作）
    scheduleAlarm()
    scheduleDailyStepReset()
    
    // ✅ 延时任务在后台线程执行
    executor.execute {
        scheduleChecks()
    }
}

    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ✅ 处理超时触发的警报
        if (intent?.action == "TRIGGER_ALERT") {
            Log.i(tag, "收到 TRIGGER_ALERT 指令，立即触发警报")
            // ✅ 写入文件日志
            com.livewell.untils.AppLogger.i(tag, "⏰ 报警确认超时，触发真正警报")
            triggerAlert()
        }
        
        // ✅ 处理稍后提醒的重新检查
        if (intent?.action == "SNOOZE_RECHECK") {
            Log.i(tag, "收到 SNOOZE_RECHECK 指令，执行安全检查")
            // ✅ 写入文件日志
            com.livewell.untils.AppLogger.i(tag, "⏰ 稍后提醒时间到，重新检查")
            performSafetyCheck()
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
      override fun onDestroy() {
    executor.shutdown()
    if (::systemStepManager.isInitialized) {
        systemStepManager.saveToPrefs(prefsManager)
        Log.i(tag, "服务销毁，保存最终步数：${systemStepManager.getTodaySteps()}")
    }
    systemStepManager.unregisterListener()
    UnifiedNotificationService.checkinServiceRunning = false
    UnifiedNotificationService.checkinInfo = ""
    UnifiedNotificationService.updateNotification(this)
    serviceScope.cancel()
    super.onDestroy()
}

    
    
     private fun createAlertNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "安全警报通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "用于发送安全警报和异常情况通知"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    
    private fun scheduleChecks() {
    // 读取配置的报警时间
    val alertHour = prefsManager.getAlertCheckHour()
    val alertMinute = prefsManager.getAlertCheckMinute()
    
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, alertHour)
        set(Calendar.MINUTE, alertMinute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    
    var initialDelay = target.timeInMillis - now.timeInMillis
    if (initialDelay < 0) {
        initialDelay += TimeUnit.DAYS.toMillis(1)
    }
    
    Log.i(tag, "下次检查时间：${alertHour}:${String.format("%02d", alertMinute)}，延迟：${initialDelay / 1000 / 60}分钟")
    
    executor.scheduleAtFixedRate({
        performSafetyCheck()
    }, initialDelay, TimeUnit.DAYS.toMillis(1), TimeUnit.MILLISECONDS)
}
    
   
    
       private fun triggerAlert() {
         Log.i(tag, "====== 触发警报 ======")
    // ✅ 写入文件日志
    com.livewell.untils.AppLogger.i(tag, "🚨 触发签到警报")
    
    // ✅ 记录最后报警时间（避免重复报警）
    prefsManager.saveLastAlertTime(System.currentTimeMillis())
    Log.i(tag, "已记录最后报警时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
    
    val notifyType = prefsManager.getNotifyType()
    val reason = buildAlertReason()
         Log.i(tag, "通知方式：$notifyType")
    
    // ✅ 向社区后端发送警报（如果是社区守护模式）
    if (prefsManager.isCommunityEnabled()) {
        sendAlertToCommunity(reason)
    }
    
    when (notifyType) {
        PrefsManager.NOTIFY_PHONE -> {
             Log.i(tag, "准备发送短信")
            // ✅ 写入文件日志
            com.livewell.untils.AppLogger.i(tag, "📱 准备发送签到警报短信")
            val contact = prefsManager.getEmergencyContact()
            val message = prefsManager.getAlertMessage()
            if (!contact.isNullOrEmpty()) {
                sendSmsAlert(contact, message)
                // ✅ 获取当前步数和使用时长（考虑跨天）
                val (usageMinutes, currentSteps) = prefsManager.getCompleteDayActivity(this@CheckinService)
                
                prefsManager.addAlertHistory(AlertHistoryRecord(
                    timestamp = System.currentTimeMillis(),
                    type = AlertType.CHECKIN,
                    status = AlertStatus.PENDING,
                    method = AlertMethod.SMS,
                    content = message.take(50),
                    reason = reason,
                    steps = currentSteps,  // ✅ 已经是 Int
                    usageMinutes = usageMinutes  // ✅ 已经是 Int
                ))
            }
        }
        PrefsManager.NOTIFY_EMAIL -> {
             Log.i(tag, "准备发送邮件") 
            // ✅ 写入文件日志
            com.livewell.untils.AppLogger.i(tag, "📧 准备发送签到警报邮件") 
            sendEmailAlert()
            // ✅ 获取当前步数和使用时长（考虑跨天）
            val (usageMinutes, currentSteps) = prefsManager.getCompleteDayActivity(this@CheckinService)
            
            prefsManager.addAlertHistory(AlertHistoryRecord(
                timestamp = System.currentTimeMillis(),
                type = AlertType.CHECKIN,
                status = AlertStatus.PENDING,
                method = AlertMethod.EMAIL,
                content = prefsManager.getAlertMessage().take(50),
                reason = reason,
                steps = currentSteps,
                usageMinutes = usageMinutes
            ))
        }
        PrefsManager.NOTIFY_BOTH -> {
            Log.i(tag, "准备同时发送邮件和短信")
            sendEmailAlert()
            val contact = prefsManager.getEmergencyContact()
            val message = prefsManager.getAlertMessage()
            if (!contact.isNullOrEmpty()) {
                sendSmsAlert(contact, message)
            }
            prefsManager.addAlertHistory(AlertHistoryRecord(
                timestamp = System.currentTimeMillis(),
                type = AlertType.CHECKIN,
                status = AlertStatus.PENDING,
                method = AlertMethod.BOTH,
                content = prefsManager.getAlertMessage().take(50),
                reason = reason
            ))
        }
    }
    
    sendAlertNotification()
}

// 添加构建原因的方法
// 修改 buildAlertReason() 方法
private fun buildAlertReason(): String {
    val reasons = mutableListOf<String>()
    
    // 自动报警模式不需要检查签到
    if (!prefsManager.isAutoAlertModeEnabled()) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastCheckin = prefsManager.getLastCheckinDate()
        if (lastCheckin != today) {
            reasons.add("未签到")
        }
    }
    
    // 今日使用时长检测（✅ 使用新的方法获取考虑跨天的数据）
    val (todayUsage, stepCount) = prefsManager.getCompleteDayActivity(this@CheckinService)
    val usageThreshold = prefsManager.getAppUsageThreshold()
    if (todayUsage < usageThreshold) {
        reasons.add("今日使用${todayUsage}min < ${usageThreshold}min")
    }
    
    // 步数检测
    val stepThreshold = prefsManager.getStepThreshold()
    if (prefsManager.isStepMonitorEnabled() && stepCount < stepThreshold) {
        reasons.add("步数${stepCount} < ${stepThreshold}")
    }
    
    return reasons.joinToString("; ")
}

// 修改 performSafetyCheck() 方法
private fun performSafetyCheck() {
    Log.i(tag, "====== 开始安全检查 ======")
    
    if (prefsManager.isTestMode()) {
        Log.i(tag, "测试模式，执行测试检查")
        performTestCheck()
        return
    }
    
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val lastCheckin = prefsManager.getLastCheckinDate()
    
    // ✅ 检查今日是否已经报过警（避免重复报警）
    val lastAlertTime = prefsManager.getLastAlertTime()
    val lastAlertDate = if (lastAlertTime > 0) {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(lastAlertTime))
    } else {
        ""
    }
    
    if (lastAlertDate == today) {
        Log.i(tag, "⚠️ 今日已报过警（${lastAlertDate}），跳过本次检查")
        return
    }
    
    // ✅ 获取今日使用时长和步数（考虑跨天睡眠）
    val (todayUsage, stepCount) = prefsManager.getCompleteDayActivity(this@CheckinService)
    
    val usageThreshold = prefsManager.getAppUsageThreshold()
    val stepThreshold = prefsManager.getStepThreshold()
    
    val usageAbnormal = todayUsage < usageThreshold
    val stepAbnormal = if (prefsManager.isStepMonitorEnabled()) {
        stepCount < stepThreshold
    } else {
        false
    }
    
    Log.i(tag, "检查结果：")
    Log.i(tag, "  - 今日日期：$today")
    Log.i(tag, "  - 最后签到：$lastCheckin")
    Log.i(tag, "  - 今日使用：${todayUsage}分钟（阈值：${usageThreshold}分钟）")
    Log.i(tag, "  - 今日步数：${stepCount}步（阈值：${stepThreshold}步）")
    Log.i(tag, "  - 使用异常：$usageAbnormal")
    Log.i(tag, "  - 步数异常：$stepAbnormal")
    Log.i(tag, "  - 自动报警模式：${prefsManager.isAutoAlertModeEnabled()}")
    Log.i(tag, "  - 需要确认：${prefsManager.isAlertConfirmEnabled()}")
    
    // ✅ 自动报警模式逻辑
    if (prefsManager.isAutoAlertModeEnabled()) {
        Log.i(tag, "进入自动报警模式处理")
        handleAutoAlertMode(usageAbnormal, stepAbnormal)
        return
    }
    
    // 原有逻辑：默认模式 - 需要签到 + 使用时间判定
    if (lastCheckin == today) {
        Log.i(tag, "用户今日已签到，跳过警报检查")
        return
    }
    
    Log.i(tag, "用户今日未签到，继续检查...")
    
    // 未签到，检查是否有任何异常
    if (usageAbnormal || stepAbnormal) {
        Log.i(tag, "检测到异常，准备触发警报")
        if (prefsManager.isAlertConfirmEnabled()) {
            Log.i(tag, "需要确认，发送确认通知")
            sendConfirmNotification()
        } else {
            Log.i(tag, "直接触发警报")
            triggerAlert()
        }
    } else {
        Log.i(tag, "使用时长和步数均正常，不触发警报")
    }
}

// ✅ 修改自动报警模式处理方法
private fun handleAutoAlertMode(usageAbnormal: Boolean, stepAbnormal: Boolean) {
    val criteria = prefsManager.getAlertCriteria()
    val shouldAlert = when (criteria) {
        PrefsManager.CRITERIA_USAGE_ONLY -> {
            // ✅ 纯使用时长：今日使用时间低于阈值
            usageAbnormal
        }
        PrefsManager.CRITERIA_STEP_ONLY -> {
            // 纯步数：步数低于阈值
            stepAbnormal
        }
        PrefsManager.CRITERIA_MIXED -> {
            // ✅ 混合模式：使用时长异常 OR 步数异常
            usageAbnormal || stepAbnormal
        }
        else -> false
    }
    
    Log.i(tag, "自动报警模式 - 评判标准：$criteria, 是否触发：$shouldAlert")
    
    if (shouldAlert) {
        // ✅ 检查今日是否已报过警
        val lastAlertTime = prefsManager.getLastAlertTime()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastAlertDate = if (lastAlertTime > 0) {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(lastAlertTime))
        } else {
            ""
        }
        
        if (lastAlertDate == today) {
            Log.i(tag, "⚠️ 自动报警模式：今日已报过警，跳过")
            return
        }
        
        if (prefsManager.isAlertConfirmEnabled()) {
            sendConfirmNotification()
        } else {
            triggerAlert()
        }
    }
}






// 修改 generateTestReport() 方法
private fun generateTestReport(
    isSigned: Boolean,
    appUsage: Int,
    stepCount: Int,
    wakeHour: Int,
    wakeMinute: Int
): String {
    val usageThreshold = prefsManager.getAppUsageThreshold()
    val stepThreshold = prefsManager.getStepThreshold()
    
    val report = StringBuilder()
    report.append("【${getString(R.string.app_name)}】测试报告\n")
    report.append("时间：${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}\n")
    report.append("================================\n\n")
    
    report.append("📅 签到状态：${if (isSigned) "已签到 ✅" else "未签到 ❌"}\n")
    
    // ✅ 改为今日使用时长
    report.append("📱 今日使用：${appUsage}分钟")
    if (appUsage < usageThreshold) {
        report.append(" (低于阈值${usageThreshold}分钟) ⚠️\n")
    } else {
        report.append(" (正常)\n")
    }
    
    if (prefsManager.isStepMonitorEnabled()) {
        report.append("👣 今日步数：${stepCount}步")
        if (stepCount < stepThreshold) {
            report.append(" (低于阈值${stepThreshold}步) ⚠️\n")
        } else {
            report.append(" (正常)\n")
        }
    }
    
    report.append("⏰ 测试起床时间：${wakeHour}:${String.format("%02d", wakeMinute)}\n")
    report.append("\n================================\n")
    report.append("此为测试数据，非真实情况")
    
    return report.toString()
}

// ✅ 移除 getAdaptiveThresholds() 方法中关于开机时间的逻辑
// 或者保留但只调整使用时长阈值
    
    private fun sendSmsAlert(phoneNumber: String, message: String) {
        try {
            val smsSender = SmsSender(this)
            smsSender.sendAlertSms(phoneNumber, message)
            Log.i(tag, "短信已加入发送队列")
        } catch (e: Exception) {
            Log.e(tag, "短信发送异常：${e.message}")
            sendAlertNotification()
        }
    }

    
private fun sendEmailAlert() {
    Log.i(tag, "====== sendEmailAlert 被调用 ======")
    Log.i(tag, "准备发送警报邮件")
    
    val securePrefs = SecurePrefsManager(this)
    val fromEmail = securePrefs.getEmailAccount()
    val authCode = securePrefs.getEmailAuthCode()
    val host = securePrefs.getSmtpHost() ?: prefsManager.getEmailSmtpHost()
    val port = securePrefs.getSmtpPort() ?: prefsManager.getEmailSmtpPort()
    val toEmail = prefsManager.getEmailTo()
    
    Log.i(tag, "发件邮箱配置：${if (fromEmail.isNullOrEmpty()) "❌ 为空" else "✅ 已配置 ($fromEmail)"}")
    Log.i(tag, "授权码配置：${if (authCode.isNullOrEmpty()) "❌ 为空" else "✅ 已配置 (${authCode.take(4)}...)"}")
    Log.i(tag, "SMTP 主机：$host")
    Log.i(tag, "SMTP 端口：$port")
    Log.i(tag, "收件邮箱：${if (toEmail.isNullOrEmpty()) "❌ 为空" else "✅ 已配置 ($toEmail)"}")
    
    if (fromEmail.isNullOrEmpty() || authCode.isNullOrEmpty() || toEmail.isNullOrEmpty()) {
        Log.e(tag, "❌ 邮件配置不完整，无法发送邮件")
        Log.e(tag, "fromEmail 为空：${fromEmail.isNullOrEmpty()}")
        Log.e(tag, "authCode 为空：${authCode.isNullOrEmpty()}")
        Log.e(tag, "toEmail 为空：${toEmail.isNullOrEmpty()}")
        sendAlertNotification()
        return
    }
    
    Log.i(tag, "✅ 邮件配置完整，开始发送邮件...")
    
    val mailSender = MailSender()
    // 构建完整的警报内容：用户自定义消息 + 步数和使用时长信息
    val userMessage = prefsManager.getAlertMessage()
    val additionalInfo = generateGuardianStatusInfo()
    val fullContent = buildFullAlertContent(userMessage, additionalInfo)
    
    Log.i(tag, "邮件主题：【安守】紧急警报")
    Log.i(tag, "邮件内容长度：${fullContent.length} 字符")
    
    mailSender.sendEmail(
        host = host,
        port = port,
        fromEmail = fromEmail,
        authCode = authCode,
        toEmail = toEmail,
        subject = "【安守】紧急警报",
        content = fullContent,
        callback = object : MailSender.SendCallback {
            override fun onSuccess() {
                Log.i(tag, "✅ 警报邮件发送成功")
                updateLastEmailRecordStatus(AlertStatus.SUCCESS)
                sendAlertNotification()
            }
            
            override fun onError(error: String) {
                Log.e(tag, "❌ 邮件发送失败：$error")
                updateLastEmailRecordStatus(AlertStatus.FAILED)
                trySmsFallback()
            }
        },
        context = this  // ✅ 添加这一行
    )
}
    
    private fun trySmsFallback() {
        val contact = prefsManager.getEmergencyContact()
        if (!contact.isNullOrEmpty()) {
            // 构建完整的警报内容：用户自定义消息 + 步数和使用时长信息
            val userMessage = prefsManager.getAlertMessage()
            val additionalInfo = generateGuardianStatusInfo()
            val fullContent = buildFullAlertContent(userMessage, additionalInfo)
            sendSmsAlert(contact, fullContent)
        } else {
            sendAlertNotification()
        }
    }
    
    private fun sendAlertNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.alert_triggered))
            .setContentText(getString(R.string.alert_sent))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1002, notification)
    }

private fun getAdaptiveThresholds(): Pair<Int, Int> {
    val mode = prefsManager.getAppMode()
    if (mode != PrefsManager.MODE_GUARDIAN && mode != PrefsManager.MODE_MIXED) {
        return Pair(prefsManager.getAppUsageThreshold(), prefsManager.getAppUsageThreshold())
    }
    
    if (!prefsManager.isSmartModeEnabled()) {
        return Pair(prefsManager.getAppUsageThreshold(), prefsManager.getAppUsageThreshold())
    }
    
    // 智能模式：根据历史数据动态调整
    val history = prefsManager.getHistoryData()
    
    // 获取平均使用时间
    val avgBootTime = getAverageBootTime()
    val avgAppUsage = getAverageAppUsage()
    
    var bootThreshold = prefsManager.getAppUsageThreshold()
    var appThreshold = prefsManager.getAppUsageThreshold()
    
    // 如果用户平均开机时间很长但应用使用很少，可能是在看电视/睡觉
    if (avgBootTime > 12 && avgAppUsage < 10) {
        bootThreshold = (bootThreshold * 1.5).toInt() // 放宽阈值
    }
    
    // 如果用户平均应用使用时间很长，收紧阈值
    if (avgAppUsage > 30) {
        appThreshold = (appThreshold * 0.8).toInt()
    }
    
    return Pair(bootThreshold, appThreshold)
}

// 修改 performSafetyCheck 方法
/**
 * 执行安全检查
 */
/**
 * 执行安全检查
 */




private fun sendConfirmNotification() {
    Log.i(tag, "====== 发送确认通知 ======")
    
    // 检查是否在稍后提醒时间内
    val snoozeTime = prefsManager.getSnoozeTime()
    if (snoozeTime > System.currentTimeMillis()) {
        Log.i(tag, "仍在稍后提醒时间内，跳过通知")
        return
    }
    
    val intent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra("confirm_alert", true)
    }
    
    val pendingIntent = PendingIntent.getActivity(
        this, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    // 创建确认按钮的 Intent
    val confirmIntent = Intent(this, AlertConfirmReceiver::class.java).apply {
        action = AlertConfirmReceiver.ACTION_CONFIRM
    }
    val confirmPendingIntent = PendingIntent.getBroadcast(
        this, 1, confirmIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    // 创建稍后提醒按钮的 Intent
    val snoozeIntent = Intent(this, AlertConfirmReceiver::class.java).apply {
        action = AlertConfirmReceiver.ACTION_SNOOZE
    }
    val snoozePendingIntent = PendingIntent.getBroadcast(
        this, 2, snoozeIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("⚠️ 疑似异常状态 ⚠️")
        .setContentText("检测到您长时间未使用手机，如果您一切正常请点击确认")
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setAutoCancel(false)
        .setOngoing(true)
        .setFullScreenIntent(pendingIntent, true)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .addAction(android.R.drawable.ic_menu_save, "我没事", confirmPendingIntent)
        .addAction(android.R.drawable.ic_menu_revert, "稍后提醒", snoozePendingIntent)
        .build()
    
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(1003, notification)
    
    // ✅ 重置确认状态
    prefsManager.setUserConfirmed(false)
    prefsManager.setSnoozeRequested(false)
    
    // ✅ 使用 AlarmManager 设置超时闹钟（而不是 Handler）
    val duration = prefsManager.getAlertDuration()
    val timeoutTime = System.currentTimeMillis() + duration * 60 * 1000L
    
    Log.i(tag, "设置超时闹钟：${duration}分钟后（${android.icu.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(timeoutTime)}）")
    
    scheduleAlertTimeoutAlarm(timeoutTime)
}

private fun scheduleAlertTimeoutAlarm(timeoutTime: Long) {
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
    
    // ✅ 创建超时触发的 Intent
    val intent = Intent(this, com.livewell.receiver.AlertTimeoutReceiver::class.java).apply {
        action = "com.livewell.ACTION_ALERT_TIMEOUT"
    }
    
    val pendingIntent = android.app.PendingIntent.getBroadcast(
        this, 3002, intent,
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )
    
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                timeoutTime,
                pendingIntent
            )
            Log.i(tag, "✅ 超时闹钟已设置")
        } else {
            alarmManager.setExact(
                android.app.AlarmManager.RTC_WAKEUP,
                timeoutTime,
                pendingIntent
            )
            Log.i(tag, "✅ 超时闹钟已设置（旧版本）")
        }
    } catch (e: Exception) {
        Log.e(tag, "❌ 设置超时闹钟失败：${e.message}")
    }
}

private fun getAverageBootTime(): Long {
    // TODO: 从历史数据中计算平均开机时间
    // 这里简单返回8小时作为默认值
    // 实际应该从 SharedPreferences 中读取历史记录计算
    return 8
}

private fun getAverageAppUsage(): Long {
    // TODO: 从历史数据中计算平均应用使用时间
    // 这里简单返回15分钟作为默认值
    // 实际应该从 SharedPreferences 中读取历史记录计算
    return 15
}

/**
 * 生成守护对象状态信息（步数和使用时长）
 */
private fun generateGuardianStatusInfo(): String {
    // ✅ 使用新的方法获取考虑跨天的数据
    val (todayUsage, stepCount) = prefsManager.getCompleteDayActivity(this@CheckinService)
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
 * 构建完整的警报内容（用户自定义消息 + 状态信息）
 */
private fun buildFullAlertContent(userMessage: String, additionalInfo: String): String {
    val report = StringBuilder()
    report.append("【安守】紧急警报\n")
    report.append("时间：${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}\n")
    report.append("================================\n\n")
    report.append(userMessage)
    report.append(additionalInfo)
    return report.toString()
}

private fun generateExceptionReport(): String {
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val lastCheckin = prefsManager.getLastCheckinDate()
    
    // ✅ 改为今日使用时长（考虑跨天）
    val (todayUsage, stepCount) = prefsManager.getCompleteDayActivity(this@CheckinService)
    val usageThreshold = prefsManager.getAppUsageThreshold()
    
    val stepThreshold = prefsManager.getStepThreshold()
    
    val report = StringBuilder()
    report.append("【${getString(R.string.app_name)}】异常情况报告\n")
    report.append("时间：${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}\n")
    report.append("================================\n\n")
    
    report.append("📅 签到状态：")
    if (lastCheckin == today) {
        report.append("今日已签到 ✅\n")
    } else {
        report.append("今日未签到 ❌\n")
    }
    
    // ✅ 设备使用统计 - 改为今日使用时长
    report.append("📱 设备使用情况：\n")
    report.append("   • 今日使用：${todayUsage}分钟")
    if (todayUsage < usageThreshold) {
        report.append(" (低于阈值${usageThreshold}分钟) ⚠️\n")
    } else {
        report.append(" (正常)\n")
    }
    
    if (prefsManager.isStepMonitorEnabled()) {
        report.append("👣 运动情况：${stepCount}步")
        if (stepCount < stepThreshold) {
            report.append(" (低于阈值${stepThreshold}步) ⚠️\n")
        } else {
            report.append(" (正常) ✅\n")
        }
    }
    
    report.append("\n================================\n")
    report.append("请根据以上信息判断是否需要采取行动。")
    
    return report.toString()
}


private fun getTodayStepCount(): Int {
    if (systemStepManager.hasStepCounter()) {
        // ✅ 强制同步最新数据
        return systemStepManager.getTodaySteps(syncIfNeeded = true)
    }
    
    // 后备方案：模拟数据
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    
    return if (hour >= 22 || hour <= 6) {
        (10..50).random()
    } else {
        (500..2000).random()
    }
}

private fun sendExceptionNotification(report: String) {
    val intent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra("show_report", true)
        putExtra("report_content", report)
    }
    
    val pendingIntent = PendingIntent.getActivity(
        this, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    // 创建查看报告的 Intent
    val viewIntent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra("show_report", true)
        putExtra("report_content", report)
    }
    val viewPendingIntent = PendingIntent.getActivity(
        this, 1, viewIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("📊 异常情况报告")
        .setContentText("检测到异常数据，请查看详情")
        .setStyle(NotificationCompat.BigTextStyle().bigText(report))
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .addAction(android.R.drawable.ic_menu_view, "查看详情", viewPendingIntent)
        .build()
    
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(1006, notification)
}



private fun sendEmailReport(report: String) {
    val securePrefs = SecurePrefsManager(this)
    val fromEmail = securePrefs.getEmailAccount()
    val authCode = securePrefs.getEmailAuthCode()
    val host = securePrefs.getSmtpHost() ?: prefsManager.getEmailSmtpHost()
    val port = securePrefs.getSmtpPort() ?: prefsManager.getEmailSmtpPort()
    val toEmail = prefsManager.getEmailTo()
    
    if (fromEmail.isNullOrEmpty() || authCode.isNullOrEmpty() || toEmail.isNullOrEmpty()) {
        Log.e(tag, "邮件配置不完整")
        return
    }
    
    val mailSender = MailSender()
    mailSender.sendEmail(
        host = host,
        port = port,
        fromEmail = fromEmail,
        authCode = authCode,
        toEmail = toEmail,
        subject = "【${getString(R.string.app_name)}】异常情况报告",
        content = report,
        callback = object : MailSender.SendCallback {
            override fun onSuccess() {
                Log.i(tag, "异常报告邮件发送成功")
                updateLastEmailRecordStatus(AlertStatus.SUCCESS)  // ✅ 新增
            }
            
            override fun onError(error: String) {
                Log.e(tag, "邮件发送失败：$error")
                updateLastEmailRecordStatus(AlertStatus.FAILED)  // ✅ 新增
            }
        },
        context = this
    )
}


/**
 * 执行测试模式下的检查
 */
private fun performTestCheck() {
    Log.i(tag, "执行测试模式检查")
    
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val lastCheckin = if (prefsManager.getTestSigned()) today else "2000-01-01"
    
    // ✅ 移除 bootTime，只保留 appUsage
    val appUsage = prefsManager.getTestAppUsage()
    val stepCount = prefsManager.getTestStepCount()
    
    val usageThreshold = prefsManager.getAppUsageThreshold()
    val stepThreshold = prefsManager.getStepThreshold()
    
    val usageAbnormal = appUsage < usageThreshold
    val stepAbnormal = if (prefsManager.isStepMonitorEnabled()) {
        stepCount < stepThreshold
    } else {
        false
    }
    
    val report = generateTestReport(
        isSigned = prefsManager.getTestSigned(),
        appUsage = appUsage,
        stepCount = stepCount,
        wakeHour = prefsManager.getTestWakeHour(),
        wakeMinute = prefsManager.getTestWakeMinute()
    )
    
    when (prefsManager.getNotifyType()) {
        "phone" -> {
            val contact = prefsManager.getEmergencyContact()
            if (!contact.isNullOrEmpty()) {
                sendSmsAlert(contact, report)
            }
        }
        "email" -> {
            sendEmailReport(report)
        }
    }
    
    sendExceptionNotification(report)
    prefsManager.clearTestData()
    
    Log.i(tag, "测试模式检查完成")
}





private fun updateLastEmailRecordStatus(status: AlertStatus) {
    val history = prefsManager.getAlertHistory()
    val lastEmailRecord = history.firstOrNull { record -> 
        record.type == AlertType.CHECKIN && 
        record.method == AlertMethod.EMAIL && 
        record.status == AlertStatus.PENDING 
    }
    
    if (lastEmailRecord != null) {
        history.remove(lastEmailRecord)
        val updatedRecord = lastEmailRecord.copy(status = status)
        history.add(0, updatedRecord)
        
        // ✅ 保存更新后的历史记录
        prefsManager.saveAlertHistoryDirectly(history)
        Log.i(tag, "更新邮件警报状态为：${status}")
    } else {
        Log.w(tag, "未找到待处理的邮件警报记录")
    }
}

// ✅ 在 scheduleChecks() 方法末尾添加
// ✅ 修复 scheduleAlarm() 方法
private fun scheduleAlarm() {
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
    val intent = Intent(this, com.livewell.receiver.AlertReceiver::class.java).apply {
        action = "com.livewell.ALERT_CHECK"
    }
    
    val pendingIntent = android.app.PendingIntent.getBroadcast(
        this, 0, intent,
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )
    
    // 读取配置的报警时间
    val alertHour = prefsManager.getAlertCheckHour()
    val alertMinute = prefsManager.getAlertCheckMinute()
    
    val now = Calendar.getInstance()
    val triggerTime = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, alertHour)
        set(Calendar.MINUTE, alertMinute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        
        // 如果设置的时间已经过去，设置为明天
        if (before(now)) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }.timeInMillis
    
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            Log.i(tag, "Alarm 已设置：${alertHour}:${alertMinute}，触发时间：${android.icu.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(triggerTime)}")
        } else {
            alarmManager.setExact(
                android.app.AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            Log.i(tag, "Alarm 已设置（旧版本）：${alertHour}:${alertMinute}")
        }
    } catch (e: Exception) {
        Log.e(tag, "设置 Alarm 失败：${e.message}")
    }
}

private fun scheduleDailyStepReset() {
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
    val intent = Intent(this, com.livewell.receiver.DailyStepResetReceiver::class.java).apply {
        action = "com.livewell.ACTION_RESET_DAILY_STEPS"
    }
    
    val pendingIntent = android.app.PendingIntent.getBroadcast(
        this, 0, intent,
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )
    
    // 设置每天凌晨 0 点执行
    val calendar = Calendar.getInstance().apply {
        timeInMillis = System.currentTimeMillis()
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 1)
        set(Calendar.MILLISECOND, 0)
        
        if (timeInMillis <= System.currentTimeMillis()) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }
    
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                android.app.AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
        Log.i(tag, "已设置每日步数重置闹钟")
    } catch (e: Exception) {
        Log.e(tag, "设置步数重置闹钟失败：${e.message}")
    }
}


// ... existing code ...

    /**
     * 创建统一的前台服务通知（不显示独立通知）
     */
    private fun createUnifiedServiceNotification(): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, UnifiedNotificationService.CHANNEL_ID)
            .setContentTitle("安守")
            .setContentText("安全守护服务运行中")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
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
                    alertJson.put("alertType", "checkin")
                    alertJson.put("alertLevel", "danger")
                    alertJson.put("triggerTime", System.currentTimeMillis())
                    alertJson.put("resolved", false)
                    alertJson.put("reason", reason)
                    alertJson.put("content", prefsManager.getAlertMessage())
                    
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
                    deviceJson.put("appVersion", packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0")
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


}


