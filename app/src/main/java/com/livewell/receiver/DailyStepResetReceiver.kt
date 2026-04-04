package com.livewell.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.livewell.untils.PrefsManager
import com.livewell.untils.SystemStepManager

class DailyStepResetReceiver : BroadcastReceiver() {
    
    private val tag = "DailyStepResetReceiver"
    
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        
        Log.i(tag, "接收到每日重置广播")
        
        val prefsManager = PrefsManager(context)
        val stepManager = SystemStepManager.getInstance(context)
        
        stepManager.resetDailySteps()
        
        prefsManager.saveStepCount(0)
        prefsManager.saveStepSaveDate(stepManager.getCurrentDateKeyForPrefs())
        
        Log.i(tag, "已重置今日步数为 0，并保存到本地")
    }
}