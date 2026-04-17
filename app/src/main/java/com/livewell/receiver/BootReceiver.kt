package com.livewell.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.livewell.service.CheckinService
import com.livewell.service.KeepAliveChecker
import com.livewell.service.KeepAliveJobService
import com.livewell.service.EmailReceiverService
import com.livewell.untils.PrefsManager

class BootReceiver : BroadcastReceiver() {
    
    private val tag = "BootReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        // ✅ 只在开机完成时启动服务，避免其他广播触发
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Log.i(tag, "====== 开机启动 ======")
            
            try {
                val prefsManager = PrefsManager(context)
                val mode = prefsManager.getAppMode()
                
                // 启动保活服务
                KeepAliveChecker.start(context)
                KeepAliveJobService.scheduleJob(context)
                
                // 根据模式启动相应服务
                when (mode) {
                    PrefsManager.MODE_GUARDIAN -> {
                        CheckinService.start(context)
                    }
                    PrefsManager.MODE_RECEIVER -> {
                        val emailIntent = Intent(context, EmailReceiverService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(emailIntent)
                        } else {
                            context.startService(emailIntent)
                        }
                    }
                    PrefsManager.MODE_MIXED -> {
                        CheckinService.start(context)
                        val emailIntent = Intent(context, EmailReceiverService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(emailIntent)
                        } else {
                            context.startService(emailIntent)
                        }
                    }
                }
                
                // 设置 Alarm
                scheduleAlertAlarm(context, prefsManager)
                
                Log.i(tag, "开机启动完成")
             } catch (e: Exception) {
                Log.e(tag, "开机启动失败：${e.message}")
            }
            // ✅ 重置步数传感器的基准值
            val stepManager = com.livewell.untils.SystemStepManager.getInstance(context) // ✅ 修改为单例方法
            stepManager.resetDailySteps()
            Log.i(tag, "开机完成，已重置步数传感器")
        } else {
            Log.w(tag, "收到非开机广播：${intent.action}，忽略")
        }
    }
    
    private fun scheduleAlertAlarm(context: Context, prefsManager: PrefsManager) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, AlertReceiver::class.java).apply {
            action = "com.livewell.ALERT_CHECK"
        }
        
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        val triggerTime = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, prefsManager.getAlertCheckHour())
            set(java.util.Calendar.MINUTE, prefsManager.getAlertCheckMinute())
            set(java.util.Calendar.SECOND, 0)
        }.timeInMillis
        
        // 如果时间已过，设置为明天
        val finalTriggerTime = if (triggerTime <= System.currentTimeMillis()) {
            triggerTime + 24 * 60 * 60 * 1000
        } else {
            triggerTime
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    finalTriggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    android.app.AlarmManager.RTC_WAKEUP,
                    finalTriggerTime,
                    pendingIntent
                )
            }
            Log.i("BootReceiver", "Alarm 已设置：${finalTriggerTime}")
        } catch (e: Exception) {
            Log.e("BootReceiver", "设置 Alarm 失败：${e.message}")
        }
    }
}