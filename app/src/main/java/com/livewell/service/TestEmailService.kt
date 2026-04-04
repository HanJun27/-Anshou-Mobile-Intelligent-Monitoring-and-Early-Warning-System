// file: e:\GongZuoTai\HuoZheNe\app\src\main\java\com\livewell\service\TestEmailService.kt
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
import com.livewell.untils.PrefsManager
import com.livewell.untils.SecurePrefsManager
import com.livewell.untils.UsageStatsHelper
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TestEmailService : Service() {

    private lateinit var prefsManager: PrefsManager
    private lateinit var securePrefs: SecurePrefsManager
    private lateinit var mailSender: MailSender
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val tag = "TestEmailService"

    companion object {
        private const val NOTIFICATION_ID = 5001
        private const val CHANNEL_ID = "test_email_channel"

        fun start(context: Context) {
            val intent = Intent(context, TestEmailService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TestEmailService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefsManager = PrefsManager(this)
        securePrefs = SecurePrefsManager(this)
        mailSender = MailSender()

        createNotificationChannel()
        
        UnifiedNotificationService.emailReceiverRunning = true
        UnifiedNotificationService.emailReceiverInfo = "测试邮件服务运行中"
        UnifiedNotificationService.updateNotification(this)
        
        startForeground(NOTIFICATION_ID, createNotification())

        scheduleTestEmail()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        executor.shutdown()
        UnifiedNotificationService.emailReceiverRunning = false
        UnifiedNotificationService.emailReceiverInfo = ""
        //UnifiedNotificationService.updateNotification(this)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "测试邮件服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于测试后台保活功能的定时邮件发送"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("安守 - 测试中")
            .setContentText("后台保活测试邮件服务运行中")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun scheduleTestEmail() {
        val interval = prefsManager.getTestEmailInterval().toLong()

        executor.scheduleAtFixedRate({
            if (prefsManager.isTestEmailEnabled()) {
                sendTestEmail()
            }
        }, 0, interval, TimeUnit.MINUTES)
    }

    private fun sendTestEmail() {
        val fromEmail = securePrefs.getEmailAccount()
        val authCode = securePrefs.getEmailAuthCode()
        val host = securePrefs.getSmtpHost() ?: prefsManager.getEmailSmtpHost()
        val port = securePrefs.getSmtpPort() ?: prefsManager.getEmailSmtpPort()
        val toEmail = prefsManager.getEmailTo()

        if (fromEmail.isNullOrEmpty() || authCode.isNullOrEmpty() || toEmail.isNullOrEmpty()) {
            Log.e(tag, "邮件配置不完整，跳过测试邮件发送")
            return
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val subject = "【安守】后台保活测试 - $timestamp"
        val content = buildTestEmailContent(timestamp)

        Log.i(tag, "发送测试邮件：$timestamp")

        mailSender.sendEmail(
            host = host,
            port = port,
            fromEmail = fromEmail,
            authCode = authCode,
            toEmail = toEmail,
            subject = subject,
            content = content,
            callback = object : MailSender.SendCallback {
                override fun onSuccess() {
                    Log.i(tag, "测试邮件发送成功")
                    prefsManager.setLastTestEmailTime(System.currentTimeMillis())
                    updateNotification("上次发送：$timestamp")
                }

                override fun onError(error: String) {
                    Log.e(tag, "测试邮件发送失败：$error")
                }
            },
            context = this
        )
    }

    private fun buildTestEmailContent(timestamp: String): String {
        val uptime = System.currentTimeMillis() - prefsManager.getLastAliveTime()
        val uptimeMinutes = uptime / 1000 / 60
        
        // ✅ 获取今日步数和使用时长
        val stepCount = getTodayStepCount()
        val appUsage = getAppUsageMinutes()
        
        val usageThreshold = prefsManager.getAppUsageThreshold()
        val stepThreshold = prefsManager.getStepThreshold()

        return """
            【安守】后台保活测试邮件
            
            发送时间：$timestamp
            设备型号：${Build.MODEL}
            Android 版本：${Build.VERSION.RELEASE}
            应用版本：1.0.0
            
            服务状态：
            • 服务运行正常 ✅
            • 距离上次存活检查：${uptimeMinutes}分钟
            • 测试间隔：${prefsManager.getTestEmailInterval()}分钟
            
            健康数据：
            📱 今日使用：${appUsage}分钟 ${if (appUsage < usageThreshold) "(低于阈值${usageThreshold}分钟) ⚠️" else "(正常) ✓"}
            👣 今日步数：${stepCount}步 ${if (stepCount < stepThreshold) "(低于阈值${stepThreshold}步) ⚠️" else "(正常) ✓"}
            
            此邮件用于验证后台服务是否正常运行，如收到此邮件说明保活功能正常工作。
            
            ---
            安守 App - 安全守护
        """.trimIndent()
    }

    private fun updateNotification(lastSendTime: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("安守 - 测试中")
            .setContentText(lastSendTime)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
    
    // ✅ 获取今日步数
    private fun getTodayStepCount(): Int {
        try {
            // 从 PrefsManager 获取最新保存的步数
            return prefsManager.getSavedStepCount()
        } catch (e: Exception) {
            Log.e(tag, "获取步数失败", e)
            return 0
        }
    }
    
    // ✅ 获取今日使用时长（分钟）
    private fun getAppUsageMinutes(): Long {
        try {
            val usageStatsHelper = UsageStatsHelper(this)
            return usageStatsHelper.getTodayAppUsageMinutes()
        } catch (e: Exception) {
            Log.e(tag, "获取使用时长失败", e)
            return 0L
        }
    }
}