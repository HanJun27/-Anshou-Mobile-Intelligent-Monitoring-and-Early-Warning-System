package com.livewell.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.livewell.MainActivity
import com.livewell.R
import com.livewell.untils.PrefsManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class KeepAliveChecker : Service() {
    
    private lateinit var prefsManager: PrefsManager
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val tag = "KeepAliveChecker"
    
    companion object {
        private const val NOTIFICATION_ID = 4001
        private const val CHANNEL_ID = "keep_alive_alerts_channel"
        private const val CHECK_INTERVAL = 30L
        
        fun start(context: Context) {
            val intent = Intent(context, KeepAliveChecker::class.java)
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
        
        createNotificationChannel()
        
        UnifiedNotificationService.keepAliveCheckerRunning = true
        UnifiedNotificationService.keepAliveInfo = "保活自检服务运行中"
        
        // ✅ 使用统一通知渠道作为前台服务通知，避免显示多个独立通知
        startForeground(UnifiedNotificationService.NOTIFICATION_ID, createUnifiedServiceNotification())
        
        startSelfCheck()
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
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, UnifiedNotificationService.CHANNEL_ID)
            .setContentTitle("安守")
            .setContentText("保活自检服务运行中")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }
   


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        executor.shutdown()
        UnifiedNotificationService.keepAliveCheckerRunning = false
        UnifiedNotificationService.keepAliveInfo = ""
        //UnifiedNotificationService.updateNotification(this)
        super.onDestroy()
    }
    

    
    private fun startSelfCheck() {
        executor.scheduleAtFixedRate({
            performSelfCheck()
        }, 0, CHECK_INTERVAL, TimeUnit.MINUTES)
    }
    
    private fun performSelfCheck() {
        val lastAliveTime = prefsManager.getLastAliveTime()
        val currentTime = System.currentTimeMillis()
        
        // 如果超过2小时没有更新存活时间，说明可能被杀过
        if (lastAliveTime > 0 && currentTime - lastAliveTime > 2 * 60 * 60 * 1000) {
            Log.w(tag, "检测到应用可能被系统杀死，已重启")
            sendRestartNotification()
        }
        
        // 更新存活时间
        prefsManager.updateLastAliveTime()
    }
    
    private fun sendRestartNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔄 应用已重启")
            .setContentText("系统已重启「安守」服务，请检查设置是否正确")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(4002, notification)
    }
}