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


class BackgroundTimerService : Service() {
    
    private lateinit var prefsManager: PrefsManager
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val tag = "BackgroundTimerService"
    private var isTimerStarted = false
    
    companion object {
        private const val NOTIFICATION_ID = 5001
        private const val CHANNEL_ID = "background_timer_channel"
        
        fun start(context: Context) {
            val intent = Intent(context, BackgroundTimerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, BackgroundTimerService::class.java)
            context.stopService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        prefsManager = PrefsManager(this)
        
        createNotificationChannel()
        
        UnifiedNotificationService.backgroundTimerRunning = true
        
        // ✅ 使用统一通知渠道作为前台服务通知，避免显示多个独立通知
        startForeground(UnifiedNotificationService.NOTIFICATION_ID, createUnifiedServiceNotification())
        
        startBackgroundTimer()
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
            .setContentText("后台计时服务运行中")
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
        stopBackgroundTimer()
        executor.shutdown()
        UnifiedNotificationService.backgroundTimerRunning = false
        UnifiedNotificationService.backgroundTimerInfo = ""
        //UnifiedNotificationService.updateNotification(this)
        super.onDestroy()
    }
    
    
    
    private fun startBackgroundTimer() {
        if (!prefsManager.isTimerRunning()) {
            prefsManager.setBackgroundStartTime(System.currentTimeMillis())
            prefsManager.setTimerRunning(true)
            Log.i(tag, "后台计时器启动")
        }
        
        executor.scheduleAtFixedRate({
            updateTotalTime()
            
            val currentFormattedTime = getFormattedTime()
            if (currentFormattedTime != lastUpdateTime) {
                updateNotificationInfo(currentFormattedTime)
                lastUpdateTime = currentFormattedTime
            }
        }, 1, 1, TimeUnit.SECONDS)
    }
    
 private var lastUpdateTime: String = ""
    
    private fun updateNotificationInfo(timeStr: String) {
        UnifiedNotificationService.backgroundTimerInfo = "已运行：$timeStr"
        UnifiedNotificationService.updateNotification(this)
    }

    private fun stopBackgroundTimer() {
        updateTotalTime()
        prefsManager.setTimerRunning(false)
        Log.i(tag, "后台计时器停止")
    }
    
    
    private fun updateTotalTime() {
        val startTime = prefsManager.getBackgroundStartTime()
        if (startTime > 0 && prefsManager.isTimerRunning()) {
            val currentTime = System.currentTimeMillis()
            val elapsedSeconds = (currentTime - startTime) / 1000
            prefsManager.setBackgroundTotalTime(elapsedSeconds)
        }
    }
    
    fun getFormattedTime(): String {
        val totalSeconds = prefsManager.getBackgroundTotalTime()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}