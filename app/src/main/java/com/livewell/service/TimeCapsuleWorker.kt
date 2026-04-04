package com.livewell.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 时光胶囊活动检测 Worker
 * 定期检查用户活动状态，如果超过阈值则发送紧急邮件
 */
class TimeCapsuleWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val TAG = "TimeCapsuleWorker"
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始检查用户活动状态")
            
            val timeCapsuleManager = TimeCapsuleManager(applicationContext)
            timeCapsuleManager.checkUserActivity()
            
            Log.d(TAG, "用户活动检查完成")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "活动检查失败", e)
            Result.retry()
        }
    }
}
