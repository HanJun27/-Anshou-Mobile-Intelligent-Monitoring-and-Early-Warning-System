package com.livewell.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.livewell.untils.PrefsManager

class KeepAliveJobService : JobService() {

    private val tag = "KeepAliveJobService"
    private lateinit var prefsManager: PrefsManager
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val JOB_ID = 10001
        private const val JOB_INTERVAL_MS = 15 * 60 * 1000L // 15 分钟

        fun scheduleJob(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                
                val jobInfo = JobInfo.Builder(JOB_ID, ComponentName(context, KeepAliveJobService::class.java))
                    .setPeriodic(JOB_INTERVAL_MS)
                    .setPersisted(true) // 设备重启后保留
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setRequiresBatteryNotLow(false)
                    .setRequiresCharging(false)
                    .build()

                val result = jobScheduler.schedule(jobInfo)
                Log.i("JobSchedule", "Job 调度结果：$result")
            }
        }

        fun cancelJob(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                jobScheduler.cancel(JOB_ID)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefsManager = PrefsManager(this)
        Log.i(tag, "JobService 创建")
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.i(tag, "Job 开始执行")
        
        // 获取 WakeLock 保持 CPU 唤醒
        acquireWakeLock()
        
        // 在后台线程执行任务
        Thread {
            try {
                // 更新存活时间
                prefsManager.updateLastAliveTime()
                
                // 检查服务状态，必要时重启
                checkAndRestartServices()
                
                // 记录日志
                Log.i(tag, "Job 执行完成")
            } catch (e: Exception) {
                Log.e(tag, "Job 执行错误：${e.message}")
            } finally {
                // 释放 WakeLock
                releaseWakeLock()
                
                // 通知 Job 完成
                jobFinished(params, false)
            }
        }.start()
        
        // 返回 true 表示有后台任务在执行
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.w(tag, "Job 被系统停止")
        releaseWakeLock()
        // 返回 true 表示需要重新调度
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        Log.i(tag, "JobService 销毁")
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "livewell:KeepAliveJobWakeLock"
            )
            wakeLock?.apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 最多持有 10 分钟
            }
            Log.i(tag, "WakeLock 已获取")
        } catch (e: Exception) {
            Log.e(tag, "获取 WakeLock 失败：${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.apply {
                if (isHeld) {
                    release()
                    Log.i(tag, "WakeLock 已释放")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(tag, "释放 WakeLock 失败：${e.message}")
        }
    }

    private fun checkAndRestartServices() {
        // 检查 KeepAliveChecker 是否运行
        val lastAliveTime = prefsManager.getLastAliveTime()
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastAliveTime > 30 * 60 * 1000) {
            Log.w(tag, "检测到服务可能已停止，尝试重启")
            
            // 重启 KeepAliveChecker
            val intent = Intent(this, KeepAliveChecker::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }
}