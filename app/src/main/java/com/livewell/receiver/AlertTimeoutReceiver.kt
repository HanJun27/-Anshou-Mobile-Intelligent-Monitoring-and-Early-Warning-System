package com.livewell.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.livewell.service.CheckinService
import com.livewell.untils.PrefsManager

/**
 * 报警确认超时接收器
 * 当用户未在指定时间内确认报警时，触发此接收器并发送真正的警报
 */
class AlertTimeoutReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "AlertTimeoutReceiver"
        const val ACTION_ALERT_TIMEOUT = "com.livewell.ACTION_ALERT_TIMEOUT"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "====== 报警确认超时，触发真正警报 ======")
        
        try {
            val prefsManager = PrefsManager(context)
            
            // 检查用户是否已确认或请求稍后提醒
            if (prefsManager.isUserConfirmed()) {
                Log.i(TAG, "用户已确认，取消超时警报")
                return
            }
            
            if (prefsManager.isSnoozeRequested()) {
                Log.i(TAG, "用户请求稍后提醒，取消超时警报")
                return
            }
            
            Log.i(TAG, "用户未确认，开始触发警报...")
            
            // 启动 CheckinService 并触发警报
            val serviceIntent = Intent(context, CheckinService::class.java).apply {
                action = "TRIGGER_ALERT"
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            Log.i(TAG, "✅ 已启动 CheckinService 触发警报")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 处理超时失败：${e.message}", e)
        }
    }
}
