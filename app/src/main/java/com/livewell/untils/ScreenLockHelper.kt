package com.livewell.untils  

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager
import android.util.Log

class ScreenLockHelper(private val context: Context) {

    private val tag = "ScreenLockHelper"
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val WAKE_LOCK_TIMEOUT = 30 * 60 * 1000L // 30 分钟
    }

    /**
     * 获取唤醒锁，防止 CPU 休眠
     */
    fun acquireCpuWakeLock() {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "livewell:CpuWakeLock"
            )
            wakeLock?.apply {
                setReferenceCounted(false)
                if (!isHeld) {
                    acquire(WAKE_LOCK_TIMEOUT)
                    Log.i(tag, "CPU 唤醒锁已获取")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "获取 CPU 唤醒锁失败：${e.message}")
        }
    }

    /**
     * 释放唤醒锁
     */
    fun releaseCpuWakeLock() {
        try {
            wakeLock?.apply {
                if (isHeld) {
                    release()
                    Log.i(tag, "CPU 唤醒锁已释放")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(tag, "释放 CPU 唤醒锁失败：${e.message}")
        }
    }

    /**
     * 获取屏幕唤醒锁（保持屏幕常亮）
     */
    fun acquireScreenWakeLock() {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val screenWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "livewell:ScreenWakeLock"
            )
            screenWakeLock.apply {
                setReferenceCounted(false)
                if (!isHeld) {
                    acquire(5 * 60 * 1000L) // 屏幕锁最多 5 分钟
                    Log.i(tag, "屏幕唤醒锁已获取")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "获取屏幕唤醒锁失败：${e.message}")
        }
    }

    /**
     * 检查设备是否处于锁屏状态
     */
    fun isDeviceLocked(): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return keyguardManager.isDeviceLocked
    }

    /**
     * 在锁屏时唤醒设备并显示界面
     */
    fun wakeUpAndShowActivity(activityClass: Class<*>) {
        try {
            acquireScreenWakeLock()

            val intent = Intent(context, activityClass).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("wake_from_lock", true)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                intent.putExtra("android.intent.extra.SHOW_WHEN_LOCKED", true)
                intent.putExtra("android.intent.extra.TURN_SCREEN_ON", true)
            }

            context.startActivity(intent)
            Log.i(tag, "已唤醒设备并启动活动")
        } catch (e: Exception) {
            Log.e(tag, "唤醒设备失败：${e.message}")
        }
    }

    /**
     * 为 Activity 设置锁屏显示标志（修复 API 兼容性）
     */
    fun setShowWhenLocked(window: android.view.Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                val setShowWhenLockedMethod = window.javaClass.getMethod(
                    "setShowWhenLocked", 
                    Boolean::class.javaPrimitiveType
                )
                val setTurnScreenOnMethod = window.javaClass.getMethod(
                    "setTurnScreenOn", 
                    Boolean::class.javaPrimitiveType
                )
                setShowWhenLockedMethod.invoke(window, true)
                setTurnScreenOnMethod.invoke(window, true)
            } catch (e: Exception) {
                Log.w(tag, "反射调用失败：${e.message}")
                useLegacyWindowFlags(window)
            }
        } else {
            useLegacyWindowFlags(window)
        }
    }

    /**
     * 旧版本的窗口标志设置方法（✅ 新增）
     */
    @Suppress("DEPRECATION")
    private fun useLegacyWindowFlags(window: android.view.Window) {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }
}