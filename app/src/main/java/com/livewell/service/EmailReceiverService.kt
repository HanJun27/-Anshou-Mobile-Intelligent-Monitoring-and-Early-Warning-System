package com.livewell.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.livewell.MainActivity
import com.livewell.R
import com.livewell.untils.PrefsManager
import com.livewell.untils.SecurePrefsManager
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import javax.mail.*
import javax.mail.internet.InternetAddress
import com.sun.mail.imap.IMAPStore  // 新增：用于发送 IMAP ID


class EmailReceiverService : Service() {
    
    private lateinit var prefsManager: PrefsManager
    private lateinit var securePrefs: SecurePrefsManager
    private val executor = Executors.newScheduledThreadPool(2)
    private val tag = "EmailReceiverService"
    
    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "email_receiver_channel"
        private const val ALERT_CHANNEL_ID = "email_alert_channel"
        private const val CHECK_INTERVAL_MINUTES: Long = 15L
        private const val ACTION_FORCE_CHECK = "com.livewell.FORCE_EMAIL_CHECK"  // ✅ 新增：强制检查动作
        
        fun start(context: Context) {
            val intent = Intent(context, EmailReceiverService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        // ✅ 新增：强制立即检查邮件
        fun forceCheck(context: Context) {
            val intent = Intent(context, EmailReceiverService::class.java).apply {
                action = ACTION_FORCE_CHECK
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        prefsManager = PrefsManager(this)
        securePrefs = SecurePrefsManager(this)
        
        createNotificationChannel()
        createAlertNotificationChannel()
        
        UnifiedNotificationService.emailReceiverRunning = true
        UnifiedNotificationService.emailReceiverInfo = "邮件接收服务运行中"
        UnifiedNotificationService.updateNotification(this)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        startEmailChecking()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ✅ 处理强制检查请求
        if (intent?.action == ACTION_FORCE_CHECK) {
            Log.i(tag, "收到强制检查邮件请求，立即执行...")
            // 立即执行一次邮件检查
            executor.execute {
                checkForAlertEmails()
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        executor.shutdown()
        UnifiedNotificationService.emailReceiverRunning = false
        UnifiedNotificationService.emailReceiverInfo = ""
        //UnifiedNotificationService.updateNotification(this)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "邮件接收服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "定期检测邮箱中的新邮件"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createAlertNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "紧急警报通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "收到紧急警报邮件时发出强提醒"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("安守")
            .setContentText("邮件接收服务运行中")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startEmailChecking() {
        executor.scheduleAtFixedRate({
            checkForAlertEmails()
        }, 0, CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES)
    }

    private fun checkForAlertEmails() {
        val mode = prefsManager.getAppMode()
        Log.d(tag, "开始检查邮件，当前模式：$mode")
        // ✅ 写入文件日志
        com.livewell.untils.AppLogger.i(tag, "📬 开始检查警报邮件")
        
        //  所有模式都检查邮件，不做限制
        // 如果是被守护模式（guardian），也会检查邮件（用于测试或其他用途）

        //  守护模式下：使用守护人自己配置的邮箱（用于接收被守护人的警报）
        //  被守护模式下：使用紧急联系人邮箱（用于测试或特殊场景）
        var email = securePrefs.getEmailAccount()  //  发件人邮箱（需要授权码的那个）
        var authCode = securePrefs.getEmailAuthCode()
        var host = securePrefs.getSmtpHost() ?: prefsManager.getEmailSmtpHost()
        
        if (email.isNullOrEmpty() || authCode.isNullOrEmpty()) {
            Log.e(tag, "邮件配置不完整，无法检查")
            Log.e(tag, "   - 发件人邮箱 (getEmailAccount): ${email?.isNullOrEmpty()}")
            Log.e(tag, "   - 授权码 (getEmailAuthCode): ${authCode?.isNullOrEmpty()}")
            Log.w(tag, "提示：请在设置中配置邮箱和授权码（需要填写授权码的邮箱）")
            return
        }
        
        Log.d(tag, " 邮件配置验证通过：email=$email, host=$host")
        
        try {
            checkImapInbox(email, authCode, host)
        } catch (e: Exception) {
            Log.e(tag, "检查邮件失败：${e.message}", e)
        }
    }

    private fun checkImapInbox(email: String, authCode: String, host: String) {
        //  正确的 IMAP 服务器映射
        val imapHost = when {
            host.contains("smtp.qq.com") -> "imap.qq.com"
            host.contains("smtp.163.com") -> "imap.163.com"
            host.contains("smtp.126.com") -> "imap.126.com"
            host.contains("smtp.gmail.com") -> "imap.gmail.com"
            host.contains("smtp.exmail.qq.com") -> "imap.exmail.qq.com"
            host.contains("smtp") -> host.replace("smtp", "imap")  // 兜底方案
            else -> host  // 如果不是 smtp 开头，直接使用原值
        }
        
        Log.d(tag, "IMAP 服务器：$imapHost (原始：$host)")
        
        val props = Properties().apply {
            put("mail.store.protocol", "imap")
            put("mail.imap.host", imapHost)
            put("mail.imap.port", "993")
            put("mail.imap.ssl.enable", "true")
            put("mail.imap.ssl.trust", "*")  //  信任所有证书
            put("mail.imap.connectiontimeout", "10000")  //  连接超时 10 秒
            put("mail.imap.timeout", "10000")  //  操作超时 10 秒
        }
        
        val session = Session.getInstance(props, null)
        val store = session.getStore("imap")
        
        try {
            Log.i(tag, "开始连接 IMAP 服务器...")
            Log.d(tag, "   - 服务器：$imapHost:993")
            Log.d(tag, "   - 邮箱：$email")
            Log.d(tag, "   - SSL：启用")
            Log.d(tag, "   - 超时：10秒")
            
            // 连接服务器
            store.connect(imapHost, 993, email, authCode)
            Log.i(tag, "IMAP 连接成功")
            
            // 针对 163/126 邮箱发送 IMAP ID 信息
            if (imapHost.contains("163.com") || imapHost.contains("126.com")) {
                try {
                    val imapStore = store as IMAPStore
                    val clientInfo = mapOf(
                        "name" to "AnShou Guardian",
                        "version" to "1.0.0",
                        "vendor" to "LiveWell Studio",
                        "support-email" to "support@livewell.com"
                    )
                    imapStore.id(clientInfo)
                    Log.i(tag, "已发送 IMAP ID 信息（163/126 邮箱必需）")
                } catch (e: Exception) {
                    Log.w(tag, "发送 IMAP ID 失败（非致命错误）：${e.message}")
                }
            }
            
            Log.d(tag, "正在打开收件箱...")
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            Log.i(tag, "收件箱打开成功，邮件总数：${inbox.messageCount}")
            
            val messages = inbox.messages
            val lastCheckTime = prefsManager.getLastEmailCheckTime()
            val currentTime = System.currentTimeMillis()
            
            Log.i(tag, "开始同步邮件...")
            Log.d(tag, "   - 上次检查时间：${formatTimestamp(lastCheckTime)}")
            Log.d(tag, "   - 当前时间：${formatTimestamp(currentTime)}")
            Log.d(tag, "   - 时间范围：最近 30 天")
            
            var newAlertCount = 0
            var checkedCount = 0
            var skippedCount = 0
            
            // 从后往前遍历（最新的邮件先检查）
            for (i in messages.indices.reversed()) {
                val message = messages[i]
                val receivedDate = message.receivedDate?.time ?: 0
                
                // 只检查最近 30 天的邮件（原来是 7 天），避免检查太多旧邮件
                val thirtyDaysAgo = currentTime - 30L * 24 * 60 * 60 * 1000
                if (receivedDate < thirtyDaysAgo) {
                    skippedCount = messages.size - i
                    Log.d(tag, "跳过 ${skippedCount} 封超过 30 天的旧邮件")
                    break  // 因为是倒序遍历，后面的都是更旧的，直接退出
                }
                
                if (receivedDate > lastCheckTime) {
                    checkedCount++
                    Log.d(tag, "检查第 ${checkedCount} 封新邮件 [${i + 1}/${messages.size}]...")
                    if (checkIfAlertEmail(message)) {  // 返回是否找到警报
                        newAlertCount++
                        Log.i(tag, "发现警报邮件 #${newAlertCount}")
                        // ✅ 写入文件日志
                        com.livewell.untils.AppLogger.i(tag, "📧 发现警报邮件 #${newAlertCount}")
                    }
                } else {
                    Log.d(tag, "跳过已检查的邮件 [${i + 1}/${messages.size}]")
                }
            }
            
            prefsManager.saveLastEmailCheckTime(currentTime)
            Log.i(tag, "邮件同步完成")
            Log.i(tag, "   - 共检查 ${checkedCount} 封新邮件")
            Log.i(tag, "   - 发现 ${newAlertCount} 封警报邮件")
            Log.i(tag, "   - 跳过 ${skippedCount} 封旧邮件")
            
            inbox.close(false)
            store.close()
            Log.i(tag, "IMAP 连接已关闭")
            
        } catch (e: Exception) {
            Log.e(tag, "IMAP 连接或同步失败")
            Log.e(tag, "   - 错误类型：${e.javaClass.simpleName}")
            Log.e(tag, "   - 错误信息：${e.message}")
            if (e.cause != null) {
                Log.e(tag, "   - 根本原因：${e.cause?.message}")
            }
            
            // 提供针对性的解决建议
            val errorMessage = e.message ?: ""
            if (errorMessage.contains("Unsafe Login")) {
                Log.e(tag, "\n163 邮箱安全限制")
                Log.e(tag, "   原因：163 邮箱检测到不安全的登录尝试")
                Log.e(tag, "   解决方法：")
                Log.e(tag, "   1. 登录网页版邮箱检查安全状态")
                Log.e(tag, "   2. 重新生成授权码")
                Log.e(tag, "   3. 联系客服：kefu@188.com")
                Log.e(tag, "   4. 检查是否开启了登录保护")
            } else if (errorMessage.contains("AuthenticationFailed")) {
                Log.e(tag, "\n认证失败")
                Log.e(tag, "   原因：授权码错误或未配置")
                Log.e(tag, "   解决方法：重新配置正确的授权码")
            } else if (errorMessage.contains("ConnectException") || errorMessage.contains("timeout")) {
                Log.e(tag, "\n网络连接失败")
                Log.e(tag, "   原因：无法连接到 IMAP 服务器")
                Log.e(tag, "   解决方法：检查网络连接和防火墙设置")
            }
            
            Log.e(tag, "\n完整堆栈：", e)
            throw e  // 重新抛出异常，让调用者知道失败了
        }
    }

    private fun checkIfAlertEmail(message: Message): Boolean {
        return try {
            val from = message.from?.firstOrNull()?.toString() ?: ""
            val subject = message.subject ?: ""
            val content = getTextFromMessage(message)
            
            Log.d(tag, "检查邮件 - 发件人：$from，主题：$subject")
            
            //  获取所有守护对象邮箱列表
            val guardianTargets = prefsManager.getGuardianTargets()
            val isFromGuardianTarget = guardianTargets.any { 
                from.contains(it.email) || it.email.contains(from)
            }
            
            Log.d(tag, "是否来自守护对象：$isFromGuardianTarget, 守护对象数量：${guardianTargets.size}")
            
            //  扩大警报识别范围
            val isAlert = subject.contains("【安守】") && (
                subject.contains("紧急警报") ||
                subject.contains("起床异常") ||
                subject.contains("超时睡眠") ||
                content.contains("紧急情况") ||
                content.contains("起床异常") ||
                content.contains("超时睡眠") ||
                isFromGuardianTarget  //  只要发件人是守护对象就判定为警报
            )
            
            if (isAlert) {
                Log.i(tag, " 检测到警报邮件！主题：$subject，发件人：$from")
                // ✅ 写入文件日志
                com.livewell.untils.AppLogger.i(tag, "📧 检测到警报邮件：$subject")
                sendEnhancedNotification(subject, content, from)
                parseAndSaveGuardianAlert(subject, content, from)
                true
            } else {
                // ✅ 非警报邮件，不通知也不保存
                Log.d(tag, "非警报邮件，跳过。主题：$subject")
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "解析邮件失败：${e.message}", e)
            false  //  失败时返回 false
        }
    }

    private fun getTextFromMessage(message: Message): String {
        return try {
            Log.d(tag, "邮件类型：${message.contentType}")
            
            // ✅ 处理纯文本格式
            if (message.isMimeType("text/plain")) {
                val content = message.content.toString()
                Log.d(tag, "✅ 提取到纯文本内容，长度：${content.length}")
                if (content.length > 0 && content.length <= 200) {
                    Log.d(tag, "内容预览：$content")
                }
                content
            } 
            // ✅ 处理纯 HTML 格式（新增）
            else if (message.isMimeType("text/html")) {
                val htmlContent = message.content.toString()
                Log.d(tag, "检测到纯 HTML 格式，原始长度：${htmlContent.length}")
                
                // 去除 HTML 标签和实体编码
                val plainText = htmlContent
                    .replace(Regex("<[^>]*>"), "")  // 去除所有 HTML 标签
                    .replace("&nbsp;", " ")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&amp;", "&")
                    .replace("&#39;", "'")
                    .replace("&quot;", "\"")
                    .replace(Regex("\\s+"), " ")  // 合并多余空白
                    .trim()
                
                Log.d(tag, "✅ HTML 转换完成，纯文本长度：${plainText.length}")
                if (plainText.length > 0 && plainText.length <= 200) {
                    Log.d(tag, "内容预览：$plainText")
                }
                plainText
            }
            // ✅ 处理多部分格式
            else if (message.isMimeType("multipart/*")) {
                val multipart = message.content as Multipart
                Log.d(tag, "Multipart 邮件，部分数量：${multipart.count}")
                
                // 先尝试找 text/plain
                for (i in 0 until multipart.count) {
                    val part = multipart.getBodyPart(i)
                    Log.d(tag, "检查第 $i 部分：${part.contentType}")
                    
                    if (part.isMimeType("text/plain")) {
                        val content = part.content.toString()
                        Log.d(tag, "✅ 找到 text/plain 部分，长度：${content.length}")
                        if (content.length > 0 && content.length <= 200) {
                            Log.d(tag, "内容预览：$content")
                        }
                        return content
                    }
                }
                
                // 如果没有 text/plain，尝试 text/html
                Log.w(tag, "未找到 text/plain 部分，尝试解析 HTML...")
                for (i in 0 until multipart.count) {
                    val part = multipart.getBodyPart(i)
                    if (part.isMimeType("text/html")) {
                        val htmlContent = part.content.toString()
                        Log.d(tag, "找到 text/html 部分，原始长度：${htmlContent.length}")
                        
                        // 简单去除 HTML 标签
                        val plainText = htmlContent
                            .replace(Regex("<[^>]*>"), "")  // 去除所有 HTML 标签
                            .replace("&nbsp;", " ")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace("&amp;", "&")
                            .replace("&#39;", "'")
                            .replace("&quot;", "\"")
                            .replace(Regex("\\s+"), " ")  // 合并多余空白
                            .trim()
                        
                        Log.d(tag, "✅ HTML 转换完成，纯文本长度：${plainText.length}")
                        if (plainText.length > 0 && plainText.length <= 200) {
                            Log.d(tag, "内容预览：$plainText")
                        }
                        return plainText
                    }
                }
                
                Log.w(tag, "❌ 未找到可读取的文本内容")
                ""
            } else {
                Log.w(tag, "❌ 不支持的邮件类型：${message.contentType}")
                ""
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ 提取邮件内容失败：${e.message}", e)
            ""
        }
    }

    private fun sendEnhancedNotification(title: String, content: String, from: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("alert_source", "email")
            putExtra("alert_from", from)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("紧急警报接收")
            .setContentText("来自：$from\n$title")
            .setStyle(NotificationCompat.BigTextStyle().bigText("来自：$from\n$title\n\n$content"))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(2002, notification)
    }
    
    /**
     * 解析邮件内容并保存为守护警报
     */
    private fun parseAndSaveGuardianAlert(subject: String, content: String, from: String) {
        try {
            Log.d(tag, "开始解析警报邮件")
            Log.d(tag, "  - 主题：$subject")
            Log.d(tag, "  - 发件人：$from")
            Log.d(tag, "  - 内容长度：${content.length}")
            if (content.isNotEmpty()) {
                Log.d(tag, "  - 内容预览：${content.take(100)}...")
            } else {
                Log.w(tag, "  ⚠️ 警告：邮件内容为空！")
            }
            
            // 判断警报类型
            val alertType = when {
                subject.contains("紧急警报") || content.contains("紧急情况") -> "emergency"
                subject.contains("起床异常") || subject.contains("超时睡眠") || 
                content.contains("起床异常") || content.contains("超时睡眠") -> "sleep"
                else -> "unknown"
            }
            
            // 提取步数
            val stepRegex = "(?:今日步数 | 步数)[:：]\\s*(\\d+)".toRegex()
            val stepCount = stepRegex.find(content)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            
            // 提取使用时长
            val usageRegex = "(?:今日使用时长 | 使用时长 | 今日使用)[:：]\\s*(\\d+)".toRegex()
            val usageMinutes = usageRegex.find(content)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            
            // 提取用户自定义消息（尝试从内容中分离）
            val userMessage = extractUserMessage(content)
            
            // 查找匹配的守护对象（通过发送方邮箱地址）
            val guardianTargets = prefsManager.getGuardianTargets()
            val targetId = guardianTargets.find { 
                from.contains(it.senderEmail) || it.senderEmail.contains(from) || 
                (it.senderEmail.isEmpty() && (from.contains(it.email) || it.email.contains(from)))  // 兼容旧数据
            }?.id ?: guardianTargets.firstOrNull()?.id
            
            if (targetId == null) {
                Log.w(tag, "未找到匹配的守护对象，跳过保存警报")
                return
            }
            
            // 创建警报复象
            val alert = com.livewell.model.GuardianAlert(
                targetId = targetId,
                alertType = alertType,
                timestamp = System.currentTimeMillis(),
                title = subject.ifEmpty { "收到警报邮件" },
                content = content,
                userMessage = userMessage,
                stepCount = stepCount,
                usageMinutes = usageMinutes,
                isRead = false
            )
            
            // 保存到本地
            prefsManager.addGuardianAlert(alert)
            Log.i(tag, " 守护警报已保存：${alert.id}")
            Log.d(tag, "  - 保存的内容长度：${alert.content.length}")
            
        } catch (e: Exception) {
            Log.e(tag, "解析并保存守护警报失败：${e.message}", e)
        }
    }
    
    /**
     * 从邮件内容中提取用户自定义消息
     */
    private fun extractUserMessage(content: String): String {
        // 尝试提取分隔线以上的内容作为用户消息
        val separatorIndex = content.indexOf("================================")
        return if (separatorIndex > 0) {
            // 有分隔线，提取分隔线以上的内容
            content.substring(0, separatorIndex).trim()
        } else {
            // 没有分隔线，返回完整内容（不要截取）
            content.trim()
        }
    }
    
    /**
     *  格式化时间戳用于日志
     */
    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0) return "未初始化"
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}