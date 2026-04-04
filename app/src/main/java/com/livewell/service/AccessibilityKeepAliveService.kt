package com.livewell.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.livewell.MainActivity
import com.livewell.untils.PrefsManager

/**
 * 辅助功能保活服务
 * 
 * 工作原理：
 * 1. 注册为系统辅助功能服务，获得最高优先级
 * 2. 空闲监听模式，不读取屏幕内容，保护用户隐私
 * 3. 检测主进程是否存活，必要时重启应用
 * 4. 系统会自动重启此服务，形成不死循环
 * 
 * 隐私说明：
 * - 本服务仅用于保活，不会收集任何屏幕内容
 * - 不会记录用户操作
 * - 不会发送任何数据到服务器
 */
class AccessibilityKeepAliveService : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibilityKeepAlive"
        
        // 服务是否运行
        var isRunning: Boolean = false
            private set
        
        // 单例引用
        var instance: AccessibilityKeepAliveService? = null
            private set
        
        /**
         * 检查辅助功能是否开启
         */
        fun isEnabled(context: android.content.Context): Boolean {
            return try {
                val serviceName = context.packageName + "/" + AccessibilityKeepAliveService::class.java.canonicalName
                val enabledServices = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
                
                // 简单判断：如果字符串包含服务名，说明已开启
                enabledServices.contains(serviceName) || enabledServices.contains(context.packageName)
            } catch (e: Exception) {
                Log.e(TAG, "检查辅助功能状态失败：${e.message}")
                false
            }
        }
    }

    private lateinit var prefsManager: PrefsManager
    private var lastCheckTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        prefsManager = PrefsManager(this)
        
        Log.d(TAG, "辅助功能服务已连接")
        
        // 配置服务类型（仅保活，不需要读取屏幕）
        val info = AccessibilityServiceInfo().apply {
            // 只接收最基本的事件，减少资源消耗
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT
            notificationTimeout = 1000
        }
        serviceInfo = info
        
        // 记录开启时间
        prefsManager.setAccessibilityServiceEnabled(true)
        
        // 启动保活检查
        startKeepAliveCheck()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 仅用于保持服务活跃，不处理任何事件
        // 这样可以最大程度保护用户隐私
    }

    override fun onInterrupt() {
        Log.w(TAG, "辅助功能服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isRunning = false
        Log.w(TAG, "辅助功能服务已销毁")
    }

    /**
     * 启动保活检查
     * 定期检查主进程和其他服务是否运行
     */
    private fun startKeepAliveCheck() {
        Thread {
            while (isRunning) {
                try {
                    Thread.sleep(30000) // 每 30 秒检查一次
                    
                    // 检查主进程是否需要重启
                    checkAndRestartApp()
                    
                } catch (e: InterruptedException) {
                    Log.e(TAG, "保活检查线程被中断")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "保活检查异常：${e.message}")
                }
            }
        }.start()
        
        Log.d(TAG, "保活检查线程已启动")
    }

    /**
     * 检查并重启应用
     */
    private fun checkAndRestartApp() {
        try {
            // 检查 SilentMusicService 是否运行
            if (!SilentMusicService.isRunning) {
                Log.w(TAG, "检测到无声音乐服务未运行，尝试重启...")
                
                // 重启 SilentMusicService
                val intent = Intent(this, SilentMusicService::class.java).apply {
                    action = SilentMusicService.ACTION_START
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                
                Log.d(TAG, "已发送无声音乐服务重启指令")
            }
            
            // 检查统一通知服务
            if (!UnifiedNotificationService.checkinServiceRunning &&
                !UnifiedNotificationService.keepAliveCheckerRunning) {
                
                Log.w(TAG, "检测到统一通知服务未运行，尝试重启...")
                
                val intent = Intent(this, UnifiedNotificationService::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                
                Log.d(TAG, "已发送统一通知服务重启指令")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "重启服务失败：${e.message}")
        }
    }

    /**
     * 打开辅助功能设置页面
     */
    fun openAccessibilitySettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Log.d(TAG, "已打开辅助功能设置页面")
        } catch (e: Exception) {
            Log.e(TAG, "打开辅助功能设置失败：${e.message}")
        }
    }
}
