package com.livewell.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.livewell.MainActivity
import com.livewell.R
import java.text.SimpleDateFormat
import java.util.*

class UnifiedNotificationService : Service() {
    
    companion object {
        const val NOTIFICATION_ID = 1000
        const val CHANNEL_ID = "unified_service_channel"
        
        var checkinServiceRunning = false
        var backgroundTimerRunning = false
        var sleepMonitorRunning = false
        var keepAliveCheckerRunning = false
        var emailReceiverRunning = false
        
        var checkinInfo = "安全守护运行中"
        var backgroundTimerInfo = ""
        var sleepMonitorInfo = "睡眠监测服务运行中"
        var keepAliveInfo = "保活自检服务运行中"
        var emailReceiverInfo = "邮件接收服务运行中"
        
        fun start(context: Context) {
            val intent = Intent(context, UnifiedNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, UnifiedNotificationService::class.java)
            context.stopService(intent)
        }
        
        fun updateNotification(context: Context) {
            val intent = Intent(context, UnifiedNotificationService::class.java)
            intent.action = "UPDATE_NOTIFICATION"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createUnifiedNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "UPDATE_NOTIFICATION") {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, createUnifiedNotification())
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "安守 - 统一通知",
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
    
    private fun createUnifiedNotification(): Notification {
        val runningServices = mutableListOf<String>()
        if (checkinServiceRunning) runningServices.add("✓ 安全签到")
        if (backgroundTimerRunning) runningServices.add("✓ 后台计时")
        if (sleepMonitorRunning) runningServices.add("✓ 睡眠监测")
        if (keepAliveCheckerRunning) runningServices.add("✓ 保活自检")
        if (emailReceiverRunning) runningServices.add("✓ 邮件接收")
        
        val summaryText = if (runningServices.isEmpty()) {
            "暂无服务运行"
        } else {
            "正在运行 ${runningServices.size} 项服务"
        }
        
        val bigTextStyle = NotificationCompat.BigTextStyle()
            .bigText(buildDetailedInfo())
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("安守")
            .setContentText(summaryText)
            .setStyle(bigTextStyle)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .build()
    }
    
    private fun buildDetailedInfo(): String {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val currentTime = dateFormat.format(Date())
        
        sb.append("【服务状态】更新时间：$currentTime\n\n")
        
        var hasServices = false
        
        if (checkinServiceRunning) {
            sb.append("⏰ 安全签到服务\n")
            sb.append("   $checkinInfo\n\n")
            hasServices = true
        }
        
        if (backgroundTimerRunning) {
            sb.append("⏱️ 后台存活计时器\n")
            sb.append("   $backgroundTimerInfo\n\n")
            hasServices = true
        }
        
        if (sleepMonitorRunning) {
            sb.append("😴 睡眠监测服务\n")
            sb.append("   $sleepMonitorInfo\n\n")
            hasServices = true
        }
        
        if (keepAliveCheckerRunning) {
            sb.append("🔍 保活自检服务\n")
            sb.append("   $keepAliveInfo\n\n")
            hasServices = true
        }
        
        if (emailReceiverRunning) {
            sb.append("📧 邮件接收服务\n")
            sb.append("   $emailReceiverInfo\n\n")
            hasServices = true
        }
        
        if (!hasServices) {
            sb.append("暂无服务运行\n\n")
        }
        
        sb.append("\n──────────────\n")
        sb.append("点击通知打开应用")
        
        return sb.toString()
    }
}