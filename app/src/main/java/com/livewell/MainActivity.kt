package com.livewell

import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.livewell.service.BackgroundTimerService
import com.livewell.service.CheckinService
import com.livewell.service.EmailReceiverService
import com.livewell.service.KeepAliveChecker
import com.livewell.service.KeepAliveJobService
import com.livewell.service.MailSender
import com.livewell.service.SleepMonitorService
import com.livewell.service.TestEmailService
import com.livewell.service.UnifiedNotificationService
import com.livewell.service.CommunityReportService
import com.livewell.service.AccessibilityKeepAliveService
import com.livewell.service.SilentMusicService
import com.livewell.service.TimeCapsuleWorker
import com.livewell.untils.KeepAliveHelper
import com.livewell.untils.PrefsManager
import com.livewell.untils.ScreenLockHelper
import com.livewell.untils.SecurePrefsManager
import com.livewell.untils.UsageStatsHelper
import com.livewell.untils.SystemStepManager
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.material.button.MaterialButton
import androidx.appcompat.widget.SwitchCompat
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import java.util.concurrent.TimeUnit






class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var prefsManager: PrefsManager
    private lateinit var usageStatsHelper: UsageStatsHelper
    private lateinit var currentMode: String
    private lateinit var keepAliveHelper: KeepAliveHelper
    
    // ✅ 底部导航栏相关
    private lateinit var bottomNavigationView: com.google.android.material.bottomnavigation.BottomNavigationView
    private var homeFragment: HomeFragment? = null
    private var settingsFragment: SettingsFragment? = null
    private var guardianFragment: GuardianFragment? = null
    
    private var timerUpdateHandler: Handler? = null
    private var timerUpdateRunnable: Runnable? = null
    
    private var titleClickCount = 0
    private val TITLE_CLICK_THRESHOLD = 10
    private var lastClickTime: Long = 0
    private val CLICK_TIME_WINDOW = 3000
    private lateinit var systemStepManager: SystemStepManager
      private val tag = "MainActivity" 
    //步数显示
 
    
    private lateinit var dialog: AlertDialog
    
    // ✅ 全局异常处理器
    private val exceptionHandler = Thread.UncaughtExceptionHandler { thread, throwable ->
        Log.e("MainActivity", "未捕获的异常", throwable)
        saveCrashInfo(throwable)
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[android.Manifest.permission.SEND_SMS] ?: false
        if (smsGranted) {
            Toast.makeText(this, "短信权限已授予", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 时光胶囊附件相关
    private var currentAttachmentType: String? = null
    private var imageCaptureUri: Uri? = null
    private var currentAttachmentsRecyclerView: androidx.recyclerview.widget.RecyclerView? = null  // ✅ 保存当前附件列表 RecyclerView 引用
    
    // ✅ SOS紧急求助位置相关
    private var pendingLocationRequest: Boolean = false  // 是否有待处理的位置请求
    
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && imageCaptureUri != null) {
            saveAttachment(imageCaptureUri.toString())
            Toast.makeText(this, "照片已保存", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "拍照失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // ✅ 修复：复制文件到应用私有目录，而不是只保存 URI 引用
            val copiedUri = copyFileToAppDirectory(it, "image")
            if (copiedUri != null) {
                saveAttachment(copiedUri.toString())
                Toast.makeText(this, "图片已添加", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "图片添加失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // ✅ 修复：复制文件到应用私有目录，而不是只保存 URI 引用
            val copiedUri = copyFileToAppDirectory(it, "video")
            if (copiedUri != null) {
                saveAttachment(copiedUri.toString())
                Toast.makeText(this, "视频已添加", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "视频添加失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // ✅ 位置权限请求
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineLocationGranted || coarseLocationGranted) {
            Log.i(TAG, "✅ 位置权限已授予")
            // 如果有待处理的SOS请求，继续执行
            if (pendingLocationRequest) {
                pendingLocationRequest = false
                getLocationAndSendSOS()
            }
        } else {
            Log.w(TAG, "⚠️ 位置权限被拒绝")
            Toast.makeText(this, "位置权限被拒绝，将无法获取位置信息", Toast.LENGTH_LONG).show()
            // 即使没有位置权限，也继续发送SOS
            if (pendingLocationRequest) {
                pendingLocationRequest = false
                sendSOSWithoutLocation()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // ✅ 初始化文件日志工具
        com.livewell.untils.FileLogger.init(applicationContext)
        com.livewell.untils.FileLogger.i("MainActivity", "====== 应用启动 ======")
        
        // ✅ 设置全局异常捕获
        Thread.setDefaultUncaughtExceptionHandler(exceptionHandler)
        
        // ✅ 初始化日志系统
        try {
            com.livewell.untils.LogWriter.init(this)
            com.livewell.untils.LogWriter.writeLog("MainActivity", "====== 应用启动 ======")
            com.livewell.untils.LogWriter.writeLog("MainActivity", "Android 版本：${Build.VERSION.RELEASE}")
            com.livewell.untils.LogWriter.writeLog("MainActivity", "设备型号：${Build.MANUFACTURER} ${Build.MODEL}")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "日志系统初始化失败", e)
        }
        
        try {
            Log.i("MainActivity", "====== 应用启动 ======")
            Log.i("MainActivity", "Android 版本：${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})")
            Log.i("MainActivity", "设备型号：${Build.MANUFACTURER} ${Build.MODEL}")
            Log.i("MainActivity", "应用版本：${packageManager.getPackageInfo(packageName, 0).versionName}")
            
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)
            
            Log.i("MainActivity", "布局加载完成")
            
            // ✅ 初始化底部导航栏
            bottomNavigationView = findViewById(R.id.bottomNavigationView)
            setupBottomNavigation()
            
            // ✅ 逐步初始化并记录日志
            try {
                prefsManager = PrefsManager(this)
                usageStatsHelper = UsageStatsHelper(this)
                Log.i("MainActivity", "PrefsManager 初始化成功")
            } catch (e: Exception) {
                Log.e("MainActivity", "PrefsManager 初始化失败", e)
                showErrorDialog("配置管理器初始化失败", e)
                return
            }
            
            try {
                usageStatsHelper = UsageStatsHelper(this)
                Log.i("MainActivity", "UsageStatsHelper 初始化成功")
            } catch (e: Exception) {
                Log.e("MainActivity", "UsageStatsHelper 初始化失败", e)
                showErrorDialog("使用统计助手初始化失败", e)
                return
            }

            // ✅ 添加 SystemStepManager 初始化
try {
    systemStepManager = SystemStepManager.getInstance(this) // ✅ 已修改为单例方法
    if (systemStepManager.hasStepCounter()) {
        systemStepManager.registerListener()
        systemStepManager.loadFromPrefs(prefsManager)
        Log.i("MainActivity", "步数传感器初始化成功")
    } else {
        Log.w("MainActivity", "设备不支持步数传感器")
    }
} catch (e: Exception) {
    Log.e("MainActivity", "SystemStepManager 初始化失败", e)
}
            
            try {
                currentMode = prefsManager.getAppMode()
                keepAliveHelper = KeepAliveHelper(this)
                Log.i("MainActivity", "模式加载成功：$currentMode")
            } catch (e: Exception) {
                Log.e("MainActivity", "模式加载失败", e)
                showErrorDialog("模式加载失败", e)
                return
            }
            
            Log.i("MainActivity", "视图初始化完成")
            
            checkPermissions()
            startAllServices()
            setupTitleClickListener()
            
            // ✅ 启动服务时添加错误捕获
            try {
                KeepAliveChecker.start(this)
                Log.i("MainActivity", "KeepAliveChecker 启动成功")
            } catch (e: Exception) {
                Log.e("MainActivity", "KeepAliveChecker 启动失败", e)
            }
            
            try {
                KeepAliveJobService.scheduleJob(this)
                Log.i("MainActivity", "KeepAliveJobService 调度成功")
            } catch (e: Exception) {
                Log.e("MainActivity", "KeepAliveJobService 调度失败", e)
            }
            
            

try {
    val screenLockHelper = ScreenLockHelper(this)
    screenLockHelper.acquireCpuWakeLock()
    Log.i("MainActivity", "唤醒锁获取成功")
} catch (e: Exception) {
    Log.e("MainActivity", "唤醒锁获取失败", e)
}


            try {
                val screenLockHelper = ScreenLockHelper(this)
                screenLockHelper.acquireCpuWakeLock()
                Log.i("MainActivity", "唤醒锁获取成功")
            } catch (e: Exception) {
                Log.e("MainActivity", "唤醒锁获取失败", e)
            }
            
            Handler(Looper.getMainLooper()).postDelayed({
                checkAndGuideKeepAlive()
            }, 1000)
            
            Log.i("MainActivity", "====== 应用启动完成 ======")
            
        } catch (e: Exception) {
            Log.e("MainActivity", "onCreate 发生异常", e)
            showErrorDialog("应用启动失败", e)
        }
    }

     override fun onDestroy() {
    super.onDestroy()
}

    override fun onPause() {
        super.onPause()
        if (::systemStepManager.isInitialized) {
            systemStepManager.saveToPrefs(prefsManager)
            Log.d("MainActivity", "Activity 暂停，保存步数：${systemStepManager.getTodaySteps()}")
        }
    }


 override fun onResume() {
        super.onResume()
        if (::systemStepManager.isInitialized) {
            systemStepManager.loadFromPrefs(prefsManager)
            Log.d("MainActivity", "Activity 恢复，重新加载步数：${systemStepManager.getTodaySteps()}")
        }
    }


    // ✅ 显示错误对话框
    private fun showErrorDialog(title: String, exception: Exception) {
        val message = buildString {
            append("错误：$title\n\n")
            append("异常类型：${exception.javaClass.simpleName}\n")
            append("异常信息：${exception.message}\n\n")
            append("堆栈追踪:\n")
            append(exception.stackTrace.take(5).joinToString("\n") { "  at $it" })
        }
        
        Log.e("MainActivity", "错误详情:\n$message")
        
        runOnUiThread {
            try {
                AlertDialog.Builder(this)
                    .setTitle("⚠️ 启动错误")
                    .setMessage(message)
                    .setPositiveButton("复制错误信息") { _, _ ->
                        copyToClipboard(message)
                    }
                    .setNegativeButton("退出") { _, _ ->
                        finish()
                    }
                    .setCancelable(false)
                    .show()
            } catch (e: Exception) {
                Log.e("MainActivity", "显示错误对话框失败", e)
            }
        }
    }
    
    // ✅ 复制错误到剪贴板
    private fun copyToClipboard(text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("error_log", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "错误信息已复制", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "复制失败", e)
        }
    }
    
    // ✅ 保存崩溃信息到文件
    private fun saveCrashInfo(throwable: Throwable) {
        try {
            val crashInfo = buildString {
                append("====== 崩溃报告 ======\n")
                append("时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                append("设备：${Build.MANUFACTURER} ${Build.MODEL}\n")
                append("Android: ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})\n")
                append("应用版本：${packageManager.getPackageInfo(packageName, 0).versionName}\n\n")
                append("异常类型：${throwable.javaClass.simpleName}\n")
                append("异常信息：${throwable.message}\n\n")
                append("堆栈追踪:\n")
                append(throwable.stackTrace.joinToString("\n") { "  at $it" })
                
                if (throwable.cause != null) {
                    append("\n\n原因:\n")
                    append(throwable.cause?.stackTrace?.joinToString("\n") { "  at $it" })
                }
            }
            
            Log.e("MainActivity", "崩溃信息:\n$crashInfo")
            
            val file = java.io.File(cacheDir, "crash_${System.currentTimeMillis()}.log")
            file.writeText(crashInfo)
            Log.i("MainActivity", "崩溃日志已保存到：${file.absolutePath}")
            
        } catch (e: Exception) {
            Log.e("MainActivity", "保存崩溃信息失败", e)
        }
    }

     /**
     * 设置底部导航栏
     */
    private fun setupBottomNavigation() {
        // 初始化 Fragment
        homeFragment = HomeFragment()
        settingsFragment = SettingsFragment()
        guardianFragment = GuardianFragment.newInstance()
        
        // 默认显示主页
        loadFragment(homeFragment!!)
        
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    loadFragment(homeFragment!!)
                    true
                }
                R.id.navigation_guardian -> {
                    loadFragment(guardianFragment!!)
                    true
                }
                R.id.navigation_settings -> {
                    loadFragment(settingsFragment!!)
                    true
                }
                else -> false
            }
        }
        
        // 设置 SOS 悬浮按钮
        setupSOSButton()
    }
    
    /**
     * 设置 SOS 紧急求助按钮
     */
    private fun setupSOSButton() {
        val fabSOS = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabSOS)
        fabSOS.setOnClickListener {
            showSOSDialog()
        }
    }
    
    /**
     * 显示 SOS 紧急求助对话框
     */
    private fun showSOSDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("🆘 紧急求助")
            .setMessage("您即将触发紧急求助，系统将：\n\n1. 向所有紧急联系人发送求助邮件\n2. 包含您的当前位置信息\n3. 记录此次求助事件\n\n是否继续？")
            .setPositiveButton("确认求助") { _, _ ->
                triggerEmergencyAlert()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 触发紧急求助
     */
    private fun triggerEmergencyAlert() {
        // ✅ 检查位置权限
        val hasFineLocation = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        if (!hasFineLocation && !hasCoarseLocation) {
            // 没有位置权限，请求权限
            Log.i(TAG, "⚠️ 未授予位置权限，正在请求...")
            pendingLocationRequest = true
            locationPermissionLauncher.launch(arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ))
            return
        }
        
        // 已有位置权限，直接获取位置并发送
        getLocationAndSendSOS()
    }
    
    /**
     * ✅ 获取位置并发送SOS邮件
     */
    private fun getLocationAndSendSOS() {
        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            
            // 尝试获取最后已知位置
            var location: android.location.Location? = null
            
            // 优先使用 GPS
            if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                try {
                    location = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                } catch (e: Exception) {
                    Log.e(TAG, "GPS 位置获取失败：${e.message}")
                }
            }
            
            // 如果 GPS 不可用，使用网络定位
            if (location == null && locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                try {
                    location = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                } catch (e: Exception) {
                    Log.e(TAG, "网络位置获取失败：${e.message}")
                }
            }
            
            val locationInfo = if (location != null) {
                String.format("纬度: %.6f, 经度: %.6f, 精度: %.0f米", 
                    location.latitude, 
                    location.longitude,
                    location.accuracy)
            } else {
                "位置信息获取失败（可能GPS信号弱或无网络连接）"
            }
            
            Log.i(TAG, "✅ 位置信息：$locationInfo")
            sendSOSWithEmail(locationInfo)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取位置失败：${e.message}", e)
            sendSOSWithoutLocation()
        }
    }
    
    /**
     * ✅ 发送SOS邮件（带位置信息）- 自动通过SMTP发送
     */
    private fun sendSOSWithEmail(locationInfo: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            
            // 创建紧急求助邮件内容
            val subject = "【紧急求助】安守 LiveWell - $timestamp"
            val body = """
                🆘 紧急求助警报！
                
                触发时间：$timestamp
                📍 位置信息：$locationInfo
                📱 设备型号：${Build.MANUFACTURER} ${Build.MODEL}
                
                ⚠️ 请立即联系用户确认安全状况！
            """.trimIndent()
            
            // ✅ 获取邮箱配置
            val toEmail = prefsManager.getEmailTo()
            val fromEmail = com.livewell.untils.SecurePrefsManager(this).getEmailAccount()
            val authCode = com.livewell.untils.SecurePrefsManager(this).getEmailAuthCode()
            val smtpHost = prefsManager.getEmailSmtpHost()
            val smtpPort = prefsManager.getEmailSmtpPort()
            
            // 验证配置完整性
            if (toEmail.isNullOrEmpty()) {
                Toast.makeText(this, "未设置收件人邮箱，请先在「功能设置」中配置邮箱", Toast.LENGTH_LONG).show()
                return
            }
            
            if (fromEmail.isNullOrEmpty() || authCode.isNullOrEmpty()) {
                Toast.makeText(this, "未配置发件人邮箱，请先在「功能设置」中配置邮箱", Toast.LENGTH_LONG).show()
                return
            }
            
            if (smtpHost.isNullOrEmpty()) {
                Toast.makeText(this, "未配置SMTP服务器，请先在「功能设置」中配置邮箱", Toast.LENGTH_LONG).show()
                return
            }
            
            // ✅ 显示发送中提示
            Toast.makeText(this, "正在发送紧急求助邮件...", Toast.LENGTH_SHORT).show()
            Log.i(TAG, "🚀 开始发送SOS邮件")
            
            // ✅ 使用 MailSender 自动发送邮件
            val mailSender = com.livewell.service.MailSender()
            mailSender.sendEmail(
                host = smtpHost,
                port = smtpPort,
                fromEmail = fromEmail,
                authCode = authCode,
                toEmail = toEmail,
                subject = subject,
                content = body,
                context = this,
                callback = object : com.livewell.service.MailSender.SendCallback {
                    override fun onSuccess() {
                        Log.i(TAG, "✅ SOS邮件发送成功")
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "✅ 紧急求助邮件已发送\n位置：$locationInfo",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    
                    override fun onError(error: String) {
                        Log.e(TAG, "❌ SOS邮件发送失败：$error")
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "❌ 邮件发送失败：$error\n请检查网络或邮箱配置",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 触发紧急求助失败", e)
            Toast.makeText(this, "求助失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * ✅ 发送SOS邮件（无位置信息）
     */
    private fun sendSOSWithoutLocation() {
        sendSOSWithEmail("位置信息不可用")
    }
    
    /**
     * 加载 Fragment
     */
    private fun loadFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun performCheckin() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        prefsManager.saveLastCheckinDate(today)
        prefsManager.saveCheckinTime(System.currentTimeMillis())
        
        Log.i("MainActivity", "====== 签到成功 ======")
        Log.i("MainActivity", "签到日期：$today")
        Log.i("MainActivity", "签到时间：${System.currentTimeMillis()}")
        
        Toast.makeText(this, "签到成功！", Toast.LENGTH_SHORT).show()
        // ✅ 通知 HomeFragment 更新 UI
        homeFragment?.let { fragment ->
            fragment.updateUI()
        }
    }
    
    /**
     * 公开方法：更新模式显示（供 GuardianFragment 调用）
     */
    fun updateModeDisplayPublic() {
        homeFragment?.updateModeDisplay()
    }

    private fun showSettingsDialog() {
        showEmergencyContactSettingsDialog()
    }

private fun showEmergencyContactSettingsDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("紧急联系人设置")
        
        val view = layoutInflater.inflate(R.layout.dialog_emergency_contact, null)
        val radioGroup = view.findViewById<RadioGroup>(R.id.radioGroupNotifyType)
        val layoutEmailSettings = view.findViewById<LinearLayout>(R.id.layoutEmailSettings)
        
        val etToEmail = view.findViewById<EditText>(R.id.etToEmail)
        val etFromEmail = view.findViewById<EditText>(R.id.etFromEmail)
        val etAuthCode = view.findViewById<EditText>(R.id.etAuthCode)
        val etSmtpHost = view.findViewById<EditText>(R.id.etSmtpHost)
        val etSmtpPort = view.findViewById<EditText>(R.id.etSmtpPort)
        val etAlertMessage = view.findViewById<EditText>(R.id.etAlertMessage)
        val btnTestEmail = view.findViewById<Button>(R.id.btnTestEmail)
        
        val currentNotifyType = prefsManager.getNotifyType()
        when (currentNotifyType) {
            PrefsManager.NOTIFY_EMAIL -> radioGroup.check(R.id.radioEmail)
            else -> radioGroup.check(R.id.radioEmail)
        }
        
        // ✅ 始终显示邮件设置
        layoutEmailSettings.visibility = View.VISIBLE
        
        etToEmail.setText(prefsManager.getEmailTo())
        etFromEmail.setText(SecurePrefsManager(this).getEmailAccount())
        //etAuthCode.setText("")
        etSmtpHost.setText(SecurePrefsManager(this).getSmtpHost() ?: prefsManager.getEmailSmtpHost())
        etSmtpPort.setText(SecurePrefsManager(this).getSmtpPort() ?: prefsManager.getEmailSmtpPort())
        etAlertMessage.setText(prefsManager.getAlertMessage())
        
        btnTestEmail.setOnClickListener {
            val toEmail = etToEmail.text.toString()
            val fromEmail = etFromEmail.text.toString()
            val authCode = etAuthCode.text.toString()
            val host = etSmtpHost.text.toString()
            val port = etSmtpPort.text.toString()
            
            if (toEmail.isEmpty() || fromEmail.isEmpty() || authCode.isEmpty()) {
                Toast.makeText(this, "请填写完整的邮件信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val mailSender = MailSender()
            mailSender.sendEmail(
                host = host,
                port = port,
                fromEmail = fromEmail,
                authCode = authCode,
                toEmail = toEmail,
                subject = "安守测试邮件",
                content = "这是一封来自安守应用的测试邮件，请忽略。",
                callback = object : MailSender.SendCallback {
                    override fun onSuccess() {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "测试邮件发送成功", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    override fun onError(error: String) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "发送失败：$error", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                context = this
            )
        }
        
        builder.setView(view)
        builder.setPositiveButton("保存") { _, _ ->
            val message = etAlertMessage.text.toString()
            
            // ✅ 默认使用邮件通知
            prefsManager.saveNotifyType(PrefsManager.NOTIFY_EMAIL)
            prefsManager.saveAlertMessage(message)
            
            val toEmail = etToEmail.text.toString()
            val fromEmail = etFromEmail.text.toString()
            val host = etSmtpHost.text.toString()
            val port = etSmtpPort.text.toString()
            
            // 如果授权码输入框为空，保留原有授权码，避免切换报警方式时丢失
            val securePrefs = SecurePrefsManager(this)
            val authCode = if (etAuthCode.text.toString().isEmpty()) {
                securePrefs.getEmailAuthCode() ?: ""
            } else {
                etAuthCode.text.toString()
            }
            
            prefsManager.setEmailTo(toEmail)
            prefsManager.setEmailSmtpHost(host)
            prefsManager.setEmailSmtpPort(port)
            prefsManager.setEmailEnabled(true)
            
            securePrefs.saveEmailCredentials(fromEmail, authCode)
            securePrefs.saveSmtpConfig(host, port)
            
            Toast.makeText(this, "紧急联系人设置已保存", Toast.LENGTH_SHORT).show()
        }
        
        builder.setNegativeButton("取消", null)
        builder.show()
    }



    private fun showEmailSettingsDialog() {
    val builder = AlertDialog.Builder(this)
    builder.setTitle("邮件紧急联系人设置")
    val view = layoutInflater.inflate(R.layout.dialog_email_settings, null)
    val etToEmail = view.findViewById<EditText>(R.id.etToEmail)
    val etFromEmail = view.findViewById<EditText>(R.id.etFromEmail)
    val etAuthCode = view.findViewById<EditText>(R.id.etAuthCode)
    val etSmtpHost = view.findViewById<EditText>(R.id.etSmtpHost)
    val etSmtpPort = view.findViewById<EditText>(R.id.etSmtpPort)
    val btnTest = view.findViewById<Button>(R.id.btnTestEmail)
    
    // ... 省略部分代码 ...
    
    btnTest.setOnClickListener {
        val toEmail = etToEmail.text.toString()
        val fromEmail = etFromEmail.text.toString()
        val authCode = etAuthCode.text.toString()
        val host = etSmtpHost.text.toString()
        val port = etSmtpPort.text.toString()
        if (toEmail.isEmpty() || fromEmail.isEmpty() || authCode.isEmpty()) {
            Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
            return@setOnClickListener
        }
        
        // 获取当前步数和使用时长
        val stepCount = getTodayStepCount()
        val appUsage = getAppUsageMinutes()
        
        // 构建测试邮件内容
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val content = StringBuilder()
        content.append("【安守】测试邮件\n")
        content.append("时间：$timestamp\n")
        content.append("================================\n\n")
        content.append("📱 今日使用时长：${appUsage}分钟\n")
        content.append("👣 今日步数：${stepCount}步\n")
        content.append("\n================================\n")
        content.append("这是一封来自安守应用的测试邮件，用于验证邮箱配置是否正确。\n")
        
        val mailSender = MailSender()
        mailSender.sendEmail(
            host = host,
            port = port,
            fromEmail = fromEmail,
            authCode = authCode,
            toEmail = toEmail,
            subject = "安守测试邮件 - $timestamp",
            content = content.toString(),
            callback = object : MailSender.SendCallback {
                override fun onSuccess() {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "测试邮件发送成功", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onError(error: String) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "发送失败：$error", Toast.LENGTH_LONG).show()
                    }
                }
            },
            context = this  // ✅ 添加这一行
        )
    }
        builder.setView(view)
        builder.setPositiveButton("保存") { _, _ ->
            val toEmail = etToEmail.text.toString()
            val fromEmail = etFromEmail.text.toString()
            val host = etSmtpHost.text.toString()
            val port = etSmtpPort.text.toString()
            
            // 如果授权码输入框为空，保留原有授权码，避免切换报警方式时丢失
            val securePrefs = SecurePrefsManager(this)
            val authCode = if (etAuthCode.text.toString().isEmpty()) {
                securePrefs.getEmailAuthCode() ?: ""
            } else {
                etAuthCode.text.toString()
            }
            
            prefsManager.setEmailTo(toEmail)
            prefsManager.setEmailSmtpHost(host)
            prefsManager.setEmailSmtpPort(port)
            prefsManager.setEmailEnabled(true)
            securePrefs.saveEmailCredentials(fromEmail, authCode)
            securePrefs.saveSmtpConfig(host, port)
            prefsManager.saveNotifyType("email")
            Toast.makeText(this, "邮件设置已保存", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("取消", null)
        builder.show()
    }

    
      private fun restartServicesBasedOnMode() {
        when (currentMode) {
            PrefsManager.MODE_GUARDIAN -> startAllServices()
            PrefsManager.MODE_RECEIVER -> {
                UnifiedNotificationService.start(this)
                EmailReceiverService.start(this)
            }
            PrefsManager.MODE_MIXED -> {
                startAllServices()
            }
            PrefsManager.MODE_COMMUNITY -> {
                startAllServices()
                CommunityReportService.start(this)
            }
        }
    }

    private fun startCommunityReportService() {
        val intent = Intent(this, CommunityReportService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Log.i(tag, "CommunityReportService 启动成功")
    }


    private fun startEmailReceiverService() {
        val intent = Intent(this, EmailReceiverService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    
    // ✅ 新增：检查首次启动
    private fun checkFirstLaunch() {
        if (!prefsManager.isSettingsGuideShown()) {
            // 延迟显示，确保 UI 已完全加载
            Handler(Looper.getMainLooper()).postDelayed({
                showSettingsGuide()
            }, 500)
        }
    }
    
    // ✅ 新增：显示设置引导
    private fun showSettingsGuide() {
        val intent = Intent(this, SettingsGuideActivity::class.java)
        startActivity(intent)
    }

    private fun checkPermissions() {
    if (!usageStatsHelper.hasUsageStatsPermission()) {
        showUsageStatsDialog()
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)
    }
    
    // ✅ 添加以下代码块（Android 12+ 精确 Alarm 权限）
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) {
            // 引导用户授权
            val intent = android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
            startActivity(android.content.Intent(intent))
        }
    }
    
    // ✅ 检查是否是首次启动，如果是则显示设置引导
    checkFirstLaunch()
}

    private fun showUsageStatsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("需要使用统计权限")
            .setMessage("为了检测您今天是否使用过手机，我们需要读取使用统计权限")
            .setPositiveButton("去设置") { _, _ ->
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton("稍后", null)
            .show()
    }
// 在 showSmartSettingsDialog() 方法的末尾部分：



private fun showSmartSettingsDialog() {
    val dialogView = layoutInflater.inflate(R.layout.dialog_smart_settings, null)
    val cardUsageSettings = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardUsageSettings)
    val cardStepSettings = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardStepSettings)
    val switchConfirmEnabled = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchConfirmEnabled)
    val tvDurationValue = dialogView.findViewById<TextView>(R.id.tvDurationValue)
    val btnDurationMinus = dialogView.findViewById<Button>(R.id.btnDurationMinus)
    val btnDurationPlus = dialogView.findViewById<Button>(R.id.btnDurationPlus)
    val cardSleepSettings = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardSleepSettings)
    
    // ✅ 报警时间设置控件
    val tvAlertHourValue = dialogView.findViewById<TextView>(R.id.tvAlertHourValue)
    val tvAlertMinuteValue = dialogView.findViewById<TextView>(R.id.tvAlertMinuteValue)
    val tvAlertTimeHint = dialogView.findViewById<TextView>(R.id.tvAlertTimeHint)
    val btnHourMinus = dialogView.findViewById<Button>(R.id.btnAlertHourMinus)
    val btnHourPlus = dialogView.findViewById<Button>(R.id.btnAlertHourPlus)
    val btnMinuteMinus = dialogView.findViewById<Button>(R.id.btnAlertMinuteMinus)
    val btnMinutePlus = dialogView.findViewById<Button>(R.id.btnAlertMinutePlus)
    
    // ✅ 设备使用统计控件（新增）
    //al tvBootTime = dialogView.findViewById<TextView>(R.id.tvBootTime)
    val tvAppUsage = dialogView.findViewById<TextView>(R.id.tvAppUsage)
    val cardAppUsage = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardAppUsage)
    
    // ✅ 步数统计控件
    val tvStepCount = dialogView.findViewById<TextView>(R.id.tvStepCount)
    val tvStepThreshold = dialogView.findViewById<TextView>(R.id.tvStepThreshold)
    val cardStepCount = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardStepCount)
    
    // ✅ 添加状态变量：是否显示相对数据（排除0点到睡觉前）
    var showRelativeData = false
    
    // ✅ 强制刷新步数，确保显示最新数据
    if (::systemStepManager.isInitialized) {
        systemStepManager.forceRefreshSteps()
        Log.d(tag, "打开智能设置 - 强制刷新步数：${systemStepManager.getTodaySteps()}")
    }
    
    // ✅ 初始显示步数
    updateStepCountDisplay(tvStepCount, tvStepThreshold)
    
    // ✅ 添加点击事件：切换显示绝对/相对数据（给整个 CardView 添加）
    val toggleDataDisplay = {
        showRelativeData = !showRelativeData
        updateDataDisplay(tvAppUsage, tvStepCount, tvStepThreshold, showRelativeData)
        
        val message = if (showRelativeData) {
            "已切换到相对数据模式\n（排除0点到睡觉前的数据）"
        } else {
            "已切换到绝对数据模式\n（显示从0点开始的累计数据）"
        }
        
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }
    
    cardAppUsage.setOnClickListener { toggleDataDisplay() }
    cardStepCount.setOnClickListener { toggleDataDisplay() }
    
    
    
    
    cardSleepSettings.setOnClickListener { showSleepSettingsDialog() }
    
    var alertDuration = prefsManager.getAlertDuration()
    tvDurationValue.text = alertDuration.toString()
    switchConfirmEnabled.isChecked = prefsManager.isAlertConfirmEnabled()
    
    // ✅ 无声音乐播放开关初始化
    val switchSilentMusic = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchSilentMusic)
    switchSilentMusic.isChecked = prefsManager.isSilentMusicEnabled()
    updateSilentMusicService() // 根据设置启动或停止服务
    
    // ✅ 辅助功能保活服务开关初始化
    val switchAccessibilityService = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchAccessibilityService)
    switchAccessibilityService.isChecked = prefsManager.isAccessibilityServiceEnabled()
    updateAccessibilityServiceState() // 根据设置更新状态
    
    cardUsageSettings.setOnClickListener { showUsageSettingsDialog() }
    cardStepSettings.setOnClickListener { showStepSettingsDialog() }
    
    // ✅ 无声音乐播放开关监听
    switchSilentMusic.setOnCheckedChangeListener { _, isChecked ->
        prefsManager.setSilentMusicEnabled(isChecked)
        updateSilentMusicService()
        Toast.makeText(
            this,
            if (isChecked) "后台保活增强已开启" else "后台保活增强已关闭",
            Toast.LENGTH_SHORT
        ).show()
    }
    
    // ✅ 辅助功能保活服务开关监听
    switchAccessibilityService.setOnCheckedChangeListener { _, isChecked ->
        prefsManager.setAccessibilityServiceEnabled(isChecked)
        if (isChecked) {
            // 打开辅助功能设置页面
            AccessibilityKeepAliveService().openAccessibilitySettings()
            Toast.makeText(this, "请在辅助功能设置中开启服务", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "辅助功能保活已关闭", Toast.LENGTH_SHORT).show()
        }
        updateAccessibilityServiceState()
    }
    
    btnDurationMinus.setOnClickListener {
        if (alertDuration > 1) {
            alertDuration--
            tvDurationValue.text = alertDuration.toString()
        }
    }
    
    btnDurationPlus.setOnClickListener {
        if (alertDuration < 30) {
            alertDuration++
            tvDurationValue.text = alertDuration.toString()
        }
    }
    
    // ✅ 报警时间设置逻辑
    var hour = prefsManager.getAlertCheckHour()
    var minute = prefsManager.getAlertCheckMinute()
    
    fun updateAlertTimeDisplay() {
        tvAlertHourValue.text = hour.toString()
        tvAlertMinuteValue.text = String.format("%02d", minute)
        tvAlertTimeHint.text = "当前设置：每天 ${hour}:${String.format("%02d", minute)} 检查"
    }
    
    updateAlertTimeDisplay()
    
    btnHourMinus.setOnClickListener {
        // 循环轮转：0 -> 23
        hour = if (hour > 0) hour - 1 else 23
        updateAlertTimeDisplay()
    }
    
    btnHourPlus.setOnClickListener {
        // 循环轮转：23 -> 0
        hour = if (hour < 23) hour + 1 else 0
        updateAlertTimeDisplay()
    }
    
    btnMinuteMinus.setOnClickListener {
        // 循环轮转：0 -> 55（步长为5）
        minute = if (minute >= 5) minute - 5 else 55
        updateAlertTimeDisplay()
    }
    
    btnMinutePlus.setOnClickListener {
        // 循环轮转：55 -> 0（步长为5）
        minute = if (minute < 55) minute + 5 else 0
        updateAlertTimeDisplay()
    }
    
    // ✅ 更新设备使用数据（新增）
    val usageThreshold = prefsManager.getAppUsageThreshold()
    val appUsage = usageStatsHelper.getTodayAppUsageMinutes()
    
    tvAppUsage.text = "${appUsage} 分钟"
    
    
    // ✅ 更新步数显示（再次刷新确保最新）
    val stepCount = getTodayStepCount()
    val stepThreshold = prefsManager.getStepThreshold()
    tvStepCount.text = "${stepCount} 步"
    tvStepThreshold.text = "目标：${stepThreshold} 步"
   
    
    
    
    // 根据完成情况设置颜色
    if (prefsManager.isStepMonitorEnabled()) {
        if (stepCount >= stepThreshold) {
            tvStepCount.setTextColor(getColor(R.color.success))
        } else {
            tvStepCount.setTextColor(getColor(R.color.error))
        }
    } else {
        tvStepCount.setTextColor(getColor(R.color.text_secondary))
    }
    
    // ✅ 设置自动报警模式 UI
    setupAutoAlertModeViews(dialogView)
    
    // ✅ 创建对话框并保存设置
    val dialog = AlertDialog.Builder(this)
        .setTitle("智能设置")
        .setView(dialogView)
        .setPositiveButton("保存") { _, _ ->
            prefsManager.setAlertConfirmEnabled(switchConfirmEnabled.isChecked)
            prefsManager.setAlertDuration(alertDuration)
            prefsManager.setAlertCheckTime(hour, minute)
            restartCheckinService()
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("取消", null)
        .create()
    
    dialog.show()
}

// ✅ 更新无声音乐播放服务状态
private fun updateSilentMusicService() {
    if (prefsManager.isSilentMusicEnabled()) {
        SilentMusicService.start(this)
        Log.d("MainActivity", "无声音乐播放服务已启动")
    } else {
        SilentMusicService.stop(this)
        Log.d("MainActivity", "无声音乐播放服务已停止")
    }
}

// ✅ 更新辅助功能服务状态
private fun updateAccessibilityServiceState() {
    val isEnabled = prefsManager.isAccessibilityServiceEnabled()
    val isActuallyEnabled = AccessibilityKeepAliveService.isEnabled(this)
    
    // 更新开关状态（以实际状态为准）
    Log.d("MainActivity", "辅助功能服务状态：设置=$isEnabled, 实际=$isActuallyEnabled")
}

// e:\GongZuoTai\HuoZheNe\app\src\main\java\com\livewell\MainActivity.kt

// ✅ 在 showSmartSettingsDialog() 方法之后添加此方法
private fun setupUsageThresholdDialog() {
    val dialogView = layoutInflater.inflate(R.layout.dialog_usage_settings, null)
    val switchSmartMode = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchSmartMode)
    //val switchPureUsageMode = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchPureUsageMode)
    val cardThresholds = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardThresholds)
    val tvAppUsageValue = dialogView.findViewById<TextView>(R.id.tvAppUsageValue)
    val btnAppUsageMinus = dialogView.findViewById<Button>(R.id.btnAppUsageMinus)
    val btnAppUsagePlus = dialogView.findViewById<Button>(R.id.btnAppUsagePlus)
    
    var appUsageThreshold = prefsManager.getAppUsageThreshold()
    
    tvAppUsageValue.text = appUsageThreshold.toString()
    switchSmartMode.isChecked = prefsManager.isSmartModeEnabled()
    //switchPureUsageMode.isChecked = prefsManager.isPureUsageMode()
    
    cardThresholds.alpha = if (switchSmartMode.isChecked) 0.5f else 1.0f
    cardThresholds.isEnabled = !switchSmartMode.isChecked
    
    switchSmartMode.setOnCheckedChangeListener { _, isChecked ->
        cardThresholds.alpha = if (isChecked) 0.5f else 1.0f
        cardThresholds.isEnabled = !isChecked
    }
    
    btnAppUsageMinus.setOnClickListener {
        if (appUsageThreshold > 1) {
            appUsageThreshold--
            tvAppUsageValue.text = appUsageThreshold.toString()
        }
    }
    
    btnAppUsagePlus.setOnClickListener {
        if (appUsageThreshold < 120) {
            appUsageThreshold++
            tvAppUsageValue.text = appUsageThreshold.toString()
        }
    }
    
    AlertDialog.Builder(this)
        .setTitle("设备使用时长设置")
        .setView(dialogView)
        .setPositiveButton("保存") { _, _ ->
            prefsManager.setSmartModeEnabled(switchSmartMode.isChecked)
            //prefsManager.setPureUsageMode(switchPureUsageMode.isChecked)
            prefsManager.setAppUsageThreshold(appUsageThreshold)
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("取消", null)
        .show()
}

//  ✅ 使用时长设置对话框 - 简化为开关控制
private fun showUsageSettingsDialog() {
    val dialogView = layoutInflater.inflate(R.layout.dialog_usage_settings, null)
    val switchUsageMonitor = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchSmartMode)  // ID保持不变
    val cardThresholds = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardThresholds)
    val tvAppUsageValue = dialogView.findViewById<TextView>(R.id.tvAppUsageValue)
    val btnAppUsageMinus = dialogView.findViewById<Button>(R.id.btnAppUsageMinus)
    val btnAppUsagePlus = dialogView.findViewById<Button>(R.id.btnAppUsagePlus)
    
    var appUsageThreshold = prefsManager.getAppUsageThreshold()
    
    tvAppUsageValue.text = appUsageThreshold.toString()
    
    // ✅ 默认开启使用时长监测
    switchUsageMonitor.isChecked = prefsManager.isSmartModeEnabled()  // 复用 smartMode 字段作为开关
    
    // ✅ 根据开关状态启用/禁用阈值设置
    cardThresholds.alpha = if (switchUsageMonitor.isChecked) 1.0f else 0.5f
    cardThresholds.isEnabled = switchUsageMonitor.isChecked
    
    switchUsageMonitor.setOnCheckedChangeListener { _, isChecked ->
        cardThresholds.alpha = if (isChecked) 1.0f else 0.5f
        cardThresholds.isEnabled = isChecked
    }
    
    btnAppUsageMinus.setOnClickListener {
        if (appUsageThreshold > 1) {
            appUsageThreshold--
            tvAppUsageValue.text = appUsageThreshold.toString()
        }
    }
    
    btnAppUsagePlus.setOnClickListener {
        if (appUsageThreshold < 120) {
            appUsageThreshold++
            tvAppUsageValue.text = appUsageThreshold.toString()
        }
    }
    
    AlertDialog.Builder(this)
        .setTitle("设备使用时长设置")
        .setView(dialogView)
        .setPositiveButton("保存") { _, _ ->
            // ✅ 保存开关状态和阈值
            prefsManager.setSmartModeEnabled(switchUsageMonitor.isChecked)
            prefsManager.setAppUsageThreshold(appUsageThreshold)
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("取消", null)
        .show()
}



    private fun showStepSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_step_settings, null)
        val switchStepMonitor = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchStepMonitor)
        val cardStepThreshold = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardStepThreshold)
        val tvStepValue = dialogView.findViewById<TextView>(R.id.tvStepValue)
        val btnStepMinus = dialogView.findViewById<Button>(R.id.btnStepMinus)
        val btnStepPlus = dialogView.findViewById<Button>(R.id.btnStepPlus)
        
        var stepThreshold = prefsManager.getStepThreshold()
        tvStepValue.text = stepThreshold.toString()
        switchStepMonitor.isChecked = prefsManager.isStepMonitorEnabled()
        
        cardStepThreshold.alpha = if (switchStepMonitor.isChecked) 1.0f else 0.5f
        cardStepThreshold.isEnabled = switchStepMonitor.isChecked
        
        switchStepMonitor.setOnCheckedChangeListener { _, isChecked ->
            cardStepThreshold.alpha = if (isChecked) 1.0f else 0.5f
            cardStepThreshold.isEnabled = isChecked
        }
        
        btnStepMinus.setOnClickListener {
            if (stepThreshold > 10) {
                stepThreshold -= 10
                tvStepValue.text = stepThreshold.toString()
            }
        }
        btnStepPlus.setOnClickListener {
            if (stepThreshold < 1000) {
                stepThreshold += 10
                tvStepValue.text = stepThreshold.toString()
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("运动监测设置")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                prefsManager.setStepMonitorEnabled(switchStepMonitor.isChecked)
                prefsManager.setStepThreshold(stepThreshold)
                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSleepSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_sleep_settings, null)
        val switchSleepMonitor = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchSleepMonitor)
        val cardSleepMode = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardSleepMode)
        val cardWakeTime = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardWakeTime)
        val cardThresholds = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardThresholds)
        val radioPowerSaving = dialogView.findViewById<RadioButton>(R.id.radioPowerSaving)
        val radioBalanced = dialogView.findViewById<RadioButton>(R.id.radioBalanced)
        val tvModeDescription = dialogView.findViewById<TextView>(R.id.tvModeDescription)
        val layoutGyro = dialogView.findViewById<LinearLayout>(R.id.layoutGyro)
        val switchGyro = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchGyro)
        val layoutAccelThreshold = dialogView.findViewById<LinearLayout>(R.id.layoutAccelThreshold)
        val tvInactiveValue = dialogView.findViewById<TextView>(R.id.tvInactiveValue)
        val tvStepValue = dialogView.findViewById<TextView>(R.id.tvStepValue)
        val tvAccelValue = dialogView.findViewById<TextView>(R.id.tvAccelValue)
        val btnInactiveMinus = dialogView.findViewById<Button>(R.id.btnInactiveMinus)
        val btnInactivePlus = dialogView.findViewById<Button>(R.id.btnInactivePlus)
        val btnStepMinus = dialogView.findViewById<Button>(R.id.btnStepMinus)
        val btnStepPlus = dialogView.findViewById<Button>(R.id.btnStepPlus)
        val btnAccelMinus = dialogView.findViewById<Button>(R.id.btnAccelMinus)
        val btnAccelPlus = dialogView.findViewById<Button>(R.id.btnAccelPlus)
        val tvAvgSleepTime = dialogView.findViewById<TextView>(R.id.tvAvgSleepTime)
        val btnViewHistory = dialogView.findViewById<Button>(R.id.btnViewHistory)
        
        val isEnabled = prefsManager.isSleepMonitorEnabled()
        val sleepMode = prefsManager.getSleepMode()
        val isGyroEnabled = prefsManager.isGyroEnabled()
        
        switchSleepMonitor.isChecked = isEnabled
        
        if (sleepMode == "power_saving") {
            radioPowerSaving.isChecked = true
            tvModeDescription.text = "省电模式：仅使用屏幕和步数判定，功耗<1%"
            layoutGyro.visibility = View.GONE
            layoutAccelThreshold.visibility = View.GONE
        } else {
            radioBalanced.isChecked = true
            tvModeDescription.text = "均衡模式：使用加速度计检测体动，精准度更高"
            layoutGyro.visibility = View.VISIBLE
            layoutAccelThreshold.visibility = View.VISIBLE
        }
        
        switchGyro.isChecked = isGyroEnabled
        
        fun updateControlsEnabled(enabled: Boolean) {
            cardSleepMode.isEnabled = enabled
            cardWakeTime.isEnabled = enabled
            cardThresholds.isEnabled = enabled
            cardSleepMode.alpha = if (enabled) 1.0f else 0.5f
            cardWakeTime.alpha = if (enabled) 1.0f else 0.5f
            cardThresholds.alpha = if (enabled) 1.0f else 0.5f
        }
        
        updateControlsEnabled(isEnabled)
        
        switchSleepMonitor.setOnCheckedChangeListener { _, isChecked ->
            updateControlsEnabled(isChecked)
            if (!isChecked) {
                stopService(Intent(this, SleepMonitorService::class.java))
            }
        }
        
        radioBalanced.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                tvModeDescription.text = "均衡模式：使用加速度计检测体动，精准度更高"
                layoutGyro.visibility = View.VISIBLE
                layoutAccelThreshold.visibility = View.VISIBLE
            }
        }
        
        radioPowerSaving.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                tvModeDescription.text = "省电模式：仅使用屏幕和步数判定，功耗<1%"
                layoutGyro.visibility = View.GONE
                layoutAccelThreshold.visibility = View.GONE
            }
        }
        
        // ✅ 点击起床时间卡片打开子界面
        cardWakeTime.setOnClickListener {
            if (switchSleepMonitor.isChecked) {
                showWakeTimeSettingsDialog()
            }
        }
        
        var inactiveThreshold = prefsManager.getInactiveThreshold()
        var stepThreshold = prefsManager.getSleepStepThreshold()
        var accelThreshold = prefsManager.getAccelThreshold()
        
        tvInactiveValue.text = inactiveThreshold.toString()
        tvStepValue.text = stepThreshold.toString()
        tvAccelValue.text = String.format("%.1f", accelThreshold)
        
        btnInactiveMinus.setOnClickListener {
            if (inactiveThreshold > 5) {
                inactiveThreshold -= 5
                tvInactiveValue.text = inactiveThreshold.toString()
            }
        }
        btnInactivePlus.setOnClickListener {
            if (inactiveThreshold < 120) {
                inactiveThreshold += 5
                tvInactiveValue.text = inactiveThreshold.toString()
            }
        }
        btnStepMinus.setOnClickListener {
            if (stepThreshold > 1) {
                stepThreshold--
                tvStepValue.text = stepThreshold.toString()
            }
        }
        btnStepPlus.setOnClickListener {
            if (stepThreshold < 50) {
                stepThreshold++
                tvStepValue.text = stepThreshold.toString()
            }
        }
        btnAccelMinus.setOnClickListener {
            if (accelThreshold > 0.5f) {
                accelThreshold = (accelThreshold * 10 - 1) / 10
                tvAccelValue.text = String.format("%.1f", accelThreshold)
            }
        }
        btnAccelPlus.setOnClickListener {
            if (accelThreshold < 5.0f) {
                accelThreshold = (accelThreshold * 10 + 1) / 10
                tvAccelValue.text = String.format("%.1f", accelThreshold)
            }
        }
        
        val avgSleepTime = prefsManager.getAverageSleepTime()
        val avgHours = avgSleepTime / (1000 * 60 * 60)
        val avgMinutes = (avgSleepTime % (1000 * 60 * 60)) / (1000 * 60)
        tvAvgSleepTime.text = String.format("平均睡眠时长：%d 小时%d 分钟", avgHours, avgMinutes)
        
        btnViewHistory.setOnClickListener { showSleepHistoryDialog() }
        
        AlertDialog.Builder(this)
            .setTitle("睡眠监测设置")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val enabled = switchSleepMonitor.isChecked
                prefsManager.setSleepMonitorEnabled(enabled)
                val mode = if (radioBalanced.isChecked) "balanced" else "power_saving"
                prefsManager.setSleepMode(mode)
                prefsManager.setGyroEnabled(switchGyro.isChecked)
                prefsManager.setInactiveThreshold(inactiveThreshold)
                prefsManager.setSleepStepThreshold(stepThreshold)
                prefsManager.setAccelThreshold(accelThreshold)
                
                Toast.makeText(
                    this, 
                    "设置已保存", 
                    Toast.LENGTH_SHORT
                ).show()
                
                if (enabled) {
                    startService(Intent(this, SleepMonitorService::class.java))
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSleepHistoryDialog() {
        val history = prefsManager.getSleepHistory()
        android.util.Log.i("SleepHistory", "====== 开始显示睡眠历史 ======")
        android.util.Log.i("SleepHistory", "原始数据：$history")
        
        val lines = history.split("\n").takeLast(10).reversed()
        if (lines.isEmpty() || lines[0].isEmpty()) {
            Toast.makeText(this, "暂无睡眠历史数据", Toast.LENGTH_SHORT).show()
            return
        }
        val sb = StringBuilder()
        sb.append("最近睡眠记录\n\n")
        for ((index, line) in lines.withIndex()) {
            android.util.Log.d("SleepHistory", "处理第 ${index + 1} 条记录：$line")
            
            val parts = line.split(",")
            android.util.Log.d("SleepHistory", "  分割后字段数：${parts.size}")
            if (parts.size >= 6) {
                val date = parts[0]
                val sleepTimeStr = parts[4]
                val wakeTimeStr = parts[5]
                val durationMs = parts[3].toLongOrNull() ?: 0L
                val durationMinutes = durationMs / (1000 * 60)
                
                android.util.Log.d("SleepHistory", "  日期：$date")
                android.util.Log.d("SleepHistory", "  入睡时间：$sleepTimeStr")
                android.util.Log.d("SleepHistory", "  醒来时间：$wakeTimeStr")
                android.util.Log.d("SleepHistory", "  时长：$durationMinutes 分钟")
                
                sb.append("$date  $sleepTimeStr-$wakeTimeStr  ${durationMinutes}分钟\n")
            } else {
                android.util.Log.w("SleepHistory", "  ⚠️ 字段数不足，跳过此条记录")
            }
        }
        
        android.util.Log.i("SleepHistory", "最终显示内容：\n${sb.toString()}")
        
        AlertDialog.Builder(this)
            .setTitle("睡眠历史")
            .setMessage(sb.toString())
            .setPositiveButton("确定", null)
            .show()
    }

    private fun setupTitleClickListener() {
        // ✅ 由于使用了 Fragment，cardHeader 在 HomeFragment 中
        // 这里不再需要处理点击事件
    }

        internal fun showDeveloperDialog() {
        try {
            if (!::prefsManager.isInitialized) {
                prefsManager = PrefsManager(this)
            }
            val dialogView = layoutInflater.inflate(R.layout.dialog_developer, null)
            val radioGroupScenario = dialogView.findViewById<RadioGroup>(R.id.radioGroupScenario)
            val radioGroupSign = dialogView.findViewById<RadioGroup>(R.id.radioGroupSign)
            val etBootTime = dialogView.findViewById<EditText>(R.id.etBootTime)
            val etAppUsage = dialogView.findViewById<EditText>(R.id.etAppUsage)
            val etStepCount = dialogView.findViewById<EditText>(R.id.etStepCount)
            val etWakeHour = dialogView.findViewById<EditText>(R.id.etWakeHour)
            val etWakeMinute = dialogView.findViewById<EditText>(R.id.etWakeMinute)
            val btnTestSms = dialogView.findViewById<Button>(R.id.btnTestSms)
            val btnTestEmail = dialogView.findViewById<Button>(R.id.btnTestEmail)
            val btnTriggerAlert = dialogView.findViewById<Button>(R.id.btnTriggerAlert)
            val btnReset = dialogView.findViewById<Button>(R.id.btnReset)
            val tvTimerValue = dialogView.findViewById<TextView>(R.id.tvTimerValue)
            val btnStartTimer = dialogView.findViewById<Button>(R.id.btnStartTimer)
            val btnStopTimer = dialogView.findViewById<Button>(R.id.btnStopTimer)
            val btnResetTimer = dialogView.findViewById<Button>(R.id.btnResetTimer)
            val switchDeveloperTestEmail = dialogView.findViewById<SwitchCompat>(R.id.switchDeveloperTestEmail)
            val tvDeveloperLastTestEmailTime = dialogView.findViewById<TextView>(R.id.tvDeveloperLastTestEmailTime)
            val btnSendSimulatedAlert = dialogView.findViewById<Button>(R.id.btnSendSimulatedAlert)
            val btnImmediateReport = dialogView.findViewById<Button>(R.id.btnImmediateReport)
            
            if (tvTimerValue == null) {
                Toast.makeText(this, "布局文件缺少 tvTimerValue 控件", Toast.LENGTH_SHORT).show()
                return
            }
            
            updateTimerDisplay(tvTimerValue)
            
            timerUpdateHandler = Handler(Looper.getMainLooper())
            timerUpdateRunnable = object : Runnable {
                override fun run() {
                    try {
                        if (this@MainActivity::dialog.isInitialized && dialog.isShowing) {
                            updateTimerDisplay(tvTimerValue)
                            timerUpdateHandler?.postDelayed(this, 1000)
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "定时器更新错误", e)
                    }
                }
            }
            timerUpdateRunnable?.let { timerUpdateHandler?.post(it) }
            
            btnStartTimer.setOnClickListener {
                try {
                    if (!prefsManager.isTimerRunning()) {
                        startBackgroundTimerService()
                        updateTimerDisplay(tvTimerValue)
                        Toast.makeText(this, "后台计时器已启动", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "计时器已在运行中", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "启动计时器错误", e)
                    Toast.makeText(this, "启动失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            
            btnStopTimer.setOnClickListener {
                try {
                    if (prefsManager.isTimerRunning()) {
                        stopBackgroundTimerService()
                        updateTimerDisplay(tvTimerValue)
                        Toast.makeText(this, "后台计时器已停止", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "计时器未运行", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "停止计时器错误", e)
                    Toast.makeText(this, "停止失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            
            btnResetTimer.setOnClickListener {
                try {
                    prefsManager.resetBackgroundTimer()
                    updateTimerDisplay(tvTimerValue)
                    Toast.makeText(this, "计时器已重置", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("MainActivity", "重置计时器错误", e)
                    Toast.makeText(this, "重置失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            
            radioGroupScenario.setOnCheckedChangeListener { _, checkedId ->
                try {
                    when (checkedId) {
                        R.id.radioNormal -> {
                            radioGroupSign.check(R.id.radioSigned)
                            etBootTime.setText("12")
                            etAppUsage.setText("60")
                            etStepCount.setText("2000")
                            etWakeHour.setText("7")
                            etWakeMinute.setText("0")
                        }
                        R.id.radioNoSign -> {
                            radioGroupSign.check(R.id.radioNotSigned)
                            etBootTime.setText("0.5")
                            etAppUsage.setText("3")
                            etStepCount.setText("500")
                            etWakeHour.setText("10")
                            etWakeMinute.setText("30")
                        }
                        R.id.radioLowUsage -> {
                            radioGroupSign.check(R.id.radioSigned)
                            etBootTime.setText("0.5")
                            etAppUsage.setText("3")
                            etStepCount.setText("2000")
                            etWakeHour.setText("7")
                            etWakeMinute.setText("0")
                        }
                        R.id.radioLowStep -> {
                            radioGroupSign.check(R.id.radioSigned)
                            etBootTime.setText("12")
                            etAppUsage.setText("60")
                            etStepCount.setText("30")
                            etWakeHour.setText("7")
                            etWakeMinute.setText("0")
                        }
                        R.id.radioSleepAbnormal -> {
                            radioGroupSign.check(R.id.radioSigned)
                            etBootTime.setText("12")
                            etAppUsage.setText("60")
                            etStepCount.setText("100")
                            etWakeHour.setText("11")
                            etWakeMinute.setText("30")
                        }
                        R.id.radioMixed -> {
                            radioGroupSign.check(R.id.radioNotSigned)
                            etBootTime.setText("0.3")
                            etAppUsage.setText("2")
                            etStepCount.setText("20")
                            etWakeHour.setText("12")
                            etWakeMinute.setText("0")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "场景选择错误", e)
                }
            }
            
            btnTestSms.setOnClickListener {
                try {
                    val report = generateTestReport(
                        isSigned = radioGroupSign.checkedRadioButtonId == R.id.radioSigned,
                        appUsage = etAppUsage.text.toString().toIntOrNull() ?: 0,
                        stepCount = etStepCount.text.toString().toIntOrNull() ?: 0,
                        wakeHour = etWakeHour.text.toString().toIntOrNull() ?: 7,
                        wakeMinute = etWakeMinute.text.toString().toIntOrNull() ?: 0
                    )
                    
                    val contact = prefsManager.getEmergencyContact()
                    if (!contact.isNullOrEmpty()) {
                        sendTestSms(contact, report)
                        Toast.makeText(this, "测试短信已发送", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "请先设置紧急联系人", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "测试短信错误", e)
                    Toast.makeText(this, "短信发送失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            
            btnTestEmail.setOnClickListener {
                try {
                    val report = generateTestReport(
                        isSigned = radioGroupSign.checkedRadioButtonId == R.id.radioSigned,
                        appUsage = etAppUsage.text.toString().toIntOrNull() ?: 0,
                        stepCount = etStepCount.text.toString().toIntOrNull() ?: 0,
                        wakeHour = etWakeHour.text.toString().toIntOrNull() ?: 7,
                        wakeMinute = etWakeMinute.text.toString().toIntOrNull() ?: 0
                    )
                    sendTestEmail(report)
                } catch (e: Exception) {
                    Log.e("MainActivity", "测试邮件错误", e)
                    Toast.makeText(this, "邮件发送失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            // ✅ 发送模拟警报到社区后端
            btnSendSimulatedAlert.setOnClickListener {
                try {
                    val reason = buildTestAlertReason(
                        radioGroupSign.checkedRadioButtonId == R.id.radioSigned,
                        etAppUsage.text.toString().toIntOrNull() ?: 0,
                        etStepCount.text.toString().toIntOrNull() ?: 0
                    )
                    sendSimulatedAlertToCommunity(reason)
                    Toast.makeText(this, "模拟警报已发送到社区后端", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("MainActivity", "发送模拟警报错误", e)
                    Toast.makeText(this, "发送失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            // ✅ 立即上报完整健康数据到社区后端
            btnImmediateReport.setOnClickListener {
                try {
                    sendImmediateHealthDataToCommunity()
                    Toast.makeText(this, "健康数据已上报到社区后端", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("MainActivity", "上报健康数据错误", e)
                    Toast.makeText(this, "上报失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            // ✅ 新增：开发者测试界面的后台保活测试邮件控制
            if (switchDeveloperTestEmail != null && tvDeveloperLastTestEmailTime != null) {
                val layoutInterval = dialogView.findViewById<LinearLayout>(R.id.layoutTestEmailInterval)
                val etInterval = dialogView.findViewById<EditText>(R.id.etTestEmailInterval)
                val btnSetInterval = dialogView.findViewById<Button>(R.id.btnSetInterval)
                val btnInterval5 = dialogView.findViewById<Button>(R.id.btnInterval5)
                val btnInterval15 = dialogView.findViewById<Button>(R.id.btnInterval15)
                val btnInterval30 = dialogView.findViewById<Button>(R.id.btnInterval30)
                val btnInterval60 = dialogView.findViewById<Button>(R.id.btnInterval60)
                val btnInterval120 = dialogView.findViewById<Button>(R.id.btnInterval120)
                
                // 初始化显示
                switchDeveloperTestEmail.isChecked = prefsManager.isTestEmailEnabled()
                updateDeveloperLastTestEmailTime(tvDeveloperLastTestEmailTime)
                
                // 显示/隐藏时间间隔设置
                if (switchDeveloperTestEmail.isChecked) {
                    layoutInterval.visibility = View.VISIBLE
                    etInterval.setText(prefsManager.getTestEmailInterval().toString())
                } else {
                    layoutInterval.visibility = View.GONE
                }
                
                switchDeveloperTestEmail.setOnCheckedChangeListener { _, isChecked ->
                    prefsManager.setTestEmailEnabled(isChecked)
                    
                    // 显示/隐藏时间间隔设置
                    layoutInterval.visibility = if (isChecked) View.VISIBLE else View.GONE
                    
                    if (isChecked) {
                        // 重启服务以应用新的间隔
                        TestEmailService.stop(this@MainActivity)
                        Handler(Looper.getMainLooper()).postDelayed({
                            TestEmailService.start(this@MainActivity)
                        }, 500)
                        
                        Snackbar.make(
                            dialogView,
                            "测试邮件已开启，每 ${prefsManager.getTestEmailInterval()} 分钟发送一次",
                            Snackbar.LENGTH_LONG
                        ).show()
                    } else {
                        TestEmailService.stop(this@MainActivity)
                        Snackbar.make(
                            dialogView,
                            "测试邮件已关闭",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
                
                // 设置按钮点击事件
                btnSetInterval.setOnClickListener {
                    val intervalStr = etInterval.text.toString().trim()
                    if (intervalStr.isEmpty()) {
                        etInterval.error = "请输入时间间隔"
                        return@setOnClickListener
                    }
                    
                    var interval = intervalStr.toIntOrNull()
                    if (interval == null) {
                        etInterval.error = "请输入有效数字"
                        return@setOnClickListener
                    }
                    
                    // 限制范围
                    if (interval < 1) {
                        interval = 1
                        etInterval.setText("1")
                        Snackbar.make(dialogView, "最小间隔为 1 分钟", Snackbar.LENGTH_SHORT).show()
                    } else if (interval > 1440) {
                        interval = 1440
                        etInterval.setText("1440")
                        Snackbar.make(dialogView, "最大间隔为 1440 分钟（24 小时）", Snackbar.LENGTH_SHORT).show()
                    }
                    
                    prefsManager.setTestEmailInterval(interval)
                    Snackbar.make(dialogView, "已设置发送间隔为 $interval 分钟", Snackbar.LENGTH_SHORT).show()
                    
                    // 如果服务正在运行，重启以应用新间隔
                    if (switchDeveloperTestEmail.isChecked) {
                        TestEmailService.stop(this@MainActivity)
                        Handler(Looper.getMainLooper()).postDelayed({
                            TestEmailService.start(this@MainActivity)
                        }, 500)
                    }
                }
                
                // 快速预设按钮
                btnInterval5.setOnClickListener {
                    etInterval.setText("5")
                    btnSetInterval.callOnClick()
                }
                
                btnInterval15.setOnClickListener {
                    etInterval.setText("15")
                    btnSetInterval.callOnClick()
                }
                
                btnInterval30.setOnClickListener {
                    etInterval.setText("30")
                    btnSetInterval.callOnClick()
                }
                
                btnInterval60.setOnClickListener {
                    etInterval.setText("60")
                    btnSetInterval.callOnClick()
                }
                
                btnInterval120.setOnClickListener {
                    etInterval.setText("120")
                    btnSetInterval.callOnClick()
                }
            }
            
            btnTriggerAlert.setOnClickListener {
                try {
                    saveTestData(
                        isSigned = radioGroupSign.checkedRadioButtonId == R.id.radioSigned,
                        appUsage = etAppUsage.text.toString().toIntOrNull() ?: 0,
                        stepCount = etStepCount.text.toString().toIntOrNull() ?: 0,
                        wakeHour = etWakeHour.text.toString().toIntOrNull() ?: 7,
                        wakeMinute = etWakeMinute.text.toString().toIntOrNull() ?: 0
                    )
                    val intent = Intent(this, CheckinService::class.java).apply {
                        action = "TRIGGER_TEST_ALERT"
                        putExtra("test_data", true)
                    }
                    startService(intent)
                    Toast.makeText(this, "已触发测试警报", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("MainActivity", "触发警报错误", e)
                    Toast.makeText(this, "触发失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            
            btnReset.setOnClickListener {
                try {
                    radioGroupScenario.check(R.id.radioNormal)
                } catch (e: Exception) {
                    Log.e("MainActivity", "重置错误", e)
                }
            }
            
            // ✅ 新增：重启所有服务按钮
            val btnRestartAllServices = dialogView.findViewById<Button>(R.id.btnRestartAllServices)
            btnRestartAllServices.setOnClickListener {
                restartAllServices()
            }

            // ✅ 新增：清除今日警报记录按钮
            val btnClearAlertRecord = dialogView.findViewById<Button>(R.id.btnClearAlertRecord)
            btnClearAlertRecord.setOnClickListener {
                clearTodayAlertRecord()
            }

            // ✅ 新增：查看日志按钮
            val btnViewLogs = dialogView.findViewById<Button>(R.id.btnViewLogs)
            btnViewLogs.setOnClickListener {
                showLogsDialog()
            }
            
            // ✅ 新增：查看睡眠快照记录按钮
            val btnViewSleepSnapshots = dialogView.findViewById<Button>(R.id.btnViewSleepSnapshots)
            btnViewSleepSnapshots.setOnClickListener {
                showSleepSnapshotsDialog()
            }
            
            // ✅ 新增：无声音乐频率测试相关按钮
            setupSilentMusicFrequencyTest(dialogView)

            AlertDialog.Builder(this)
                .setTitle("开发者测试界面")
                .setView(dialogView)
                .setPositiveButton("关闭") { _, _ ->
                    // 停止后台计时器更新
                    timerUpdateRunnable?.let {
                        timerUpdateHandler?.removeCallbacks(it)
                    }
                }
                .setOnDismissListener {
                    // 对话框 dismissed 时也停止定时器（防止内存泄漏）
                    timerUpdateRunnable?.let {
                        timerUpdateHandler?.removeCallbacks(it)
                    }
                }
                .show()
        } catch (e: Exception) {
            Log.e("MainActivity", "显示开发者对话框错误", e)
            Toast.makeText(this, "对话框打开失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateTimerDisplay(textView: TextView) {
        val totalSeconds = prefsManager.getBackgroundTotalTime()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        textView.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun startBackgroundTimerService() {
        val intent = Intent(this, BackgroundTimerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopBackgroundTimerService() {
        val intent = Intent(this, BackgroundTimerService::class.java)
        stopService(intent)
    }

    private fun generateTestReport(
    isSigned: Boolean, appUsage: Int, stepCount: Int,
    wakeHour: Int, wakeMinute: Int
): String {
    val thresholds = getThresholds()
    val report = StringBuilder()
    report.append("【安守】测试报告\n")
    report.append("时间：${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}\n")
    report.append("================================\n\n")
    report.append("📅 签到状态：${if (isSigned) "已签到 ✅" else "未签到 ❌"}\n")
    // ✅ 删除开机时间行
    report.append("📱 今日使用：${appUsage}分钟 (阈值${thresholds.first}分钟) ${if (appUsage < thresholds.first) "⚠️" else "✓"}\n")
    report.append("👣 今日步数：${stepCount}步 (阈值${thresholds.second}步) ${if (stepCount < thresholds.second) "⚠️" else "✓"}\n")
    report.append("⏰ 起床时间：${wakeHour}:${String.format("%02d", wakeMinute)}\n")
    report.append("\n================================\n")
    report.append("此为测试数据，非真实情况")
    return report.toString()
}

    private fun getThresholds(): Pair<Int, Int> {
    return Pair(
        prefsManager.getAppUsageThreshold(),
        prefsManager.getStepThreshold()
    )
}

    private fun saveTestData(
    isSigned: Boolean, appUsage: Int, stepCount: Int,
    wakeHour: Int, wakeMinute: Int
) {
    prefsManager.setTestData(
        isSigned = isSigned, bootTime = 0f, appUsage = appUsage,
        stepCount = stepCount, wakeHour = wakeHour, wakeMinute = wakeMinute
    )
} 

    private fun sendTestSms(phoneNumber: String, report: String) {
        try {
            val smsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$phoneNumber")).apply {
                putExtra("sms_body", report)
            }
            startActivity(smsIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "短信发送失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

/**
 * 显示起床时间和阈值设置子界面
 */
private fun showWakeTimeSettingsDialog() {
    val dialogView = layoutInflater.inflate(R.layout.dialog_wake_time_settings, null)
    
    val subHourPicker = dialogView.findViewById<NumberPicker>(R.id.numberPickerHour)
    val subMinutePicker = dialogView.findViewById<NumberPicker>(R.id.numberPickerMinute)
    val subTolerancePicker = dialogView.findViewById<NumberPicker>(R.id.numberPickerTolerance)
    val tvCurrentSettings = dialogView.findViewById<TextView>(R.id.tvCurrentSettings)
    
    // 初始化值
    subHourPicker.minValue = 0
    subHourPicker.maxValue = 23
    subHourPicker.value = prefsManager.getWakeUpHour()
    subHourPicker.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
    
    subMinutePicker.minValue = 0
    subMinutePicker.maxValue = 59
    subMinutePicker.value = prefsManager.getWakeUpMinute()
    subMinutePicker.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
    
    subTolerancePicker.minValue = 1
    subTolerancePicker.maxValue = 6
    subTolerancePicker.value = prefsManager.getWakeUpToleranceHours()
    subTolerancePicker.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
    
    // 更新当前设置显示
    fun updateCurrentSettingsDisplay() {
        val hour = String.format("%02d", subHourPicker.value)
        val minute = String.format("%02d", subMinutePicker.value)
        val tolerance = subTolerancePicker.value
        tvCurrentSettings.text = "当前设置：$hour:$minute 起床，容忍 $tolerance 小时"
    }
    
    updateCurrentSettingsDisplay()
    
    // 监听值变化
    subHourPicker.setOnValueChangedListener { _, _, _ -> updateCurrentSettingsDisplay() }
    subMinutePicker.setOnValueChangedListener { _, _, _ -> updateCurrentSettingsDisplay() }
    subTolerancePicker.setOnValueChangedListener { _, _, _ -> updateCurrentSettingsDisplay() }
    
    // 保存按钮
    AlertDialog.Builder(this)
        .setTitle("起床时间设置")
        .setView(dialogView)
        .setPositiveButton("保存") { _, _ ->
            // 保存到 PrefsManager
            prefsManager.setWakeUpHour(subHourPicker.value)
            prefsManager.setWakeUpMinute(subMinutePicker.value)
            prefsManager.setWakeUpToleranceHours(subTolerancePicker.value)
            
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("取消", null)
        .show()
}

private fun sendTestEmail(report: String) {
    val securePrefs = SecurePrefsManager(this)
    val fromEmail = securePrefs.getEmailAccount()
    val authCode = securePrefs.getEmailAuthCode()
    val host = securePrefs.getSmtpHost() ?: prefsManager.getEmailSmtpHost()
    val port = securePrefs.getSmtpPort() ?: prefsManager.getEmailSmtpPort()
    val toEmail = prefsManager.getEmailTo()
    
    if (fromEmail.isNullOrEmpty() || authCode.isNullOrEmpty() || toEmail.isNullOrEmpty()) {
        Toast.makeText(this, "邮件配置不完整，请先在设置中配置发件邮箱", Toast.LENGTH_LONG).show()
        return
    }
    
    Toast.makeText(this, "正在后台发送测试邮件...", Toast.LENGTH_SHORT).show()
    
    val mailSender = MailSender()
    mailSender.sendEmail(
        host = host,
        port = port,
        fromEmail = fromEmail,
        authCode = authCode,
        toEmail = toEmail,
        subject = "【安守】测试报告",
        content = report,
        callback = object : MailSender.SendCallback {
            override fun onSuccess() {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "测试邮件发送成功", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onError(error: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "发送失败：$error", Toast.LENGTH_LONG).show()
                }
            }
        },
        context = this
    )
}

    private fun checkAndGuideKeepAlive() {
        // ✅ 如果已完成设置引导，则检查保活状态
        if (prefsManager.isSettingsGuideShown()) {
            checkKeepAliveStatus()
            return
        }
        
        // ✅ 如果已请求过电池优化设置，也不再显示引导
        if (prefsManager.isBatteryOptimizationRequested()) {
            checkKeepAliveStatus()
            return
        }
        
        // ✅ 否则显示保活设置引导
        showKeepAliveGuideDialog()
    }

private fun checkKeepAliveStatus() {
    val isIgnoring = keepAliveHelper.isIgnoringBatteryOptimizations()
    if (!isIgnoring) {
        showBatteryOptimizationReminder()
    }
}

private fun showBatteryOptimizationReminder() {
    AlertDialog.Builder(this)
        .setTitle("⚡ 电池优化未关闭")
        .setMessage("检测到应用未加入电池优化白名单，这可能导致应用在后台被系统杀死，影响紧急警报功能。")
        .setPositiveButton("去设置") { _, _ ->
            keepAliveHelper.requestBatteryOptimizationIgnore()
        }
        .setNegativeButton("稍后") { _, _ ->
            scheduleReminder(3)
        }
        .show()
}


private fun showKeepAliveWarning() {
    AlertDialog.Builder(this)
        .setTitle("⚠️ 重要提示")
        .setMessage("为了确保紧急警报能正常发送，请完成以下设置：\n\n" +
                    "1. ❌ 不要滑动关闭应用\n" +
                    "2. ✅ 关闭电池优化\n" +
                    "3. ✅ 允许自启动\n" +
                    "4. ✅ 锁定最近任务\n\n" +
                    "如果滑动关闭应用，可能无法及时发送警报！")
        .setPositiveButton("去设置") { _, _ ->
            keepAliveHelper.requestBatteryOptimizationIgnore()
        }
        .setNegativeButton("我知道了") { _, _ ->
            prefsManager.setSettingsGuideShown(true)
        }
        .setCancelable(false)
        .show()
}
    private fun scheduleReminder(days: Int) {
    val nextReminder = System.currentTimeMillis() + days * 24 * 60 * 60 * 1000L
    prefsManager.setNextReminderTime(nextReminder)
}

private fun showKeepAliveGuideDialog() {
    val manufacturer = keepAliveHelper.getManufacturer()
    val advice = keepAliveHelper.getKeepAliveAdvice()
    AlertDialog.Builder(this)
        .setTitle("🔋 后台运行设置")
        .setMessage("为了确保「安守」能在紧急时刻成功发送警报，需要进行以下设置：\n\n$advice\n\n是否立即进行设置？")
        .setPositiveButton("开始设置") { _, _ ->
            keepAliveHelper.requestBatteryOptimizationIgnore()
            Handler(Looper.getMainLooper()).postDelayed({ showAutoStartGuide() }, 1000)
        }
        .setNegativeButton("稍后") { _, _ ->
            // ✅ 不修改 isSettingsGuideShown，只标记已请求过保活设置
            prefsManager.setBatteryOptimizationRequested(true)
        }
        .show()
}

    private fun showAutoStartGuide() {
    val autoStartIntent = keepAliveHelper.getAutoStartIntent()
    if (autoStartIntent != null) {
        AlertDialog.Builder(this)
            .setTitle("⚙️ 自启动权限")
            .setMessage("点击确定后，请在设置中找到「安守」，开启自启动权限。")
            .setPositiveButton("确定") { _, _ ->
                try {
                    startActivity(autoStartIntent)
                } catch (e: Exception) {
                    showManualGuideDialog()
                }
            }
            .show()
    } else {
        showManualGuideDialog()
    }
}

private fun showManualGuideDialog() {
    val advice = keepAliveHelper.getKeepAliveAdvice()
    AlertDialog.Builder(this)
        .setTitle("📱 手动设置指南")
        .setMessage(advice + "\n\n设置完成后，请返回应用。")
        .setPositiveButton("我知道了") { _, _ ->
            prefsManager.setSettingsGuideShown(true)
            prefsManager.setBatteryOptimizationRequested(true)
            prefsManager.setAutoStartRequested(true)
        }
        .show()
}

    
// ✅ 重启 CheckinService 以应用新时间
private fun restartCheckinService() {
    try {
        stopService(Intent(this, CheckinService::class.java))
        Handler(Looper.getMainLooper()).postDelayed({
            startService(Intent(this, CheckinService::class.java))
        }, 100)
    } catch (e: Exception) {
        Log.e("MainActivity", "重启服务失败", e)
    }
}


// 在 MainActivity.kt 中，修改 setupAutoAlertModeViews 方法：
// ✅ 确保这个方法在 MainActivity 类内部，在最后一个 } 之前
private fun setupAutoAlertModeViews(dialogView: View) {
    val switchAutoAlertMode = dialogView.findViewById<SwitchCompat>(R.id.switchAutoAlertMode)
    val rgAlertCriteria = dialogView.findViewById<RadioGroup>(R.id.rgAlertCriteria)
    val rbUsageOnly = dialogView.findViewById<RadioButton>(R.id.rbUsageOnly)
    val rbStepOnly = dialogView.findViewById<RadioButton>(R.id.rbStepOnly)
    val rbMixed = dialogView.findViewById<RadioButton>(R.id.rbMixed)
    
    switchAutoAlertMode.isChecked = prefsManager.isAutoAlertModeEnabled()
    
    when (prefsManager.getAlertCriteria()) {
        PrefsManager.CRITERIA_USAGE_ONLY -> rbUsageOnly.isChecked = true
        PrefsManager.CRITERIA_STEP_ONLY -> rbStepOnly.isChecked = true
        PrefsManager.CRITERIA_MIXED -> rbMixed.isChecked = true
    }
    
    switchAutoAlertMode.setOnCheckedChangeListener { _, isChecked ->
        prefsManager.setAutoAlertModeEnabled(isChecked)
        rgAlertCriteria.isEnabled = isChecked
        rbUsageOnly.isEnabled = isChecked
        rbStepOnly.isEnabled = isChecked
        rbMixed.isEnabled = isChecked
    }
    
      rgAlertCriteria.setOnCheckedChangeListener { _, checkedId ->
        val criteria = when (checkedId) {
            R.id.rbUsageOnly -> PrefsManager.CRITERIA_USAGE_ONLY
            R.id.rbStepOnly -> PrefsManager.CRITERIA_STEP_ONLY
            R.id.rbMixed -> PrefsManager.CRITERIA_MIXED
            else -> PrefsManager.CRITERIA_MIXED
        }
        prefsManager.setAlertCriteria(criteria)
    }
}




/**
 * ✅ 获取今日步数（与 CheckinService 保持一致）
 */
// ✅ 修改 getTodayStepCount 方法
private fun getTodayStepCount(): Int {
    // ✅ 安全检查：确保 systemStepManager 已初始化
    if (!::systemStepManager.isInitialized) {
        Log.w(tag, "systemStepManager 未初始化，使用模拟数据")
        return getSimulatedStepCount()
    }
    
    // ✅ 优先使用系统传感器数据（带自动同步）
    if (systemStepManager.hasStepCounter()) {
        try {
            // ✅ 传入 true，强制同步最新数据
            val steps = systemStepManager.getTodaySteps(syncIfNeeded = true)
            Log.d(tag, "从系统传感器获取步数：$steps (最后同步：${systemStepManager.getLastSyncTime()})")
            return steps
        } catch (e: Exception) {
            Log.e(tag, "读取步数失败", e)
        }
    }
    
    // 后备方案：使用模拟数据
    Log.w(tag, "系统传感器不可用，使用模拟数据")
    return getSimulatedStepCount()
}


private fun updateLastTestEmailTime(tv: TextView) {
    val lastTime = prefsManager.getLastTestEmailTime()
    if (lastTime > 0) {
        val format = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        tv.text = "上次发送：${format.format(Date(lastTime))}"
    } else {
        tv.text = "上次发送：从未"
    }
}

private fun updateDeveloperLastTestEmailTime(tv: TextView) {
    val lastTime = prefsManager.getLastTestEmailTime()
    if (lastTime > 0) {
        val format = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        tv.text = "上次发送：${format.format(Date(lastTime))}"
    } else {
        tv.text = "上次发送：从未"
    }
}

// ✅ 添加模拟步数的辅助方法
private fun getSimulatedStepCount(): Int {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    
    return if (hour >= 22 || hour <= 6) {
        (10..50).random()
    } else {
        (500..2000).random()
    }
}

// ✅ 获取今日使用时长（分钟）
private fun getAppUsageMinutes(): Long {
    try {
        val usageStatsHelper = com.livewell.untils.UsageStatsHelper(this)
        return usageStatsHelper.getTodayAppUsageMinutes()
    } catch (e: Exception) {
        Log.e("MainActivity", "获取使用时长失败", e)
        return 0L
    }
}

private fun updateStepCountDisplay(tvStepCount: TextView, tvStepThreshold: TextView) {
    val stepCount = getTodayStepCount()
    val stepThreshold = prefsManager.getStepThreshold()
    
    tvStepCount.text = "$stepCount 步"
    tvStepThreshold.text = "目标：$stepThreshold 步"
    
    if (prefsManager.isStepMonitorEnabled()) {
        if (stepCount >= stepThreshold) {
            tvStepCount.setTextColor(getColor(R.color.success))
        } else {
            tvStepCount.setTextColor(getColor(R.color.error))
        }
    } else {
        tvStepCount.setTextColor(getColor(R.color.text_secondary))
    }
}

private fun startAllServices() {
    Log.i("MainActivity", "====== 开始启动所有服务 ======")
    
    try {
        // ✅ 首先启动统一通知服务（立即）
        UnifiedNotificationService.start(this)
        Log.i("MainActivity", "✅ UnifiedNotificationService 已启动")
    } catch (e: Exception) {
        Log.e("MainActivity", "❌ UnifiedNotificationService 启动失败", e)
    }
    
    // ✅ 延迟启动其他服务，每个间隔 200ms，避免主线程阻塞
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        try {
            BackgroundTimerService.start(this)
            Log.i("MainActivity", "✅ BackgroundTimerService 已启动")
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ BackgroundTimerService 启动失败", e)
        }
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                CheckinService.start(this)
                Log.i("MainActivity", "✅ CheckinService 已启动")
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ CheckinService 启动失败", e)
            }
            
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    SleepMonitorService.start(this)
                    Log.i("MainActivity", "✅ SleepMonitorService 已启动")
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ SleepMonitorService 启动失败", e)
                }
                
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        KeepAliveChecker.start(this)
                        Log.i("MainActivity", "✅ KeepAliveChecker 已启动")
                    } catch (e: Exception) {
                        Log.e("MainActivity", "❌ KeepAliveChecker 启动失败", e)
                    }
                    
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            EmailReceiverService.start(this)
                            Log.i("MainActivity", "✅ EmailReceiverService 已启动")
                        } catch (e: Exception) {
                            Log.e("MainActivity", "❌ EmailReceiverService 启动失败", e)
                        }
                        
                        // ✅ 启动时光胶囊活动检测
                        scheduleTimeCapsuleWorker()
                        
                        Log.i("MainActivity", "====== 所有服务启动完成 ======")
                    }, 200)
                }, 200)
            }, 200)
        }, 200)
    }, 200)
}

 private fun showModeSelectDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_mode_select, null)
        
        // ✅ 获取卡片
        val cardGuardian = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardGuardian)
        val cardReceiver = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardReceiver)
        val cardMixed = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardMixed)
        val cardCommunity = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardCommunity)
        
        val checkIcon = dialogView.findViewById<android.widget.ImageView>(R.id.checkIcon)
        
        val tvDescription = dialogView.findViewById<TextView>(R.id.tvModeDescription)
        val communitySettingsPanel = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.communitySettingsPanel)
        val btnPersonalInfo = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPersonalInfo)
        val btnServerConfig = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnServerConfig)
        
        // ✅ 当前选中的模式
        var selectedMode = currentMode
        
        // ✅ 初始化选中状态
        updateCardSelection(
            selectedMode,
            cardGuardian, cardReceiver, cardMixed, cardCommunity,
            checkIcon, tvDescription, communitySettingsPanel
        )
        
        // ✅ 为每个卡片添加点击事件
        cardGuardian.setOnClickListener {
            selectedMode = PrefsManager.MODE_GUARDIAN
            updateCardSelection(
                selectedMode,
                cardGuardian, cardReceiver, cardMixed, cardCommunity,
                checkIcon, tvDescription, communitySettingsPanel
            )
        }
        
        cardReceiver.setOnClickListener {
            selectedMode = PrefsManager.MODE_RECEIVER
            updateCardSelection(
                selectedMode,
                cardGuardian, cardReceiver, cardMixed, cardCommunity,
                checkIcon, tvDescription, communitySettingsPanel
            )
        }
        
        cardMixed.setOnClickListener {
            selectedMode = PrefsManager.MODE_MIXED
            updateCardSelection(
                selectedMode,
                cardGuardian, cardReceiver, cardMixed, cardCommunity,
                checkIcon, tvDescription, communitySettingsPanel
            )
        }
        
        cardCommunity.setOnClickListener {
            selectedMode = PrefsManager.MODE_COMMUNITY
            updateCardSelection(
                selectedMode,
                cardGuardian, cardReceiver, cardMixed, cardCommunity,
                checkIcon, tvDescription, communitySettingsPanel
            )
        }
        
        // 绑定按钮点击事件
        btnPersonalInfo.setOnClickListener {
            showPersonalInfoDialog()
        }
        
        btnServerConfig.setOnClickListener {
            showCommunityServerConfigDialog()
        }
        
        AlertDialog.Builder(this)
            .setTitle("模式选择")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val newMode = selectedMode
                
                // 如果从被守护模式切换到其他模式，保存被守护模式设置
                if (currentMode == PrefsManager.MODE_GUARDIAN && newMode != PrefsManager.MODE_GUARDIAN) {
                    prefsManager.saveGuardianModeSettings()
                    prefsManager.disableAllGuardianSettings()
                    Toast.makeText(this, "已保存并关闭被守护模式设置", Toast.LENGTH_SHORT).show()
                }
                
                // 如果切换到被守护模式，恢复之前保存的设置
                if (newMode == PrefsManager.MODE_GUARDIAN && currentMode != PrefsManager.MODE_GUARDIAN) {
                    prefsManager.restoreGuardianModeSettings()
                    Toast.makeText(this, "已恢复被守护模式设置", Toast.LENGTH_SHORT).show()
                }
                
                currentMode = newMode
                prefsManager.saveAppMode(currentMode)
                // ✅ 更新 Fragment 中的模式显示
                homeFragment?.let { fragment ->
                    fragment.updateModeDisplay()
                }
                restartServicesBasedOnMode()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * ✅ 更新卡片选中状态
     */
    private fun updateCardSelection(
        mode: String,
        cardGuardian: com.google.android.material.card.MaterialCardView,
        cardReceiver: com.google.android.material.card.MaterialCardView,
        cardMixed: com.google.android.material.card.MaterialCardView,
        cardCommunity: com.google.android.material.card.MaterialCardView,
        checkIcon: android.widget.ImageView,
        tvDescription: TextView,
        communitySettingsPanel: com.google.android.material.card.MaterialCardView
    ) {
        // 重置所有卡片样式
        resetCardStyle(cardGuardian)
        resetCardStyle(cardReceiver)
        resetCardStyle(cardMixed)
        resetCardStyle(cardCommunity)
        
        // 隐藏勾选图标
        checkIcon.visibility = android.view.View.GONE
        
        // 根据模式设置选中状态
        when (mode) {
            PrefsManager.MODE_GUARDIAN -> {
                setSelectedCardStyle(cardGuardian)
                tvDescription.text = "当前选择：被守护模式"
            }
            PrefsManager.MODE_RECEIVER -> {
                setSelectedCardStyle(cardReceiver)
                tvDescription.text = "当前选择：守护模式"
            }
            PrefsManager.MODE_MIXED -> {
                setSelectedCardStyle(cardMixed)
                tvDescription.text = "当前选择：混合模式"
            }
            PrefsManager.MODE_COMMUNITY -> {
                setSelectedCardStyle(cardCommunity)
                checkIcon.visibility = android.view.View.VISIBLE
                tvDescription.text = "当前选择：社区守护模式"
            }
        }
        
        // 显示/隐藏社区设置面板
        communitySettingsPanel.visibility = if (mode == PrefsManager.MODE_COMMUNITY) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }
    
    /**
     * ✅ 重置卡片样式（未选中）
     */
    private fun resetCardStyle(card: com.google.android.material.card.MaterialCardView) {
        card.strokeWidth = 2
        card.strokeColor = android.graphics.Color.parseColor("#E0E0E0")
        card.setCardBackgroundColor(android.graphics.Color.parseColor("#F9F9F9"))
        card.cardElevation = 0f
    }
    
    /**
     * ✅ 设置卡片选中样式
     */
    private fun setSelectedCardStyle(card: com.google.android.material.card.MaterialCardView) {
        card.strokeWidth = 2
        card.strokeColor = android.graphics.Color.parseColor("#F87171")
        card.setCardBackgroundColor(android.graphics.Color.parseColor("#FEF2F2"))
        card.cardElevation = 4f
    }

    private fun updateCommunitySettingsPanelVisibility(panel: LinearLayout, mode: String) {
        panel.visibility = if (mode == PrefsManager.MODE_COMMUNITY) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    /**
     * 显示个人信息设置对话框
     */
    private fun showPersonalInfoDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_personal_info, null)
        
        // 初始化控件
        val etName = dialogView.findViewById<EditText>(R.id.etName)
        val etAge = dialogView.findViewById<EditText>(R.id.etAge)
        val spinnerGender = dialogView.findViewById<Spinner>(R.id.spinnerGender)
        val etPhone = dialogView.findViewById<EditText>(R.id.etPhone)
        val etEmail = dialogView.findViewById<EditText>(R.id.etEmail)
        val etAddress = dialogView.findViewById<EditText>(R.id.etAddress)
        val etEmergencyContact = dialogView.findViewById<EditText>(R.id.etEmergencyContact)
        val etEmergencyPhone = dialogView.findViewById<EditText>(R.id.etEmergencyPhone)
        val spinnerBloodType = dialogView.findViewById<Spinner>(R.id.spinnerBloodType)
        val etAllergies = dialogView.findViewById<EditText>(R.id.etAllergies)
        val etMedicalHistory = dialogView.findViewById<EditText>(R.id.etMedicalHistory)
        val etHeight = dialogView.findViewById<EditText>(R.id.etHeight)
        val etWeight = dialogView.findViewById<EditText>(R.id.etWeight)
        val etCommunityCode = dialogView.findViewById<EditText>(R.id.etCommunityCode)
        
        // 填充现有数据
        etName.setText(prefsManager.getUserName())
        if (prefsManager.getUserAge() > 0) {
            etAge.setText(prefsManager.getUserAge().toString())
        }
        etPhone.setText(prefsManager.getUserPhone())
        etEmail.setText(prefsManager.getUserEmail())
        etAddress.setText(prefsManager.getUserAddress())
        etEmergencyContact.setText(prefsManager.getUserEmergencyContact())
        etEmergencyPhone.setText(prefsManager.getUserEmergencyPhone())
        etAllergies.setText(prefsManager.getUserAllergies())
        etMedicalHistory.setText(prefsManager.getUserMedicalHistory())
        if (prefsManager.getUserHeight() > 0) {
            etHeight.setText(prefsManager.getUserHeight().toString())
        }
        if (prefsManager.getUserWeight() > 0) {
            etWeight.setText(prefsManager.getUserWeight().toString())
        }
        etCommunityCode.setText(prefsManager.getUserCommunityCode())
        
        AlertDialog.Builder(this)
            .setTitle("个人信息设置")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                // 保存数据
                prefsManager.saveUserName(etName.text.toString())
                if (etAge.text.toString().isNotEmpty()) {
                    prefsManager.saveUserAge(etAge.text.toString().toInt())
                }
                prefsManager.saveUserGender(spinnerGender.selectedItem.toString())
                prefsManager.saveUserPhone(etPhone.text.toString())
                prefsManager.saveUserEmail(etEmail.text.toString())
                prefsManager.saveUserAddress(etAddress.text.toString())
                prefsManager.saveUserEmergencyContact(etEmergencyContact.text.toString())
                prefsManager.saveUserEmergencyPhone(etEmergencyPhone.text.toString())
                prefsManager.saveUserBloodType(spinnerBloodType.selectedItem.toString())
                prefsManager.saveUserAllergies(etAllergies.text.toString())
                prefsManager.saveUserMedicalHistory(etMedicalHistory.text.toString())
                if (etHeight.text.toString().isNotEmpty()) {
                    prefsManager.saveUserHeight(etHeight.text.toString().toFloat())
                }
                if (etWeight.text.toString().isNotEmpty()) {
                    prefsManager.saveUserWeight(etWeight.text.toString().toFloat())
                }
                prefsManager.saveUserCommunityCode(etCommunityCode.text.toString())
                
                Toast.makeText(this, "个人信息已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示社区服务器配置对话框
     */
    



        private fun showCommunityServerConfigDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_server_config, null)
        
        val etServerUrl = dialogView.findViewById<EditText>(R.id.etServerUrl)
        val etApiKey = dialogView.findViewById<EditText>(R.id.etApiKey)
        val etUserId = dialogView.findViewById<EditText>(R.id.etUserId)
        
        // 填充现有数据
        etServerUrl.setText(prefsManager.getCommunityServerUrl())
        etApiKey.setText(prefsManager.getCommunityApiKey())
        etUserId.setText(prefsManager.getCommunityUserId())
        
        AlertDialog.Builder(this)
            .setTitle("后端服务器配置")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                prefsManager.saveCommunityServerUrl(etServerUrl.text.toString())
                prefsManager.saveCommunityApiKey(etApiKey.text.toString())
                prefsManager.saveCommunityUserId(etUserId.text.toString())
                prefsManager.setCommunityEnabled(true)
                
                Toast.makeText(this, "服务器配置已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }   
    
     /**
     * 构建测试警报原因
     */
    private fun buildTestAlertReason(isSigned: Boolean, appUsage: Int, stepCount: Int): String {
        val reasons = mutableListOf<String>()
        
        if (!isSigned) {
            reasons.add("未签到")
        }
        
        val usageThreshold = prefsManager.getAppUsageThreshold()
        if (appUsage < usageThreshold) {
            reasons.add("今日使用${appUsage}min < ${usageThreshold}min")
        }
        
        val stepThreshold = prefsManager.getStepThreshold()
        if (prefsManager.isStepMonitorEnabled() && stepCount < stepThreshold) {
            reasons.add("步数${stepCount} < ${stepThreshold}")
        }
        
        return reasons.joinToString("; ")
    }
    
    /**
     * 发送模拟警报到社区后端
     */
    private fun sendSimulatedAlertToCommunity(reason: String) {
        val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val serverUrl = prefsManager.getCommunityServerUrl()
                    val apiKey = prefsManager.getCommunityApiKey()
                    val userId = prefsManager.getCommunityUserId()
                    
                    if (serverUrl.isEmpty() || apiKey.isEmpty() || userId.isEmpty()) {
                        Log.e("MainActivity", "社区服务器配置不完整，无法发送警报")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "请先配置社区服务器信息", Toast.LENGTH_SHORT).show()
                        }
                        return@withContext
                    }
                    
                    // 构建警报复数据
                    val alertJson = JSONObject()
                    alertJson.put("userId", userId)
                    alertJson.put("alertType", "checkin")
                    alertJson.put("alertLevel", "warning")
                    alertJson.put("triggerTime", System.currentTimeMillis())
                    alertJson.put("resolved", false)
                    alertJson.put("reason", reason)
                    alertJson.put("content", "【测试警报】这是一条模拟的测试警报数据")
                    alertJson.put("isTest", true)
                    
                    // 添加紧急联系人信息
                    val contact = prefsManager.getEmergencyContact()
                    if (!contact.isNullOrEmpty()) {
                        alertJson.put("emergencyContact", contact)
                    }
                    
                    // 构建设备状态
                    val deviceJson = JSONObject()
                    deviceJson.put("batteryLevel", -1)
                    deviceJson.put("isCharging", false)
                    deviceJson.put("networkType", "wifi")
                    deviceJson.put("signalStrength", -1)
                    deviceJson.put("appVersion", packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0")
                    deviceJson.put("systemVersion", Build.VERSION.RELEASE)
                    alertJson.put("deviceStatus", deviceJson)
                    
                    val jsonData = alertJson.toString()
                    
                    // 发送到社区后端 API
                    val url = URL("$serverUrl/api/community/alert")
                    val connection = url.openConnection() as HttpURLConnection
                    
                    try {
                        connection.requestMethod = "POST"
                        connection.connectTimeout = 15000
                        connection.readTimeout = 15000
                        connection.doOutput = true
                        
                        // 设置请求头
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.setRequestProperty("Authorization", "Bearer $apiKey")
                        connection.setRequestProperty("X-User-ID", userId)
                        
                        // 发送数据
                        connection.outputStream.use { os ->
                            os.write(jsonData.toByteArray())
                        }
                        
                        // 读取响应
                        val responseCode = connection.responseCode
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            val reader = BufferedReader(InputStreamReader(connection.inputStream))
                            val response = reader.readText()
                            Log.i("MainActivity", "✅ 模拟警报发送成功：$response")
                            reader.close()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "✅ 模拟警报发送成功", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Log.e("MainActivity", "❌ 模拟警报发送失败，响应码：$responseCode")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "❌ 发送失败，响应码：$responseCode", Toast.LENGTH_LONG).show()
                            }
                        }
                    } finally {
                        connection.disconnect()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ 发送模拟警报异常：${e.message}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "❌ 发送异常：${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    
    /**
     * 立即上报完整健康数据到社区后端
     */
    private fun sendImmediateHealthDataToCommunity() {
        val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val serverUrl = prefsManager.getCommunityServerUrl()
                    val apiKey = prefsManager.getCommunityApiKey()
                    val userId = prefsManager.getCommunityUserId()
                    
                    if (serverUrl.isEmpty() || apiKey.isEmpty() || userId.isEmpty()) {
                        Log.e("MainActivity", "社区服务器配置不完整，无法上报数据")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "请先配置社区服务器信息", Toast.LENGTH_SHORT).show()
                        }
                        return@withContext
                    }
                    
                    // 获取系统服务
                    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    
                    // 获取电池信息
                    val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    val isCharging = batteryManager.isCharging
                    
                    // 获取网络类型（兼容新旧版本）
                      // 获取网络类型（兼容新旧版本）
                    val networkType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val network = connectivityManager.activeNetwork
                        val capabilities = network?.let { 
                            connectivityManager.getNetworkCapabilities(it) 
                        }
                        when {
                            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
                            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "mobile"
                            else -> "disconnected"
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val networkInfo = connectivityManager.activeNetworkInfo
                        when {
                            networkInfo == null -> "disconnected"
                            networkInfo.type == ConnectivityManager.TYPE_WIFI -> "wifi"
                            networkInfo.type == ConnectivityManager.TYPE_MOBILE -> "mobile"
                            else -> "unknown"
                        }
                    }
                    
                    // 获取应用使用时间
                    val usageStatsHelper = UsageStatsHelper(this@MainActivity)
                    val appUsageTime = (usageStatsHelper.getTodayAppUsageMinutes() / 1000 / 60).toInt()
                    
                    // 获取步数
                    val systemStepManager = SystemStepManager.getInstance(this@MainActivity)
                    val stepCount = if (systemStepManager.hasStepCounter()) {
                        systemStepManager.getTodaySteps()
                    } else {
                        0
                    }
                    
                    // 构建完整的健康数据包
                    val healthDataJson = JSONObject()
                    healthDataJson.put("userId", userId)
                    healthDataJson.put("timestamp", System.currentTimeMillis())
                    
                    // 设备状态
                    val deviceJson = JSONObject()
                    deviceJson.put("batteryLevel", batteryLevel)
                    deviceJson.put("isCharging", isCharging)
                    deviceJson.put("networkType", networkType)
                    deviceJson.put("signalStrength", -1)
                    deviceJson.put("appVersion", packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0")
                    deviceJson.put("systemVersion", Build.VERSION.RELEASE)
                    healthDataJson.put("deviceStatus", deviceJson)
                    
                    // 健康指标
                    val healthJson = JSONObject()
                    healthJson.put("stepCount", stepCount)
                    healthJson.put("sleepDuration", 0)
                    healthJson.put("sleepQuality", "fair")
                    healthJson.put("heartRate", null)
                    healthJson.put("bloodOxygen", null)
                    healthJson.put("calorieBurn", 0)
                    healthDataJson.put("healthMetrics", healthJson)
                    
                    // 活动数据
                    val activityJson = JSONObject()
                    activityJson.put("screenOnTime", 0)
                    activityJson.put("appUsageTime", appUsageTime)
                    activityJson.put("lastActiveTime", System.currentTimeMillis())
                    activityJson.put("isDeviceInUse", false)
                    activityJson.put("unlockCount", 0)
                    healthDataJson.put("activityData", activityJson)
                    
                    // 警报事件（空列表）
                    val alertsArray = JSONArray()
                    healthDataJson.put("alertEvents", alertsArray)
                    
                    val jsonData = healthDataJson.toString()
                    
                    // 发送到社区后端 API
                    val url = URL("$serverUrl/api/community/health-data")
                    val connection = url.openConnection() as HttpURLConnection
                    
                    try {
                        connection.requestMethod = "POST"
                        connection.connectTimeout = 15000
                        connection.readTimeout = 15000
                        connection.doOutput = true
                        
                        // 设置请求头
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.setRequestProperty("Authorization", "Bearer $apiKey")
                        connection.setRequestProperty("X-User-ID", userId)
                        
                        // 发送数据
                        connection.outputStream.use { os ->
                            os.write(jsonData.toByteArray())
                        }
                        
                        // 读取响应
                        val responseCode = connection.responseCode
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            val reader = BufferedReader(InputStreamReader(connection.inputStream))
                            val response = reader.readText()
                            Log.i("MainActivity", "✅ 健康数据上报成功：$response")
                            reader.close()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "✅ 健康数据上报成功", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Log.e("MainActivity", "❌ 健康数据上报失败，响应码：$responseCode")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "❌ 上报失败，响应码：$responseCode", Toast.LENGTH_LONG).show()
                            }
                        }
                    } finally {
                        connection.disconnect()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ 上报健康数据异常：${e.message}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "❌ 上报异常：${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // ✅ 以下方法供 SettingsFragment 调用
    
    /**
     * 显示紧急联系人设置对话框（公开版本）
     */
    fun showEmergencyContactSettingsDialogPublic() {
        showEmergencyContactSettingsDialog()
    }
    
    /**
     * 显示功能设置对话框（公开版本）
     */
    fun showSmartSettingsDialogPublic() {
        showSmartSettingsDialog()
    }
    
    /**
     * 显示模式选择对话框（公开版本）
     */
    fun showModeSelectDialogPublic() {
        showModeSelectDialog()
    }
    
    /**
     * 显示设置引导（公开版本）
     */
    fun showSettingsGuidePublic() {
        showSettingsGuide()
    }
    
    // ========== 时光胶囊相关方法 ==========
    
    /**
     * 显示时光胶囊主对话框
     */
    fun showTimeCapsuleDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        
        // 创建自定义视图
        val view = layoutInflater.inflate(R.layout.dialog_time_capsule_main, null)
        
        val btnViewPasswordBook = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnViewPasswordBook)
        val btnSettings = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSettings)
        val btnCancel = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        
        // 设置按钮点击事件
        btnViewPasswordBook.setOnClickListener {
            // 启动密码书查看界面
            val intent = Intent(this, PasswordBookViewActivity::class.java)
            startActivity(intent)
        }
        
        btnSettings.setOnClickListener {
            // 启动时光胶囊设置界面
            val intent = Intent(this, TimeCapsuleSettingsActivity::class.java)
            startActivity(intent)
        }
        
        btnCancel.setOnClickListener {
            // 关闭对话框
        }
        
        builder.setView(view)
        val dialog = builder.create()
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    /**
     * 显示时光胶囊设置对话框
     */
    private fun showTimeCapsuleSettingsDialog() {
        // 如果已设置密码书问题，需要先验证
        val question = prefsManager.getPasswordBookQuestion()
        val answer = prefsManager.getPasswordBookAnswer()
        
        if (question.isNotEmpty() && answer.isNotEmpty()) {
            // 显示密码验证对话框
            showPasswordVerificationDialog()
        } else {
            // 未设置密码，直接进入设置界面
            showTimeCapsuleSettingsDialogInternal()
        }
    }
    
    /**
     * 显示密码验证对话框（用于进入设置界面）
     */
    private fun showPasswordVerificationDialog() {
        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_password_verification, null)
        
        val tvQuestion = view.findViewById<TextView>(R.id.tvVerificationQuestion)
        val etAnswer = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etVerificationAnswer)
        val btnVerify = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnVerifyPassword)
        
        // 显示问题
        val question = prefsManager.getPasswordBookQuestion()
        tvQuestion.text = "请回答安全问题以验证身份：\n$question"
        
        // 验证按钮
        btnVerify.setOnClickListener {
            val inputAnswer = etAnswer.text.toString()
            val correctAnswer = prefsManager.getPasswordBookAnswer()
            
            if (inputAnswer == correctAnswer) {
                // 答案正确，进入设置界面
                showTimeCapsuleSettingsDialogInternal()
            } else {
                // 答案错误
                Toast.makeText(this, "答案错误，请重试", Toast.LENGTH_SHORT).show()
                etAnswer.text?.clear()
            }
        }
        
        builder.setView(view)
        builder.setTitle("身份验证")
        builder.setPositiveButton("取消", null)
        builder.show()
    }
    
    /**
     * 显示时光胶囊设置对话框（内部方法）
     */
    private fun showTimeCapsuleSettingsDialogInternal() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("时光胶囊设置")
        
        val view = layoutInflater.inflate(R.layout.dialog_time_capsule_settings, null)
        
        // 获取视图引用
        val switchTimeCapsule = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchTimeCapsule)
        val cardEmergencyEmail = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardEmergencyEmail)
        val cardPasswordBook = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardPasswordBook)
        val switchEmergencyEmail = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchEmergencyEmail)
        val switchPasswordBook = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchPasswordBook)
        val etInactiveThreshold = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etInactiveThreshold)
        val etEmergencyEmailText = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEmergencyEmailText)
        val etPasswordQuestion = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPasswordQuestion)
        val etPasswordAnswer = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPasswordAnswer)
        val etPasswordContent = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPasswordContent)
        val rvAttachments = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvAttachments)  // ✅ 改为 RecyclerView
        val btnAddAttachment = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddAttachment)
        
        // 加载现有设置
        switchTimeCapsule.isChecked = prefsManager.isTimeCapsuleEnabled()
        switchEmergencyEmail.isChecked = prefsManager.isEmergencyEmailEnabled()
        switchPasswordBook.isChecked = prefsManager.isPasswordBookEnabled()
        etInactiveThreshold.setText(prefsManager.getInactiveThresholdDays().toString())
        etEmergencyEmailText.setText(prefsManager.getEmergencyEmailText())
        etPasswordQuestion.setText(prefsManager.getPasswordBookQuestion())
        etPasswordAnswer.setText(prefsManager.getPasswordBookAnswer())
        etPasswordContent.setText(prefsManager.getPasswordBookContent())
        
        // ✅ 加载现有附件列表（使用 RecyclerView）
        setupAttachmentRecyclerView(rvAttachments, prefsManager.getPasswordBookAttachments(), true)
        
        // 根据总开关显示/隐藏功能卡片
        updateFeatureCardsVisibility(switchTimeCapsule.isChecked, cardEmergencyEmail, cardPasswordBook)
        
        // 总开关监听
        switchTimeCapsule.setOnCheckedChangeListener { _, isChecked ->
            updateFeatureCardsVisibility(isChecked, cardEmergencyEmail, cardPasswordBook)
        }
        
        // 添加附件按钮
        btnAddAttachment.setOnClickListener {
            showAddAttachmentDialog(rvAttachments)  // ✅ 传入 RecyclerView
        }
        
        builder.setView(view)
        builder.setPositiveButton("保存") { _, _ ->
            saveTimeCapsuleSettings(
                switchTimeCapsule.isChecked,
                switchEmergencyEmail.isChecked,
                switchPasswordBook.isChecked,
                etInactiveThreshold.text.toString(),
                etEmergencyEmailText.text.toString(),
                etPasswordQuestion.text.toString(),
                etPasswordAnswer.text.toString(),
                etPasswordContent.text.toString()
            )
            Toast.makeText(this, "时光胶囊设置已保存", Toast.LENGTH_SHORT).show()
        }
        
        builder.setNegativeButton("取消", null)
        builder.show()
    }
    
    /**
     * 更新功能卡片可见性
     */
    private fun updateFeatureCardsVisibility(
        isEnabled: Boolean,
        cardEmergencyEmail: com.google.android.material.card.MaterialCardView,
        cardPasswordBook: com.google.android.material.card.MaterialCardView
    ) {
        cardEmergencyEmail.visibility = if (isEnabled) View.VISIBLE else View.GONE
        cardPasswordBook.visibility = if (isEnabled) View.VISIBLE else View.GONE
    }
    
    /**
     * 显示添加附件对话框
     */
    private fun showAddAttachmentDialog(rvAttachments: androidx.recyclerview.widget.RecyclerView) {
        // ✅ 保存当前附件列表 RecyclerView 引用
        currentAttachmentsRecyclerView = rvAttachments
        
        val items = arrayOf("拍照", "从相册选择图片", "拍摄视频", "从相册选择视频")
        
        MaterialAlertDialogBuilder(this)
            .setTitle("添加附件")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> requestCameraPermissionForPhoto()
                    1 -> pickImageFromGallery()
                    2 -> requestCameraPermissionForVideo()
                    3 -> pickVideoFromGallery()
                }
            }
            .show()
    }
    
    /**
     * 请求相机权限（拍照）
     */
    private fun requestCameraPermissionForPhoto() {
        currentAttachmentType = "photo"
        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }
    
    /**
     * 启动相机拍照
     */
    private fun launchCamera() {
        // 创建文件 URI
        val photoFile = createImageFile()
        imageCaptureUri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            photoFile
        )
        takePictureLauncher.launch(imageCaptureUri)
    }
    
    /**
     * 创建图片文件
     */
    private fun createImageFile(): java.io.File {
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return java.io.File.createTempFile(
            "PHOTO_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }
    
    /**
     * 从相册选择图片
     */
    private fun pickImageFromGallery() {
        pickImageLauncher.launch("image/*")
    }
    
    /**
     * 请求相机权限（拍视频）
     */
    private fun requestCameraPermissionForVideo() {
        currentAttachmentType = "video"
        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }
    
    /**
     * 从相册选择视频
     */
    private fun pickVideoFromGallery() {
        pickVideoLauncher.launch("video/*")
    }
    
    /**
     * 保存附件到列表
     */
    private fun saveAttachment(uriString: String) {
        prefsManager.addPasswordBookAttachment(uriString)
        // ✅ 使用保存的 RecyclerView 引用来更新当前对话框中的附件列表
        currentAttachmentsRecyclerView?.let { rv ->
            setupAttachmentRecyclerView(rv, prefsManager.getPasswordBookAttachments(), true)
        }
        Log.d(TAG, "✅ 附件已保存并更新UI：$uriString")
    }
    
    /**
     * 更新附件列表显示
     */
    private fun updateAttachmentsList(layoutAttachments: LinearLayout?) {
        val attachments = prefsManager.getPasswordBookAttachments()
        layoutAttachments?.removeAllViews()
        
        if (attachments.isEmpty()) {
            val textView = TextView(this).apply {
                text = "暂无附件"
                textSize = 14f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(16, 16, 16, 16)
            }
            layoutAttachments?.addView(textView)
        } else {
            attachments.forEach { path ->
                val itemView = createAttachmentItemView(path)
                layoutAttachments?.addView(itemView)
            }
        }
    }
    
    /**
     * 创建单个附件视图项
     */
    private fun createAttachmentItemView(path: String): View {
        val view = layoutInflater.inflate(R.layout.item_attachment, null)
        val tvName = view.findViewById<TextView>(R.id.tvAttachmentName)
        val btnRemove = view.findViewById<ImageButton>(R.id.btnRemoveAttachment)
        
        val fileName = path.substringAfterLast("/")
        tvName.text = fileName
        
        // 设置图标
        val iconRes = when {
            fileName.endsWith(".jpg") || fileName.endsWith(".png") || fileName.endsWith(".jpeg") ->
                android.R.drawable.ic_menu_gallery
            fileName.endsWith(".mp4") || fileName.endsWith(".3gp") ->
                android.R.drawable.ic_menu_view
            else -> android.R.drawable.ic_menu_share
        }
        view.findViewById<ImageView>(R.id.ivAttachmentIcon).setImageResource(iconRes)
        
        // 删除按钮
        btnRemove.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除这个附件吗？")
                .setPositiveButton("删除") { _, _ ->
                    prefsManager.removePasswordBookAttachment(path)
                    updateAttachmentsList(view.parent as? LinearLayout)
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        
        // 点击查看
        view.setOnClickListener {
            viewAttachment(path)
        }
        
        return view
    }
    
    /**
     * 查看附件
     */
    /**
     * 查看附件
     */
    private fun viewAttachment(path: String) {
        // 判断文件类型
        if (isImageFile(path)) {
            // 图片：使用应用内查看器
            ImageViewerActivity.start(this, path)
        } else {
            // 其他文件：调用系统查看器
            try {
                val uri = Uri.parse(path)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, getMimeType(path))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开文件：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 判断是否为图片文件
     */
    private fun isImageFile(path: String): Boolean {
        val lowerPath = path.lowercase()
        return lowerPath.endsWith(".jpg") || 
               lowerPath.endsWith(".jpeg") || 
               lowerPath.endsWith(".png") || 
               lowerPath.endsWith(".gif") || 
               lowerPath.endsWith(".webp") || 
               lowerPath.endsWith(".bmp")
    }
    
    /**
     * 获取文件 MIME 类型
     */
    private fun getMimeType(path: String): String {
        return when {
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".gif") -> "image/gif"
            path.endsWith(".mp4") -> "video/mp4"
            path.endsWith(".3gp") -> "video/3gpp"
            else -> "*/*"
        }
    }
    
    /**
     * 持久化 URI 权限
     */
    private fun persistUriPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            Log.d(TAG, "URI 权限持久化失败：${e.message}")
        }
    }
    
    /**
     * ✅ 复制文件到应用私有目录（防止原文件被删除）
     * @param sourceUri 源文件 URI
     * @param fileType 文件类型：image 或 video
     * @return 复制后的文件 URI，失败返回 null
     */
    private fun copyFileToAppDirectory(sourceUri: Uri, fileType: String): Uri? {
        return try {
            // 生成文件名
            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                .format(java.util.Date())
            val extension = when (fileType) {
                "image" -> ".jpg"
                "video" -> ".mp4"
                else -> ".dat"
            }
            val fileName = "ATTACHMENT_${timeStamp}_${System.currentTimeMillis()}$extension"
            
            // 确定存储目录
            val storageDir = when (fileType) {
                "image" -> getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                "video" -> getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                else -> getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            }
            
            if (storageDir == null) {
                Log.e(TAG, "❌ 存储目录为空")
                return null
            }
            
            // 创建目标文件
            val destFile = java.io.File(storageDir, fileName)
            
            // 复制文件
            contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                java.io.FileOutputStream(destFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()
                }
            }
            
            // 获取新文件的 URI
            val newUri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                destFile
            )
            
            Log.i(TAG, "✅ 文件已复制到：${destFile.absolutePath}")
            newUri
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 复制文件失败：${e.message}", e)
            null
        }
    }
    
    /**
     * 保存时光胶囊设置
     */
    private fun saveTimeCapsuleSettings(
        timeCapsuleEnabled: Boolean,
        emergencyEmailEnabled: Boolean,
        passwordBookEnabled: Boolean,
        thresholdDays: String,
        emailText: String,
        question: String,
        answer: String,
        content: String
    ) {
        prefsManager.setTimeCapsuleEnabled(timeCapsuleEnabled)
        prefsManager.setEmergencyEmailEnabled(emergencyEmailEnabled)
        prefsManager.setPasswordBookEnabled(passwordBookEnabled)
        
        if (thresholdDays.isNotEmpty()) {
            prefsManager.setInactiveThresholdDays(thresholdDays.toIntOrNull() ?: 3)
        }
        
        prefsManager.setEmergencyEmailText(emailText)
        prefsManager.setPasswordBookQuestion(question)
        prefsManager.setPasswordBookAnswer(answer)
        prefsManager.setPasswordBookContent(content)
    }
    
    /**
     * 显示密码书查看对话框
     */
    private fun showPasswordBookViewDialog() {
        if (!prefsManager.isTimeCapsuleEnabled() || !prefsManager.isPasswordBookEnabled()) {
            Toast.makeText(this, "密码书功能未启用", Toast.LENGTH_SHORT).show()
            return
        }
        
        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_password_book_view, null)
        
        val tvPasswordQuestion = view.findViewById<TextView>(R.id.tvPasswordQuestion)
        val etAnswerInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etAnswerInput)
        val btnVerifyAnswer = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnVerifyAnswer)
        val layoutContentDisplay = view.findViewById<LinearLayout>(R.id.layoutContentDisplay)
        val tvPasswordContent = view.findViewById<TextView>(R.id.tvPasswordContent)
        val tvAttachmentsTitle = view.findViewById<TextView>(R.id.tvAttachmentsTitle)
        val rvAttachments = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvAttachments)
        
        // 加载问题
        val question = prefsManager.getPasswordBookQuestion()
        tvPasswordQuestion.text = if (question.isEmpty()) "问题未设置" else question
        
        // 验证答案按钮
        btnVerifyAnswer.setOnClickListener {
            val inputAnswer = etAnswerInput.text.toString()
            val correctAnswer = prefsManager.getPasswordBookAnswer()
            
            if (inputAnswer == correctAnswer) {
                // 答案正确，显示内容
                layoutContentDisplay.visibility = View.VISIBLE
                tvPasswordContent.text = prefsManager.getPasswordBookContent()
                
                // 加载附件
                val attachments = prefsManager.getPasswordBookAttachments()
                if (attachments.isNotEmpty()) {
                    tvAttachmentsTitle.visibility = View.VISIBLE
                    setupAttachmentRecyclerView(rvAttachments, attachments, false)
                } else {
                    tvAttachmentsTitle.visibility = View.GONE
                }
                
                Toast.makeText(this, "验证成功", Toast.LENGTH_SHORT).show()
            } else {
                // 答案错误
                Toast.makeText(this, "答案错误，请重试", Toast.LENGTH_SHORT).show()
                etAnswerInput.text?.clear()
            }
        }
        
        builder.setView(view)
        builder.setTitle(" 密码书")
        builder.setPositiveButton("关闭", null)
        builder.show()
    }
    
    /**
     * 设置附件 RecyclerView
     */
    private fun setupAttachmentRecyclerView(
        recyclerView: androidx.recyclerview.widget.RecyclerView,
        attachments: List<String>,
        isEditMode: Boolean
    ) {
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        val adapter = AttachmentAdapter(attachments, isEditMode, { path ->
            // ✅ 删除附件
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除这个附件吗？")
                .setPositiveButton("删除") { _, _ ->
                    prefsManager.removePasswordBookAttachment(path)
                    // 重新加载附件列表
                    setupAttachmentRecyclerView(recyclerView, prefsManager.getPasswordBookAttachments(), isEditMode)
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }) { path ->
            // ✅ 查看附件
            viewAttachment(path)
        }
        recyclerView.adapter = adapter
    }
    
    /**
     * 调度时光胶囊 Worker
     * 每天检查一次用户活动状态
     */
    private fun scheduleTimeCapsuleWorker() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<TimeCapsuleWorker>(
            24, java.util.concurrent.TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag("time_capsule_worker")
            .build()
        
        androidx.work.WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "time_capsule_worker",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        
        Log.d(TAG, "时光胶囊 Worker 已调度")
    }
    
    /**
     * ✅ 重启所有服务（用于开发者测试）
     */
    private fun restartAllServices() {
        Log.i(TAG, "====== 开始重启所有服务 ======")
        // ✅ 使用 AppLogger 写入文件，以便在日志查看界面显示
        com.livewell.untils.AppLogger.i(TAG, "🔄 开始重启所有服务")
        
        try {
            // 1. 停止 CheckinService
            Log.i(TAG, "正在停止 CheckinService...")
            com.livewell.untils.AppLogger.i(TAG, "⏹️ 正在停止 CheckinService")
            val checkinIntent = Intent(this, CheckinService::class.java)
            stopService(checkinIntent)
            
            // 2. 停止 EmailReceiverService
            Log.i(TAG, "正在停止 EmailReceiverService...")
            com.livewell.untils.AppLogger.i(TAG, "⏹️ 正在停止 EmailReceiverService")
            val emailIntent = Intent(this, EmailReceiverService::class.java)
            stopService(emailIntent)
            
            // 3. 停止 KeepAliveChecker
            Log.i(TAG, "正在停止 KeepAliveChecker...")
            com.livewell.untils.AppLogger.i(TAG, "⏹️ 正在停止 KeepAliveChecker")
            val keepAliveIntent = Intent(this, KeepAliveChecker::class.java)
            stopService(keepAliveIntent)
            
            // 4. 取消 KeepAliveJobService
            Log.i(TAG, "正在取消 KeepAliveJobService...")
            com.livewell.untils.AppLogger.i(TAG, "⏹️ 正在取消 KeepAliveJobService")
            KeepAliveJobService.cancelJob(this)
            
            // 5. 等待一下，确保服务完全停止
            Thread.sleep(500)
            
            // 6. 重新启动服务
            Log.i(TAG, "正在重新启动服务...")
            com.livewell.untils.AppLogger.i(TAG, "🚀 正在重新启动服务...")
            val mode = prefsManager.getAppMode()
            
            when (mode) {
                PrefsManager.MODE_GUARDIAN -> {
                    CheckinService.start(this)
                    Log.i(TAG, "✅ CheckinService 已启动")
                    com.livewell.untils.AppLogger.i(TAG, "✅ CheckinService 已启动")
                }
                PrefsManager.MODE_RECEIVER -> {
                    EmailReceiverService.start(this)
                    Log.i(TAG, "✅ EmailReceiverService 已启动")
                    com.livewell.untils.AppLogger.i(TAG, "✅ EmailReceiverService 已启动")
                }
                PrefsManager.MODE_MIXED -> {
                    CheckinService.start(this)
                    EmailReceiverService.start(this)
                    Log.i(TAG, "✅ CheckinService 和 EmailReceiverService 已启动")
                    com.livewell.untils.AppLogger.i(TAG, "✅ CheckinService 和 EmailReceiverService 已启动")
                }
            }
            
            // 7. 重新启动 KeepAliveChecker
            KeepAliveChecker.start(this)
            KeepAliveJobService.scheduleJob(this)
            Log.i(TAG, "✅ KeepAliveChecker 和 KeepAliveJobService 已启动")
            com.livewell.untils.AppLogger.i(TAG, "✅ KeepAliveChecker 和 KeepAliveJobService 已启动")
            
            // 8. 显示成功提示
            Toast.makeText(this, "✅ 所有服务已重启", Toast.LENGTH_SHORT).show()
            Log.i(TAG, "====== 所有服务重启完成 ======")
            com.livewell.untils.AppLogger.i(TAG, "✅✅✅ 所有服务重启完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 重启服务失败：${e.message}", e)
            com.livewell.untils.AppLogger.e(TAG, "❌ 重启服务失败：${e.message}")
            Toast.makeText(this, "❌ 重启失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * ✅ 清除今日警报记录（用于生产环境测试）
     */
    private fun clearTodayAlertRecord() {
        Log.i(TAG, "====== 清除今日警报记录 ======")
        com.livewell.untils.AppLogger.i(TAG, "🗑️ 清除今日警报记录")
        
        try {
            // 1. 显示确认对话框
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("⚠️ 确认清除")
                .setMessage("此操作将清除今日的最后报警时间记录，允许再次触发警报。\n\n仅用于生产环境测试，请谨慎使用！\n\n是否继续？")
                .setPositiveButton("确认清除") { _, _ ->
                    // 2. 清除 last_alert_time
                    prefsManager.saveLastAlertTime(0)
                    
                    Log.i(TAG, "✅ 已清除最后报警时间记录")
                    com.livewell.untils.AppLogger.i(TAG, "✅ 已清除最后报警时间记录")
                    
                    // 3. 显示成功提示
                    Toast.makeText(this, "✅ 已清除今日警报记录，可以重新触发警报", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("取消", null)
                .show()
                
        } catch (e: Exception) {
            Log.e(TAG, "❌ 清除警报记录失败：${e.message}", e)
            com.livewell.untils.AppLogger.e(TAG, "❌ 清除警报记录失败：${e.message}")
            Toast.makeText(this, "❌ 清除失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * ✅ 设置无声音乐频率测试按钮
     */
    private fun setupSilentMusicFrequencyTest(dialogView: View) {
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvSilentMusicStatus)
        val btnStart = dialogView.findViewById<Button>(R.id.btnStartSilentMusic)
        val btnStop = dialogView.findViewById<Button>(R.id.btnStopSilentMusic)
        val btnFreq8k = dialogView.findViewById<Button>(R.id.btnFreq8k)
        val btnFreq11k = dialogView.findViewById<Button>(R.id.btnFreq11k)
        val btnFreq16k = dialogView.findViewById<Button>(R.id.btnFreq16k)
        val btnFreq22k = dialogView.findViewById<Button>(R.id.btnFreq22k)
        val btnFreq32k = dialogView.findViewById<Button>(R.id.btnFreq32k)
        val btnFreq44k = dialogView.findViewById<Button>(R.id.btnFreq44k)
        
        // 更新状态显示
        updateSilentMusicStatus(tvStatus)
        
        // 启动按钮
        btnStart.setOnClickListener {
            val sampleRate = SilentMusicService.currentSampleRate
            SilentMusicService.startWithSampleRate(this, sampleRate)
            Toast.makeText(this, "启动无声音乐 - ${sampleRate / 1000}kHz", Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({
                updateSilentMusicStatus(tvStatus)
            }, 1000)
        }
        
        // 停止按钮
        btnStop.setOnClickListener {
            SilentMusicService.stop(this)
            Toast.makeText(this, "停止无声音乐", Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({
                updateSilentMusicStatus(tvStatus)
            }, 500)
        }
        
        // 频率选择按钮
        btnFreq8k.setOnClickListener {
            changeSampleRateAndRestart(8000, tvStatus)
        }
        
        btnFreq11k.setOnClickListener {
            changeSampleRateAndRestart(11025, tvStatus)
        }
        
        btnFreq16k.setOnClickListener {
            changeSampleRateAndRestart(16000, tvStatus)
        }
        
        btnFreq22k.setOnClickListener {
            changeSampleRateAndRestart(22050, tvStatus)
        }
        
        btnFreq32k.setOnClickListener {
            changeSampleRateAndRestart(32000, tvStatus)
        }
        
        btnFreq44k.setOnClickListener {
            changeSampleRateAndRestart(44100, tvStatus)
        }
    }
    
    /**
     * ✅ 改变采样率并重启服务
     */
    private fun changeSampleRateAndRestart(sampleRate: Int, statusTextView: TextView) {
        val wasRunning = SilentMusicService.isRunning
        
        // 更新当前采样率
        SilentMusicService.currentSampleRate = sampleRate
        
        Toast.makeText(this, "设置采样率：${sampleRate / 1000}kHz", Toast.LENGTH_SHORT).show()
        
        // 如果服务正在运行，重启以应用新采样率
        if (wasRunning) {
            SilentMusicService.stop(this)
            Handler(Looper.getMainLooper()).postDelayed({
                SilentMusicService.startWithSampleRate(this, sampleRate)
                updateSilentMusicStatus(statusTextView)
            }, 500)
        } else {
            updateSilentMusicStatus(statusTextView)
        }
    }
    
    /**
     * ✅ 更新无声音乐状态显示
     */
    private fun updateSilentMusicStatus(textView: TextView) {
        if (SilentMusicService.isRunning) {
            val rate = SilentMusicService.currentSampleRate
            textView.text = "运行中 (${rate / 1000}kHz)"
            textView.setTextColor(getColor(R.color.success))
        } else {
            textView.text = "未运行"
            textView.setTextColor(getColor(R.color.error))
        }
    }
    
    /**
     * ✅ 显示日志查看对话框
     */
    private fun showLogsDialog() {
        try {
            val builder = AlertDialog.Builder(this)
            val view = layoutInflater.inflate(R.layout.dialog_logs, null)
            
            val tvLogContent = view.findViewById<TextView>(R.id.tvLogContent)
            val chipGroupLogFilter = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupLogFilter)
            val chipAllLogs = view.findViewById<com.google.android.material.chip.Chip>(R.id.chipAllLogs)
            val chipSleepLogs = view.findViewById<com.google.android.material.chip.Chip>(R.id.chipSleepLogs)
            val chipEmailLogs = view.findViewById<com.google.android.material.chip.Chip>(R.id.chipEmailLogs)
            val chipAlertLogs = view.findViewById<com.google.android.material.chip.Chip>(R.id.chipAlertLogs)
            val btnRefreshLogs = view.findViewById<Button>(R.id.btnRefreshLogs)
            val btnClearLogs = view.findViewById<Button>(R.id.btnClearLogs)
            val btnExportLogs = view.findViewById<Button>(R.id.btnExportLogs)
            
            // 默认选择全部
            chipAllLogs.isChecked = true
            
            // 加载并显示日志
            loadAndDisplayLogs(tvLogContent, "all")
            
            // 过滤选项切换
            chipGroupLogFilter.setOnCheckedStateChangeListener { group, checkedIds ->
                when (checkedIds.firstOrNull()) {
                    R.id.chipAllLogs -> loadAndDisplayLogs(tvLogContent, "all")
                    R.id.chipSleepLogs -> loadAndDisplayLogs(tvLogContent, "sleep")
                    R.id.chipEmailLogs -> loadAndDisplayLogs(tvLogContent, "email")
                    R.id.chipAlertLogs -> loadAndDisplayLogs(tvLogContent, "alert")
                }
            }
            
            // 刷新按钮
            btnRefreshLogs.setOnClickListener {
                loadAndDisplayLogs(tvLogContent, when {
                    chipSleepLogs.isChecked -> "sleep"
                    chipEmailLogs.isChecked -> "email"
                    chipAlertLogs.isChecked -> "alert"
                    else -> "all"
                })
                Toast.makeText(this, "日志已刷新", Toast.LENGTH_SHORT).show()
            }
            
            // 清空按钮
            btnClearLogs.setOnClickListener {
                clearLogs()
                tvLogContent.text = "日志已清空"
                Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show()
            }
            
            // 导出按钮
            btnExportLogs.setOnClickListener {
                exportLogsToExternalStorage()
            }
            
            builder.setView(view)
            builder.setTitle("系统日志")
            builder.setPositiveButton("关闭", null)
            builder.show()
            
        } catch (e: Exception) {
            Log.e("MainActivity", "显示日志对话框错误", e)
            Toast.makeText(this, "打开日志失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * ✅ 加载并显示日志
     */
    private fun loadAndDisplayLogs(textView: TextView, filterType: String) {
        val logs = StringBuilder()
        
        // ✅ 使用文件日志代替 logcat（更可靠）
        try {
            val logFile = File(filesDir, "app_logs.txt")
            
            if (!logFile.exists()) {
                textView.text = "暂无日志记录\n\n提示：\n1. 日志会在应用运行时自动记录\n2. 请触发一些操作（如签到、发送警报等）\n3. 然后重新打开此界面查看"
                return
            }
            
            val reader = BufferedReader(FileReader(logFile))
            var line: String?
            
            val regex = when (filterType) {
                "sleep" -> "SleepMonitorService|睡眠监测"
                "email" -> "EmailReceiverService|IMAP|邮件|checkIfAlert|parseAndSave|警报邮件"
                "alert" -> "CheckinService|警报 | 起床异常"
                else -> "" // 全部显示
            }
            
            val pattern = if (regex.isNotEmpty()) {
                regex.toRegex(RegexOption.IGNORE_CASE)
            } else {
                null
            }
            
            var matchedCount = 0
            var totalCount = 0
            val allLines = mutableListOf<String>()
            
            // 读取所有行
            while (true) {
                line = reader.readLine() ?: break
                allLines.add(line)
                totalCount++
            }
            reader.close()
            
            // 从后往前显示（最新的在前）
            for (i in allLines.size - 1 downTo 0) {
                val currentLine = allLines[i]
                
                val shouldInclude = if (pattern != null) {
                    pattern.containsMatchIn(currentLine)
                } else {
                    true
                }
                
                if (shouldInclude) {
                    logs.appendLine(currentLine)
                    matchedCount++
                    
                    if (matchedCount >= 300) {
                        break
                    }
                }
            }
            
            Log.d("MainActivity", "日志加载完成，总共读取 $totalCount 条，匹配到 $matchedCount 条日志")
            
        } catch (e: Exception) {
            logs.appendLine("读取日志失败：${e.message}")
            logs.appendLine("错误详情：${e.javaClass.simpleName}")
            Log.e("MainActivity", "读取日志失败", e)
        }
        
        // 如果日志为空，显示提示
        if (logs.isEmpty()) {
            textView.text = "暂无相关日志\n\n提示：\n1. 请先触发相关操作（如刷新邮件、发送警报等）\n2. 尝试切换到“全部”标签查看\n3. 日志会自动保存到应用内部存储"
        } else {
            textView.text = logs.toString()
        }
    }
    
    /**
     * ✅ 清空日志
     */
    private fun clearLogs() {
        try {
            // 清空文件日志
            com.livewell.untils.FileLogger.clearLogs()
            
            // 也清空 logcat（如果有权限）
            Runtime.getRuntime().exec("logcat -c")
            
            Log.i("MainActivity", "日志已清空")
        } catch (e: Exception) {
            Log.e("MainActivity", "清空日志失败", e)
        }
    }
    
    /**
     * ✅ 导出日志到外部存储
     */
    private fun exportLogsToExternalStorage() {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "AnShou_Logs_$timestamp.txt"
            
            // 保存到应用私有目录
            val file = java.io.File(cacheDir, fileName)
            val process = Runtime.getRuntime().exec("logcat -d")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            
            file.bufferedWriter().use { writer ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    writer.write(line)
                    writer.newLine()
                }
            }
            
            reader.close()
            
            // 使用 FileProvider 分享文件
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, "分享日志文件"))
            
            Toast.makeText(this, "日志文件已生成：$fileName", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            Log.e("MainActivity", "导出日志失败", e)
            Toast.makeText(this, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * ✅ 显示睡眠快照记录对话框
     */
    private fun showSleepSnapshotsDialog() {
        com.livewell.untils.AppLogger.i("SleepSnapshot", "====== 开始显示睡眠快照对话框 ======")
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_sleep_snapshots, null)
        val layoutSnapshotsList = dialogView.findViewById<LinearLayout>(R.id.layoutSnapshotsList)
        val btnClose = dialogView.findViewById<Button>(R.id.btnClose)
        
        // ✅ 新增：调试信息 - 显示所有快照键（写入文件日志）
        val allPrefs = prefsManager.getAllPrefs()
        val snapshotKeys = allPrefs.keys.filter { it.startsWith("pre_sleep_") }
        com.livewell.untils.AppLogger.i("SleepSnapshot", "🔍 调试：SharedPreferences 中所有快照键（共${snapshotKeys.size}个）：")
        if (snapshotKeys.isEmpty()) {
            com.livewell.untils.AppLogger.w("SleepSnapshot", "⚠️ 未找到任何快照数据！")
        } else {
            snapshotKeys.forEach { key ->
                com.livewell.untils.AppLogger.i("SleepSnapshot", "   $key = ${allPrefs[key]}")
            }
        }
        
        // 获取最近 7 天的快照
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        
        var hasSnapshots = false
        var snapshotCount = 0
        
        for (i in 0 until 7) {
            val dateStr = dateFormat.format(calendar.time)
            
            val snapshot = prefsManager.getPreSleepSnapshot(dateStr)
            
            if (snapshot != null) {
                hasSnapshots = true
                snapshotCount++
                val (steps, usage, sleepTime) = snapshot
                
                com.livewell.untils.AppLogger.i("SleepSnapshot", "✅ 找到快照 [$snapshotCount]：日期=$dateStr, 步数=$steps, 使用=${usage}分钟, 入睡时间=${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(sleepTime))}")
                
                // 创建快照卡片
                val cardView = com.google.android.material.card.MaterialCardView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 0, 0, 8)
                    }
                    radius = 8f
                    cardElevation = 2f
                }
                
                val cardContent = LinearLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.VERTICAL
                    setPadding(32, 24, 32, 24)
                }
                
                // 日期标题
                val tvDate = TextView(this).apply {
                    text = "日期：$dateStr"
                    textSize = 16f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(getColor(R.color.primary))
                }
                
                // 入睡时间
                val sleepTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(sleepTime))
                val tvSleepTime = TextView(this).apply {
                    text = "入睡时间：$sleepTimeStr"
                    textSize = 14f
                    setTextColor(getColor(R.color.text_primary))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 8
                    }
                }
                
                // 步数
                val tvSteps = TextView(this).apply {
                    text = "睡前步数：$steps 步"
                    textSize = 14f
                    setTextColor(getColor(R.color.text_primary))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 4
                    }
                }
                
                // 使用时长
                val tvUsage = TextView(this).apply {
                    text = "睡前使用时长：$usage 分钟"
                    textSize = 14f
                    setTextColor(getColor(R.color.text_primary))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 4
                    }
                }
                
                cardContent.addView(tvDate)
                cardContent.addView(tvSleepTime)
                cardContent.addView(tvSteps)
                cardContent.addView(tvUsage)
                cardView.addView(cardContent)
                
                layoutSnapshotsList.addView(cardView)
            }
            
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        
        com.livewell.untils.AppLogger.i("SleepSnapshot", "📊 检查结果：hasSnapshots=$hasSnapshots, 共找到 $snapshotCount 个快照")
        
        if (!hasSnapshots) {
            com.livewell.untils.AppLogger.w("SleepSnapshot", "⚠️ 界面显示：暂无快照记录")
            
            val tvNoData = TextView(this).apply {
                text = "暂无快照记录\n\n提示：\n• 快照会在检测到入睡时自动保存\n• 请确保睡眠监测服务正在运行\n• 尝试睡一觉后再来查看"
                textSize = 14f
                setTextColor(getColor(R.color.text_secondary))
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 40, 0, 40)
                }
            }
            layoutSnapshotsList.addView(tvNoData)
        }
        
        // 创建对话框
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        btnClose.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    /**
     * ✅ 公开方法：显示睡眠快照记录（用于测试）
     */
    fun showSleepSnapshotsForTest() {
        showSleepSnapshotsDialog()
    }
    
    /**
     * ✅ 更新数据显示（绝对/相对数据切换）
     */
    private fun updateDataDisplay(
        tvAppUsage: TextView,
        tvStepCount: TextView,
        tvStepThreshold: TextView,
        showRelative: Boolean
    ) {
        if (showRelative) {
            // 显示相对数据（排除0点到睡觉前）
            val (stepCount, usageMinutes) = prefsManager.getCompleteDayActivity(this)
            
            tvAppUsage.text = "${usageMinutes} 分钟"
            tvStepCount.text = "${stepCount} 步"
            
            // 设置颜色
            val stepThreshold = prefsManager.getStepThreshold()
            tvStepThreshold.text = "目标：${stepThreshold} 步"
            
            if (prefsManager.isStepMonitorEnabled()) {
                if (stepCount >= stepThreshold) {
                    tvStepCount.setTextColor(getColor(R.color.success))
                } else {
                    tvStepCount.setTextColor(getColor(R.color.error))
                }
            } else {
                tvStepCount.setTextColor(getColor(R.color.text_secondary))
            }
            
            android.util.Log.d("DataDisplay", "显示相对数据：使用时长=${usageMinutes}分钟, 步数=${stepCount}步")
        } else {
            // 显示绝对数据（从0点开始的累计）
            val appUsage = usageStatsHelper.getTodayAppUsageMinutes()
            val stepCount = getTodayStepCount()
            
            tvAppUsage.text = "${appUsage} 分钟"
            tvStepCount.text = "${stepCount} 步"
            
            // 设置颜色
            val stepThreshold = prefsManager.getStepThreshold()
            tvStepThreshold.text = "目标：${stepThreshold} 步"
            
            if (prefsManager.isStepMonitorEnabled()) {
                if (stepCount >= stepThreshold) {
                    tvStepCount.setTextColor(getColor(R.color.success))
                } else {
                    tvStepCount.setTextColor(getColor(R.color.error))
                }
            } else {
                tvStepCount.setTextColor(getColor(R.color.text_secondary))
            }
            
            android.util.Log.d("DataDisplay", "显示绝对数据：使用时长=${appUsage}分钟, 步数=${stepCount}步")
        }
    }
}

