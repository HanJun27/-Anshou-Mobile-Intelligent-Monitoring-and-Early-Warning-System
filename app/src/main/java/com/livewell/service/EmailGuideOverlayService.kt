package com.livewell.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.material.card.MaterialCardView
import com.livewell.R

/**
 * 邮箱配置悬浮窗指引服务
 */
class EmailGuideOverlayService : AccessibilityService() {
    
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var currentStep = 0
    private var emailType = ""
    private var emailAddress = ""
    
    // 拖动相关
    private var touchX = 0f
    private var touchY = 0f
    private var initialX = 0
    private var initialY = 0
    
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "email_guide_overlay_channel"
        private const val NOTIFICATION_ID = 1001
    }
    
    // 步骤定义
    data class GuideStep(
        val title: String,
        val description: String,
        val imageUrl: String,
        val targetUrlPattern: String,
        val buttonText: String
    )
    
    private val qqSteps = listOf(
        GuideStep(
            title = "第 1 步：切换到电脑版",
            description = "手机版界面功能受限，请点击右上角菜单，勾选「桌面版网站」",
            imageUrl = "guide_qq_desktop",
            targetUrlPattern = "mail.qq.com",
            buttonText = "已切换到电脑版"
        ),
        GuideStep(
            title = "第 2 步：登录邮箱",
            description = "请输入您的 QQ 邮箱账号和密码进行登录",
            imageUrl = "guide_qq_login",
            targetUrlPattern = "mail.qq.com",
            buttonText = "我已登录"
        ),
        GuideStep(
            title = "第 3 步：进入设置页面",
            description = "点击页面顶部的「设置」标签（在邮箱名称下方的一排按钮中）",
            imageUrl = "guide_qq_settings",
            targetUrlPattern = "mail.qq.com",
            buttonText = "已进入设置页面"
        ),
        GuideStep(
            title = "第 4 步：找到账户设置",
            description = "在设置页面确保选中了「账户」选项卡，然后向下滚动找到「POP3/IMAP/SMTP/Exchange」服务",
            imageUrl = "guide_qq_account",
            targetUrlPattern = "mail.qq.com.*tab=account",
            buttonText = "已找到 SMTP 设置"
        ),
        GuideStep(
            title = "第 5 步：开启服务并获取授权码",
            description = "点击「开启服务」按钮（从灰色变为绿色），然后点击「生成授权码」并复制",
            imageUrl = "guide_qq_authcode",
            targetUrlPattern = "mail.qq.com.*tab=account",
            buttonText = "已复制授权码"
        )
    )
    
    private val netease163Steps = listOf(
        GuideStep(
            title = "第 1 步：切换到电脑版",
            description = "手机版界面功能受限，请点击右上角菜单，勾选「桌面版网站」",
            imageUrl = "guide_163_desktop",
            targetUrlPattern = "mail.163.com",
            buttonText = "已切换到电脑版"
        ),
        GuideStep(
            title = "第 2 步：登录邮箱",
            description = "请输入您的 163 邮箱账号和密码进行登录",
            imageUrl = "guide_163_login",
            targetUrlPattern = "mail.163.com",
            buttonText = "我已登录"
        ),
        GuideStep(
            title = "第 3 步：进入设置页面",
            description = "点击页面右上角的齿轮图标，选择「POP3/SMTP/IMAP」",
            imageUrl = "guide_163_settings",
            targetUrlPattern = "mail.163.com",
            buttonText = "已进入设置页面"
        ),
        GuideStep(
            title = "第 4 步：开启 SMTP 服务",
            description = "找到「POP3/SMTP 服务」或「IMAP/SMTP 服务」，点击开关将其开启",
            imageUrl = "guide_163_smtp",
            targetUrlPattern = "mail.163.com.*module=options",
            buttonText = "已开启服务"
        ),
        GuideStep(
            title = "第 5 步：获取授权码",
            description = "点击「客户端授权码」，获取授权码并复制",
            imageUrl = "guide_163_authcode",
            targetUrlPattern = "mail.163.com.*module=options",
            buttonText = "已复制授权码"
        )
    )
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        createNotificationChannel()
        // 服务连接成功
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // 检测 URL 变化（需要 Android 7.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val url = event.source?.let { node ->
                // 尝试从 WebView 中提取 URL
                // 这部分需要根据实际情况调整
                null
            }
            
            // 根据 URL 自动切换步骤
            url?.let { currentUrl ->
                autoSwitchStep(currentUrl)
            }
        }
        
        // 监听窗口状态变化
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 窗口变化时检查是否需要更新指引
                checkAndUpdateStep()
            }
        }
    }
    
    override fun onInterrupt() {
        // 服务被中断
        hideOverlay()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
    }
    
    /**
     * 显示悬浮窗指引
     */
    fun showGuide(step: GuideStep) {
        if (overlayView != null) {
            updateGuideContent(step)
            return
        }
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // 创建悬浮窗布局
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_email_guide, null)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else 
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 100 // 距离底部 100px
        }
        
        windowManager?.addView(overlayView, params)
        
        // 初始化内容
        updateGuideContent(step)
        
        // ✅ 设置触摸监听器实现拖动功能
        setupDragListener(params)
    }
    
    /**
     * 设置拖动监听器
     */
    private fun setupDragListener(layoutParams: WindowManager.LayoutParams) {
        overlayView?.findViewById<View>(R.id.draggableArea)?.let { draggableArea ->
            draggableArea.setOnTouchListener { view, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        touchX = event.rawX
                        touchY = event.rawY
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchX).toInt()
                        val dy = (event.rawY - touchY).toInt()
                        
                        layoutParams.x = initialX + dx
                        layoutParams.y = initialY + dy
                        
                        windowManager?.updateViewLayout(overlayView, layoutParams)
                        true
                    }
                    else -> false
                }
            }
        }
    }
    
    /**
     * 更新指引内容
     */
    private fun updateGuideContent(step: GuideStep) {
        overlayView?.apply {
            findViewById<TextView>(R.id.tvStepTitle)?.text = step.title
            findViewById<TextView>(R.id.tvStepDescription)?.text = step.description
            
            // 加载配图（由用户后续添加）
            val imageView = findViewById<ImageView>(R.id.ivGuideImage)
            val resId = resources.getIdentifier(step.imageUrl, "drawable", packageName)
            if (resId != 0) {
                imageView.setImageResource(resId)
                imageView.visibility = View.VISIBLE
            } else {
                imageView.visibility = View.GONE
            }
            
            // 下一步按钮
            findViewById<Button>(R.id.btnNextStep)?.apply {
                text = step.buttonText
                setOnClickListener {
                    goToNextStep()
                }
            }
            
            // 关闭按钮
            findViewById<Button>(R.id.btnCloseGuide)?.setOnClickListener {
                hideOverlay()
            }
        }
    }
    
    /**
     * 自动根据 URL 切换步骤
     */
    private fun autoSwitchStep(currentUrl: String) {
        val steps = if (emailType == "qq") qqSteps else netease163Steps
        
        for ((index, step) in steps.withIndex()) {
            if (currentUrl.contains(Regex(step.targetUrlPattern))) {
                if (currentStep != index) {
                    currentStep = index
                    showGuide(step)
                    Toast.makeText(this, "💡 检测到新页面，已更新指引", Toast.LENGTH_SHORT).show()
                }
                break
            }
        }
    }
    
    /**
     * 检查并更新步骤
     */
    private fun checkAndUpdateStep() {
        // 这里可以根据屏幕内容智能判断用户当前所在步骤
        // 需要结合无障碍服务的节点信息
    }
    
    /**
     * 前往下一步
     */
    private fun goToNextStep() {
        val steps = if (emailType == "qq") qqSteps else netease163Steps
        
        if (currentStep < steps.size - 1) {
            currentStep++
            showGuide(steps[currentStep])
        } else {
            // 最后一步，提示完成
            Toast.makeText(this, "✅ 恭喜！您已完成所有步骤，请返回应用填写授权码", Toast.LENGTH_LONG).show()
            
            // ✅ 延迟关闭悬浮窗，避免触发 ANR
            Handler(Looper.getMainLooper()).postDelayed({
                hideOverlay()
            }, 500) // 延迟 500ms 关闭，给系统缓冲时间
        }
    }
    
    /**
     * 隐藏悬浮窗
     */
    private fun hideOverlay() {
        try {
            overlayView?.let { view ->
                windowManager?.removeView(view)
                overlayView = null
            }
        } catch (e: IllegalArgumentException) {
            // 如果视图已经被移除，忽略异常
            e.printStackTrace()
        } finally {
            overlayView = null
        }
    }
    
    /**
     * 启动服务时初始化参数
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ✅ 首先调用 startForeground() 避免 ANR
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ 需要指定前台服务类型
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        intent?.let {
            emailType = it.getStringExtra("email_type") ?: ""
            emailAddress = it.getStringExtra("email_address") ?: ""
            
            // 显示第一步指引
            val steps = if (emailType == "qq") qqSteps else netease163Steps
            if (steps.isNotEmpty()) {
                showGuide(steps[0])
            }
        }
        
        return START_STICKY
    }
    
    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "邮箱配置悬浮窗指引",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于显示邮箱配置悬浮窗指引服务的前台通知"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 创建前台通知
     */
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("邮箱配置指引中")
            .setContentText("正在为您提供邮箱配置帮助")
            .setSmallIcon(R.mipmap.ic_launcher) // 使用应用图标
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
