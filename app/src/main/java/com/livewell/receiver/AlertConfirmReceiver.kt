package com.livewell.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.livewell.MainActivity
import com.livewell.R  // ✅ 添加 R 引用
import com.livewell.untils.PrefsManager

class AlertConfirmReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "AlertConfirmReceiver"
        const val ACTION_CONFIRM = "CONFIRM_ALERT"
        const val ACTION_SNOOZE = "SNOOZE_ALERT"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CONFIRM -> handleConfirm(context)
            ACTION_SNOOZE -> handleSnooze(context)
            else -> Log.e(TAG, "Unknown action: ${intent.action}")
        }
    }
    
    private fun handleConfirm(context: Context) {
        Log.i(TAG, "用户确认安全")
        
        // ✅ 取消超时闹钟
        cancelTimeoutAlarm(context)
        
        // 1. 取消确认通知
        androidx.core.app.NotificationManagerCompat.from(context).cancel(1003)
        
        // 2. 记录用户已确认状态
        val prefsManager = PrefsManager(context)
        prefsManager.setUserConfirmed(true)
        prefsManager.setLastConfirmTime(System.currentTimeMillis())
        
        // 3. 可以发送一个确认已收到的通知
        sendConfirmationNotification(context)
    }
    
    private fun handleSnooze(context: Context) {
        Log.i(TAG, "用户选择稍后提醒")
        
        // ✅ 取消超时闹钟
        cancelTimeoutAlarm(context)
        
        // 1. 取消当前通知
        androidx.core.app.NotificationManagerCompat.from(context).cancel(1003)
        
        // 2. 设置30分钟后再次提醒
        val prefsManager = PrefsManager(context)
        val snoozeTime = System.currentTimeMillis() + 30 * 60 * 1000
        prefsManager.setSnoozeRequested(true)
        prefsManager.setSnoozeTime(snoozeTime)
        
        // ✅ 3. 设置30分钟后的闹钟，重新触发检查
        scheduleSnoozeAlarm(context, snoozeTime)
        
        // 4. 发送稍后提醒通知
        sendSnoozeNotification(context)
    }
    
    /**
     * ✅ 取消超时闹钟
     */
    private fun cancelTimeoutAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(context, AlertTimeoutReceiver::class.java).apply {
                action = "com.livewell.ACTION_ALERT_TIMEOUT"
            }
            
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context, 3002, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.cancel(pendingIntent)
            Log.i(TAG, "✅ 已取消超时闹钟")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 取消超时闹钟失败：${e.message}")
        }
    }
    
    /**
     * ✅ 设置稍后提醒的闹钟（30分钟后重新触发检查）
     */
    private fun scheduleSnoozeAlarm(context: Context, snoozeTime: Long) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            
            // ✅ 创建一个 Intent，用于30分钟后重新启动 CheckinService
            val intent = Intent(context, com.livewell.service.CheckinService::class.java).apply {
                action = "SNOOZE_RECHECK"
            }
            
            val pendingIntent = android.app.PendingIntent.getService(
                context, 3003, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    snoozeTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    android.app.AlarmManager.RTC_WAKEUP,
                    snoozeTime,
                    pendingIntent
                )
            }
            
            Log.i(TAG, "✅ 已设置稍后提醒闹钟：${android.icu.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(snoozeTime)}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 设置稍后提醒闹钟失败：${e.message}")
        }
    }
    
    private fun sendConfirmationNotification(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = androidx.core.app.NotificationCompat.Builder(context, "checkin_service_channel")
            .setContentTitle("已收到您的确认")
            .setContentText("感谢您的确认，我们将继续守护您的安全")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        val manager = NotificationManagerCompat.from(context)
        manager.notify(1004, notification)
    }
    
    private fun sendSnoozeNotification(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = androidx.core.app.NotificationCompat.Builder(context, "checkin_service_channel")
            .setContentTitle("提醒已推迟")
            .setContentText("我们将在30分钟后再次提醒您")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        val manager = NotificationManagerCompat.from(context)
        manager.notify(1005, notification)
    }
}