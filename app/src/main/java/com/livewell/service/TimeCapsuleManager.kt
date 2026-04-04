package com.livewell.service

import android.content.Context
import android.util.Log
import com.livewell.untils.PrefsManager
import com.livewell.untils.UsageStatsHelper
import java.text.SimpleDateFormat
import java.util.*

/**
 * 时光胶囊管理器
 * 负责监测用户活动状态，在达到阈值时触发紧急邮件发送
 */
class TimeCapsuleManager(private val context: Context) {
    
    private val prefsManager = PrefsManager(context)
    private val usageStatsHelper = UsageStatsHelper(context)
    private val TAG = "TimeCapsuleManager"
    
    // 上次检测到活动的日期
    private var lastActiveDate: String? = null
    
    companion object {
        private const val KEY_LAST_ACTIVE_DATE = "time_capsule_last_active_date"
        private const val KEY_LAST_CHECK_TIME = "time_capsule_last_check_time"
    }
    
    /**
     * 检查用户活动状态
     * 如果超过设定天数无活动（步数和使用时长都为 0），则触发紧急邮件
     */
    fun checkUserActivity() {
        if (!prefsManager.isTimeCapsuleEnabled() || !prefsManager.isEmergencyEmailEnabled()) {
            Log.d(TAG, "时光胶囊或紧急邮件功能未启用")
            return
        }
        
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val thresholdDays = prefsManager.getInactiveThresholdDays()
        
        Log.d(TAG, "检查用户活动状态，阈值：$thresholdDays 天")
        
        // 获取今天的步数和使用时长
        val todaySteps = prefsManager.getSavedStepCount()
        val todayUsageMinutes = usageStatsHelper.getTodayAppUsageMinutes()
        
        Log.d(TAG, "今日步数：$todaySteps, 今日使用时长：$todayUsageMinutes 分钟")
        
        // 检查是否都为 0
        if (todaySteps == 0 && todayUsageMinutes == 0L) {
            Log.w(TAG, "今日步数和使用时长都为 0")
            
            // 获取上次活动日期
            val lastActive = getLastActiveDate()
            
            if (lastActive != null) {
                val daysSinceActive = calculateDaysBetween(lastActive, today)
                Log.d(TAG, "距离上次活动已过去 $daysSinceActive 天")
                
                if (daysSinceActive >= thresholdDays) {
                    Log.e(TAG, "已达到阈值天数，触发紧急邮件发送！")
                    sendEmergencyEmail()
                }
            } else {
                // 第一次检测，记录当前日期
                setLastActiveDate(today)
                Log.d(TAG, "首次检测，记录活动日期：$today")
            }
        } else {
            // 有活动，更新最后活动日期
            setLastActiveDate(today)
            Log.d(TAG, "检测到用户活动，更新最后活动日期：$today")
        }
        
        // 保存最后检查时间
        prefsManager.saveLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
    }
    
    /**
     * 发送紧急邮件
     */
    private fun sendEmergencyEmail() {
        try {
            val emergencyEmailText = prefsManager.getEmergencyEmailText()
            val emailSubject = "【时光胶囊】来自用户的紧急消息"
            
            // 获取收件人邮箱
            val toEmail = prefsManager.getEmergencyEmail()
            if (toEmail.isNullOrEmpty()) {
                Log.e(TAG, "未设置收件人邮箱，无法发送")
                return
            }
            
            // 获取发件人配置
            val securePrefs = com.livewell.untils.SecurePrefsManager(context)
            val fromEmail = securePrefs.getEmailAccount()
            val authCode = securePrefs.getEmailAuthCode()
            val smtpHost = securePrefs.getSmtpHost() ?: prefsManager.getEmailSmtpHost()
            val smtpPort = securePrefs.getSmtpPort() ?: prefsManager.getEmailSmtpPort()
            
            if (fromEmail.isNullOrEmpty() || authCode.isNullOrEmpty()) {
                Log.e(TAG, "发件人配置不完整，无法发送")
                return
            }
            
            Log.i(TAG, "准备发送紧急邮件")
            Log.i(TAG, "发件人：$fromEmail")
            Log.i(TAG, "收件人：$toEmail")
            Log.i(TAG, "邮件主题：$emailSubject")
            Log.i(TAG, "邮件内容：$emergencyEmailText")
            
            // 使用 MailSender 发送邮件
            val mailSender = MailSender()
            mailSender.sendEmail(
                host = smtpHost,
                port = smtpPort,
                fromEmail = fromEmail,
                authCode = authCode,
                toEmail = toEmail,
                subject = emailSubject,
                content = emergencyEmailText,
                callback = object : MailSender.SendCallback {
                    override fun onSuccess() {
                        Log.i(TAG, "紧急邮件发送成功")
                        // 标记为已发送，避免重复发送
                        prefsManager.saveLong("last_emergency_email_time", System.currentTimeMillis())
                    }
                    
                    override fun onError(error: String) {
                        Log.e(TAG, "紧急邮件发送失败：$error")
                    }
                },
                context = context
            )
        } catch (e: Exception) {
            Log.e(TAG, "发送紧急邮件异常", e)
        }
    }
    
    /**
     * 获取最后活动日期
     */
    private fun getLastActiveDate(): String? {
        return prefsManager.getString(KEY_LAST_ACTIVE_DATE, null)
    }
    
    /**
     * 设置最后活动日期
     */
    private fun setLastActiveDate(date: String) {
        prefsManager.saveString(KEY_LAST_ACTIVE_DATE, date)
    }
    
    /**
     * 计算两个日期之间的天数
     */
    private fun calculateDaysBetween(startDate: String, endDate: String): Int {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val start = format.parse(startDate)
            val end = format.parse(endDate)
            
            if (start != null && end != null) {
                val diffInMillis = end.time - start.time
                val diffInDays = diffInMillis / (1000 * 60 * 60 * 24)
                diffInDays.toInt()
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "日期计算失败", e)
            0
        }
    }
    
    /**
     * 重置活动检测（当检测到用户活动时调用）
     */
    fun resetActivityDetection() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        setLastActiveDate(today)
        Log.d(TAG, "重置活动检测，最后活动日期：$today")
    }
}
