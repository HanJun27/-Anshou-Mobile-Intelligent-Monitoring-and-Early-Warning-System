package com.livewell.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.livewell.service.CheckinService

class AlertReceiver : BroadcastReceiver() {
    
    private val tag = "AlertReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(tag, "====== Alarm 触发 ======")
        
        try {
            // 尝试启动检查服务
            val serviceIntent = Intent(context, CheckinService::class.java)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            Log.i(tag, "CheckinService 已启动")
        } catch (e: Exception) {
            Log.e(tag, "启动服务失败：${e.message}")
        }
    }
}