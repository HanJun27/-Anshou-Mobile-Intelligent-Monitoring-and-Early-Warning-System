package com.livewell

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.livewell.service.MailSender
import com.livewell.service.EmailGuideOverlayService
import com.livewell.untils.PrefsManager
import com.livewell.untils.SecurePrefsManager
import com.livewell.model.GuardianTarget

class SettingsGuideActivity : AppCompatActivity() {
    
    internal lateinit var viewPager: ViewPager2
    private lateinit var btnPrevious: Button
    private lateinit var btnNext: Button
    internal lateinit var prefsManager: PrefsManager
    
    internal var currentStep = 0
    private var totalSteps = 9  // 初始值，会在 loadFragmentsForMode 中根据模式动态更新
    
    // 用户选择的模式
    internal var selectedMode: String? = null
    
    private val fragments = mutableListOf<Fragment>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_guide)
        
        prefsManager = PrefsManager(this)
        
        viewPager = findViewById(R.id.viewPager)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnNext = findViewById(R.id.btnNext)
        
        // ✅ 检查是否有之前保存的模式（且不是默认值）
        val savedMode = prefsManager.getAppMode()
        val hasExplicitlySetMode = prefsManager.hasExplicitlySetAppMode()
        
        android.util.Log.d("SettingsGuide", "启动时检查: savedMode=$savedMode, hasExplicitlySetMode=$hasExplicitlySetMode")
        
        if (hasExplicitlySetMode && (savedMode == PrefsManager.MODE_GUARDIAN || 
                                   savedMode == PrefsManager.MODE_RECEIVER || 
                                   savedMode == PrefsManager.MODE_MIXED)) {
            // 有明确保存的模式，直接加载对应的 Fragment
            android.util.Log.d("SettingsGuide", "✅ 发现已保存的模式: $savedMode，直接加载")
            loadFragmentsForMode(savedMode)
        } else {
            // 没有保存的模式或只有默认值，初始化基础 Fragment
            android.util.Log.d("SettingsGuide", "⚠️ 未找到已保存的模式，初始化基础 Fragment")
            initFragments()
        }
        
        setupViewPager()
        updateButtons()
    }
    
    private fun initFragments() {
        // 第 0 个界面：权限与安全说明
        fragments.add(PermissionsFragment())
        
        // 第一个界面：模式选择
        fragments.add(ModeSelectionFragment())
        
        // 被守护模式的 Fragment（原样保留）
        val guardianModeFragments = listOf(
            AutoAlertModeFragment(),
            AlertCriteriaFragment(),
            AlertTimeFragment(),
            ThresholdFragment(),
            ConfirmMechanismFragment(),
            EmailRecipientFragment(),
            SleepMonitorFragment()
        )
        
        // 守护模式的 Fragment（新增）
        val receiverModeFragments = listOf(
            EmailProviderFragment(),  // 邮箱配置
            GuardianTargetsFragment() // 添加被守护人
        )
        
        // 根据后续选择的模式动态添加 Fragment
        // 默认先不添加，等用户选择模式后再添加
    }
    
    private fun setupViewPager() {
        val savedStep = prefsManager.getSettingsGuideStep()
        // ✅ 确保保存的步骤不超过当前总步骤数
        currentStep = if (savedStep >= 0 && savedStep < fragments.size) savedStep else 0
        
        viewPager.adapter = SettingsGuideAdapter(this, fragments)
        viewPager.currentItem = currentStep
        viewPager.offscreenPageLimit = 1
        
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                android.util.Log.d("ViewPager", "📄 页面切换: position=$position, previous currentStep=$currentStep")
                currentStep = position
                prefsManager.saveSettingsGuideStep(position)
                updateButtons()
                
                // ✅ 更新当前页面的步骤数显示
                updateCurrentFragmentStepIndicator(position)
                
                android.util.Log.d("ViewPager", "✅ currentStep 已更新为: $currentStep")
            }
        })
    }
    
    private fun updateButtons() {
        btnPrevious.visibility = if (currentStep == 0) View.GONE else View.VISIBLE
        btnPrevious.isEnabled = currentStep > 0
            
        if (currentStep == 0) {
            // 权限页面，显示“下一步”
            btnNext.text = "下一步"
        } else if (currentStep == totalSteps - 1) {
            btnNext.text = "完成"
        } else {
            btnNext.text = "下一步"
        }
        
        btnNext.setOnClickListener {
            android.util.Log.d("NextButton", "点击下一步: currentStep=$currentStep, selectedMode=$selectedMode, totalSteps=$totalSteps, fragments.size=${fragments.size}")
            
            if (currentStep == 0) {
                // 在权限页面，检查是否需要提醒
                checkPermissionsAndProceed()
            } else if (currentStep == 1 && selectedMode == null) {
                // ✅ 在模式选择页面但未选择模式，提示用户
                android.util.Log.w("NextButton", "⚠️ 未选择模式，显示提示对话框")
                showModeSelectionRequiredDialog()
            } else if (currentStep < totalSteps - 1) {
                android.util.Log.d("NextButton", "✅ 跳转到下一步: ${currentStep + 1}")
                viewPager.currentItem = currentStep + 1
            } else {
                android.util.Log.d("NextButton", "✅ 完成引导")
                finishGuide()
            }
        }
        
        btnPrevious.setOnClickListener {
            if (currentStep > 0) {
                viewPager.currentItem = currentStep - 1
            }
        }
    }
    
    /**
     * ✅ 更新当前 Fragment 的步骤数显示
     */
    private fun updateCurrentFragmentStepIndicator(position: Int) {
        try {
            val fragment = fragments[position]
            val view = fragment.view
                
            if (view != null) {
                // 查找步骤数 TextView（ID: tvStepIndicator）
                val tvStepIndicator = view.findViewById<android.widget.TextView>(
                    resources.getIdentifier("tvStepIndicator", "id", packageName)
                )
                    
                if (tvStepIndicator != null) {
                    tvStepIndicator.text = "第 ${position + 1} 步，共 ${totalSteps} 步"
                    android.util.Log.d("StepIndicator", "✅ 更新步骤数: ${position + 1} / $totalSteps")
                } else {
                    android.util.Log.w("StepIndicator", "⚠️ 未找到 tvStepIndicator")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("StepIndicator", "❌ 更新步骤数失败: ${e.message}")
        }
    }
    
    internal fun finishGuide() {
        // ✅ 标记设置引导已完成，下次启动不再显示
        prefsManager.setSettingsGuideShown(true)
        prefsManager.saveSettingsGuideStep(0)
        finish()
    }
    
    /**
     * 根据选择的模式加载后续 Fragment
     */
    internal fun loadFragmentsForMode(mode: String) {
        selectedMode = mode
        
        // ✅ 立即保存模式到 PrefsManager
        prefsManager.saveAppMode(mode)
        
        // 保留权限页面（第一个）
        val permissionFragment = fragments.firstOrNull()
        fragments.clear()
        if (permissionFragment != null) {
            fragments.add(permissionFragment)
        }
        
        // 添加模式选择页面
        fragments.add(ModeSelectionFragment())
        
        when (mode) {
            PrefsManager.MODE_GUARDIAN -> {
                // 被守护模式：本机用户，发送警报给紧急联系人
                fragments.add(AutoAlertModeFragment())           // 自动报警开关
                fragments.add(AlertCriteriaFragment())           // 报警判断标准
                fragments.add(AlertTimeFragment())               // 报警检查时间
                fragments.add(ThresholdFragment())               // 报警阈值（步数 + 使用时长）
                fragments.add(ConfirmMechanismFragment())        // 报警确认机制
                fragments.add(EmailProviderFragment())           // 发件人邮箱配置（SMTP）
                fragments.add(EmailRecipientFragment())          // 收件人邮箱配置（紧急联系人）
                fragments.add(SleepMonitorFragment())            // 睡眠监测设置
            }
            PrefsManager.MODE_RECEIVER -> {
                // 守护模式：监控他人，接收警报邮件
                fragments.add(EmailProviderFragment())           // 发件人邮箱配置（IMAP，用于接收邮件）
                fragments.add(GuardianTargetsFragment())         // 添加被守护人（监控对象）
            }
            PrefsManager.MODE_MIXED -> {
                // 混合模式：同时具备被守护和守护功能
                // 第一部分：守护功能配置
                fragments.add(EmailProviderFragment())           // 发件人邮箱配置（SMTP + IMAP）
                fragments.add(GuardianTargetsFragment())         // 添加被守护人
                
                // 第二部分：被守护功能配置
                fragments.add(AutoAlertModeFragment())           // 自动报警开关
                fragments.add(AlertCriteriaFragment())           // 报警判断标准
                fragments.add(AlertTimeFragment())               // 报警检查时间
                fragments.add(ThresholdFragment())               // 报警阈值（步数 + 使用时长）
                fragments.add(ConfirmMechanismFragment())        // 报警确认机制
                fragments.add(EmailRecipientFragment())          // 收件人邮箱配置（紧急联系人）
                fragments.add(SleepMonitorFragment())            // 睡眠监测设置
            }
        }
        
        // 更新适配器并刷新
        viewPager.adapter = SettingsGuideAdapter(this, fragments)
        
        // ✅ 根据实际 Fragment 数量更新 totalSteps
        totalSteps = fragments.size
        
        viewPager.currentItem = 0
        currentStep = 0
        updateButtons()
    }
    
    override fun onBackPressed() {
        if (currentStep > 0) {
            viewPager.currentItem = currentStep - 1
        } else {
            prefsManager.saveSettingsGuideStep(currentStep)
            finish()
        }
    }
    
    /**
     * 检查权限并在需要时显示警告对话框
     */
    private fun checkPermissionsAndProceed() {
        val permissionsFragment = fragments[0] as? PermissionsFragment
        if (permissionsFragment == null) {
            viewPager.currentItem = 1
            return
        }
        
        if (!permissionsFragment.areAllPermissionsGranted()) {
            // 有权限未授予，显示警告对话框
            showPermissionWarningDialog(permissionsFragment)
        } else {
            // 所有权限已授予，继续下一步
            viewPager.currentItem = 1
        }
    }
    
    /**
     * 显示权限警告对话框
     */
    private fun showPermissionWarningDialog(permissionsFragment: PermissionsFragment) {
        val ungrantedPermissions = permissionsFragment.getUngrantedPermissions()
        
        MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ 权限设置提醒")
            .setMessage("""您还有以下权限未设置：

• ${ungrantedPermissions.joinToString("\n• ")}

❗ 缺少这些权限可能导致：
${getPermissionWarningMessage(ungrantedPermissions)}

是否现在前往设置？""")
            .setPositiveButton("现在获取") { _, _ ->
                // 重新弹出权限请求
                requestUngrantedPermissions(ungrantedPermissions)
            }
            .setNegativeButton("稍后获取") { _, _ ->
                // 允许用户继续，但保存状态
                prefsManager.setPermissionsRemindLater(true)
                viewPager.currentItem = 1
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 提示用户必须选择模式
     */
    private fun showModeSelectionRequiredDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("提示")
            .setMessage("请先选择一个使用模式，然后继续下一步")
            .setPositiveButton("确定", null)
            .show()
    }
    
    /**
     * 根据未授予的权限返回警告信息
     */
    private fun getPermissionWarningMessage(ungrantedPermissions: List<String>): String {
        val warnings = mutableListOf<String>()
        
        for (permission in ungrantedPermissions) {
            when (permission) {
                "无障碍服务" -> warnings.add("• 无法使用邮箱配置悬浮窗引导")
                "通知" -> warnings.add("• 无法接收警报通知和状态提醒")
                "剪贴板" -> warnings.add("• 需要手动输入邮箱授权码")
                "悬浮窗" -> warnings.add("• 无法显示邮箱配置引导悬浮窗")
            }
        }
        
        return warnings.joinToString("\n")
    }
    
    /**
     * 请求未授予的权限
     */
    private fun requestUngrantedPermissions(ungrantedPermissions: List<String>) {
        val permissionsFragment = fragments[0] as? PermissionsFragment ?: return
        
        for (permission in ungrantedPermissions) {
            when (permission) {
                "无障碍服务" -> permissionsFragment.showAccessibilityPermissionDialog()
                "通知" -> permissionsFragment.showNotificationPermissionDialog()
                "剪贴板" -> {
                    // 剪贴板权限只需勾选即可，不需要额外请求
                    permissionsFragment.setClipboardChecked()
                }
                "悬浮窗" -> permissionsFragment.showOverlayPermissionDialog()
            }
        }
    }
}

// ========== Fragment 基类 ==========
abstract class SettingsGuideFragment : Fragment() {
    protected lateinit var prefsManager: PrefsManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsManager = PrefsManager(requireContext())
    }
    
    /**
     * ✅ 更新步骤数显示
     */
    protected fun updateStepIndicator(currentStep: Int, totalSteps: Int) {
        try {
            val activity = requireActivity() as? SettingsGuideActivity
            if (activity != null) {
                // 查找所有可能的步骤数 TextView（不同界面可能有不同的 ID）
                val stepTextViews = listOf(
                    "tvStepIndicator",  // 通用 ID
                    "tvStepCount"       // 备用 ID
                )
                
                for (idName in stepTextViews) {
                    val resId = resources.getIdentifier(idName, "id", requireContext().packageName)
                    if (resId != 0) {
                        val tvStep = view?.findViewById<android.widget.TextView>(resId)
                        tvStep?.text = "第 ${currentStep + 1} 步，共 ${totalSteps} 步"
                        break
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsGuideFragment", "更新步骤数失败: ${e.message}")
        }
    }
}

// ========== 第 0 步：权限与安全说明 ==========
class PermissionsFragment : SettingsGuideFragment() {
    
    private var _binding: View? = null
    private val binding get() = _binding!!
    
    private lateinit var cbAccessibility: CheckBox
    private lateinit var cbNotification: CheckBox
    private lateinit var cbClipboard: CheckBox
    private lateinit var cbOverlay: CheckBox
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = inflater.inflate(R.layout.fragment_guide_permissions, container, false)
        return _binding
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        cbAccessibility = view.findViewById(R.id.cbAccessibility)
        cbNotification = view.findViewById(R.id.cbNotification)
        cbClipboard = view.findViewById(R.id.cbClipboard)
        cbOverlay = view.findViewById(R.id.cbOverlay)
        
        // 加载已保存的权限状态
        loadPermissionStates()
        
        // 监听复选框变化
        setupCheckBoxListeners()
        
        // ✅ 进入界面时自动弹窗请求权限（延迟 500ms，等待 UI 加载完成）
        view.postDelayed({
            autoRequestPermissions()
        }, 500)
    }
    
    override fun onResume() {
        super.onResume()
        // ✅ 每次返回页面时，同步系统实际的权限状态
        syncSystemPermissionStates()
    }
    
    private fun loadPermissionStates() {
        cbAccessibility.isChecked = prefsManager.isAccessibilityPermissionGranted()
        cbNotification.isChecked = prefsManager.isNotificationPermissionGranted()
        cbClipboard.isChecked = prefsManager.isClipboardPermissionGranted()
        cbOverlay.isChecked = prefsManager.isOverlayPermissionGranted()
    }
    
    /**
     * 同步系统实际的权限状态到 UI 和 SharedPreferences
     */
    private fun syncSystemPermissionStates() {
        // 1. 检查无障碍服务是否真的开启
        val isAccessibilityEnabled = isAccessibilityServiceEnabled()
        if (isAccessibilityEnabled != cbAccessibility.isChecked) {
            cbAccessibility.isChecked = isAccessibilityEnabled
            prefsManager.setAccessibilityPermissionGranted(isAccessibilityEnabled)
        }
        
        // 2. 检查通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotification = requireContext().checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (hasNotification != cbNotification.isChecked) {
                cbNotification.isChecked = hasNotification
                prefsManager.setNotificationPermissionGranted(hasNotification)
            }
        }
        
        // 3. 检查悬浮窗权限（Android 6.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val hasOverlay = android.provider.Settings.canDrawOverlays(requireContext())
            if (hasOverlay != cbOverlay.isChecked) {
                cbOverlay.isChecked = hasOverlay
                prefsManager.setOverlayPermissionGranted(hasOverlay)
            }
        }
        
        // 4. 剪贴板权限不需要检查系统状态（只是用户确认标记）
    }
    
    /**
     * 检查无障碍服务是否已启用
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = requireContext().getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == requireContext().packageName) {
                return true
            }
        }
        return false
    }
    
    private fun setupCheckBoxListeners() {
        cbAccessibility.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setAccessibilityPermissionGranted(isChecked)
            if (isChecked) {
                showAccessibilityPermissionDialog()
            }
        }
        
        cbNotification.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setNotificationPermissionGranted(isChecked)
            if (isChecked) {
                showNotificationPermissionDialog()
            }
        }
        
        cbClipboard.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setClipboardPermissionGranted(isChecked)
        }
        
        cbOverlay.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setOverlayPermissionGranted(isChecked)
            if (isChecked) {
                showOverlayPermissionDialog()
            }
        }
    }
    
    /**
     * 显示无障碍权限对话框
     */
    internal fun showAccessibilityPermissionDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🔐 无障碍服务权限")
            .setMessage("""无障碍服务用于：
• 检测浏览器页面变化
• 显示悬浮窗引导您配置邮箱
• 仅在邮箱配置时使用

💡 温馨提示：
悬浮窗界面支持拖动，您可以按住顶部的≡图标区域自由调整位置，避免遮挡操作区域。

是否前往设置？""")
            .setPositiveButton("去设置") { _, _ ->
                openAccessibilitySettings()
            }
            .setNegativeButton("取消") { _, _ ->
                cbAccessibility.isChecked = false
            }
            .show()
    }
    
    /**
     * 显示通知权限对话框
     */
    internal fun showNotificationPermissionDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (requireContext().checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), REQUEST_NOTIFICATION_PERMISSION)
            }
        }
    }
    
    /**
     * 显示悬浮窗权限对话框
     */
    internal fun showOverlayPermissionDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(requireContext())) {
                val intent = android.content.Intent().apply {
                    action = android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                    data = android.net.Uri.parse("package:${requireContext().packageName}")
                }
                startActivity(intent)
            }
        }
    }
    
    /**
     * 打开无障碍服务设置
     */
    private fun openAccessibilitySettings() {
        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
    
    /**
     * 自动请求所有未授予的权限（一个接一个）
     */
    private fun autoRequestPermissions() {
        val ungrantedPermissions = getUngrantedPermissions()
        if (ungrantedPermissions.isNotEmpty()) {
            requestNextPermission(ungrantedPermissions, 0)
        }
    }
    
    /**
     * 递归请求下一个权限
     */
    private fun requestNextPermission(permissions: List<String>, index: Int) {
        if (index >= permissions.size) return
        
        val permission = permissions[index]
        when (permission) {
            "无障碍服务" -> {
                cbAccessibility.isChecked = true
                showAccessibilityPermissionDialog()
            }
            "通知" -> {
                cbNotification.isChecked = true
                showNotificationPermissionDialog()
            }
            "剪贴板" -> {
                cbClipboard.isChecked = true
                prefsManager.setClipboardPermissionGranted(true)
                // 剪贴板不需要弹窗，直接请求下一个
                requestNextPermission(permissions, index + 1)
            }
            "悬浮窗" -> {
                cbOverlay.isChecked = true
                showOverlayPermissionDialog()
            }
            "使用统计" -> {
                showUsageStatsPermissionDialog()
                // 使用统计需要跳转到设置页面，等待用户返回后继续
            }
            "电池优化" -> {
                showBatteryOptimizationDialog()
                // 电池优化需要跳转到设置页面，等待用户返回后继续
            }
            "精确闹钟" -> {
                showExactAlarmPermissionDialog()
                // 精确闹钟需要跳转到设置页面，等待用户返回后继续
            }
        }
    }
    
    /**
     * 检查是否所有权限都已授予
     */
    fun areAllPermissionsGranted(): Boolean {
        return cbAccessibility.isChecked && 
               cbNotification.isChecked && 
               cbClipboard.isChecked && 
               cbOverlay.isChecked
    }
    
    /**
     * 获取未授予的权限列表
     */
    fun getUngrantedPermissions(): List<String> {
        val ungranted = mutableListOf<String>()
        if (!cbAccessibility.isChecked) ungranted.add("无障碍服务")
        if (!cbNotification.isChecked) ungranted.add("通知")
        if (!cbClipboard.isChecked) ungranted.add("剪贴板")
        if (!cbOverlay.isChecked) ungranted.add("悬浮窗")
        
        // ✅ 新增：检查其他系统权限
        if (!hasUsageStatsPermission()) ungranted.add("使用统计")
        if (!isBatteryOptimizationDisabled()) ungranted.add("电池优化")
        if (!hasExactAlarmPermission()) ungranted.add("精确闹钟")
        
        return ungranted
    }
    
    /**
     * 设置剪贴板权限为已勾选
     */
    fun setClipboardChecked() {
        cbClipboard.isChecked = true
        prefsManager.setClipboardPermissionGranted(true)
    }
    
    /**
     * ✅ 检查使用统计权限
     */
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = requireContext().getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                requireContext().packageName
            )
        } else {
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                requireContext().packageName
            )
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }
    
    /**
     * ✅ 检查电池优化是否已禁用
     */
    private fun isBatteryOptimizationDisabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            return powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)
        }
        return true // Android 6.0 以下不需要检查
    }
    
    /**
     * ✅ 检查精确闹钟权限
     */
    private fun hasExactAlarmPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }
        return true // Android 12 以下不需要检查
    }
    
    /**
     * ✅ 显示使用统计权限对话框
     */
    private fun showUsageStatsPermissionDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📊 使用统计权限")
            .setMessage("""为了检测您今天是否使用过手机，我们需要读取使用统计权限。

此权限用于：
• 计算今日应用使用时长
• 判断是否达到报警阈值

是否前往设置？""")
            .setPositiveButton("去设置") { _, _ ->
                startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * ✅ 显示电池优化权限对话框
     */
    private fun showBatteryOptimizationDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("🔋 电池优化权限")
                .setMessage("""为了确保服务稳定运行，我们需要关闭电池优化。

此设置用于：
• 防止后台服务被系统杀死
• 确保定时任务正常执行

是否前往设置？""")
                .setPositiveButton("去设置") { _, _ ->
                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = android.net.Uri.parse("package:${requireContext().packageName}")
                    startActivity(intent)
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
    
    /**
     * ✅ 显示精确闹钟权限对话框
     */
    private fun showExactAlarmPermissionDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("⏰ 精确闹钟权限")
                .setMessage("""Android 12+ 需要精确闹钟权限才能准时触发报警。

此权限用于：
• 在预设时间准时检查状态
• 确保睡眠监测闹钟准确

是否前往设置？""")
                .setPositiveButton("去设置") { _, _ ->
                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    startActivity(intent)
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
    
    companion object {
        const val REQUEST_NOTIFICATION_PERMISSION = 1001
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ========== 第 1 步：自动报警模式选择 ==========
class AutoAlertModeFragment : SettingsGuideFragment() {
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_guide_auto_alert, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val switchAutoAlert = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchAutoAlert)
        val tvStatusBadge = view.findViewById<TextView>(R.id.tvStatusBadge)
        
        val prefsManager = PrefsManager(requireContext())
        val isEnabled = prefsManager.isAutoAlertModeEnabled()
        
        switchAutoAlert.isChecked = isEnabled
        
        // ✅ 根据开关状态更新徽章显示
        updateAutoAlertBadge(isEnabled, tvStatusBadge)
        
        switchAutoAlert.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setAutoAlertModeEnabled(isChecked)
            updateAutoAlertBadge(isChecked, tvStatusBadge)
        }
    }
    
    /**
     * ✅ 更新自动报警状态徽章
     */
    private fun updateAutoAlertBadge(isEnabled: Boolean, badge: TextView) {
        if (isEnabled) {
            badge.text = "ACTIVE PROTECTION MODE"
            badge.setTextColor(android.graphics.Color.parseColor("#AE2F34"))
            badge.visibility = View.VISIBLE
        } else {
            badge.text = "PROTECTION OFF"
            badge.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
            badge.visibility = View.VISIBLE
        }
    }
}

// ========== 第 2 步：报警方式选择 ==========
class AlertCriteriaFragment : SettingsGuideFragment() {
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_guide_alert_criteria, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val radioGroup = view.findViewById<android.widget.RadioGroup>(R.id.radioGroupCriteria)
        val cardStep = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardStep)
        val cardUsage = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardUsage)
        val cardMixed = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardMixed)
        val prefsManager = PrefsManager(requireContext())
        
        when (prefsManager.getAlertCriteria()) {
            PrefsManager.CRITERIA_STEP_ONLY -> radioGroup.check(R.id.radioStep)
            PrefsManager.CRITERIA_USAGE_ONLY -> radioGroup.check(R.id.radioUsage)
            else -> radioGroup.check(R.id.radioMixed)
        }
        
        // ✅ 初始化时更新卡片边框颜色
        updateCriteriaCardBorderColors(radioGroup.checkedRadioButtonId, cardStep, cardUsage, cardMixed)
        
        // ✅ 监听 RadioGroup 选择变化
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val criteria = when (checkedId) {
                R.id.radioStep -> PrefsManager.CRITERIA_STEP_ONLY
                R.id.radioUsage -> PrefsManager.CRITERIA_USAGE_ONLY
                else -> PrefsManager.CRITERIA_MIXED
            }
            prefsManager.setAlertCriteria(criteria)
            updateCriteriaCardBorderColors(checkedId, cardStep, cardUsage, cardMixed)
        }
        
        // ✅ 为步数检测卡片添加点击事件
        cardStep.setOnClickListener {
            radioGroup.check(R.id.radioStep)
        }
        
        // ✅ 为使用时长检测卡片添加点击事件
        cardUsage.setOnClickListener {
            radioGroup.check(R.id.radioUsage)
        }
        
        // ✅ 为混合模式卡片添加点击事件
        cardMixed.setOnClickListener {
            radioGroup.check(R.id.radioMixed)
        }
    }
    
    /**
     * ✅ 更新报警标准卡片边框颜色，选中时红色，未选中时灰色
     */
    private fun updateCriteriaCardBorderColors(
        checkedId: Int,
        cardStep: com.google.android.material.card.MaterialCardView,
        cardUsage: com.google.android.material.card.MaterialCardView,
        cardMixed: com.google.android.material.card.MaterialCardView
    ) {
        val selectedColor = android.graphics.Color.parseColor("#ff6b6b") // 红色
        val unselectedColor = android.graphics.Color.parseColor("#CBD5E1") // 灰色
        
        when (checkedId) {
            R.id.radioStep -> {
                cardStep.setStrokeColor(selectedColor)
                cardUsage.setStrokeColor(unselectedColor)
                cardMixed.setStrokeColor(unselectedColor)
            }
            R.id.radioUsage -> {
                cardStep.setStrokeColor(unselectedColor)
                cardUsage.setStrokeColor(selectedColor)
                cardMixed.setStrokeColor(unselectedColor)
            }
            R.id.radioMixed -> {
                cardStep.setStrokeColor(unselectedColor)
                cardUsage.setStrokeColor(unselectedColor)
                cardMixed.setStrokeColor(selectedColor)
            }
        }
    }
}

// ========== 第 3 步：报警时间设置 ==========
class AlertTimeFragment : SettingsGuideFragment() {
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_guide_alert_time, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val tvHour = view.findViewById<TextView>(R.id.tvAlertHour)
        val tvMinute = view.findViewById<TextView>(R.id.tvAlertMinute)
        val btnHourPlus = view.findViewById<android.widget.ImageButton>(R.id.btnHourPlus)
        val btnHourMinus = view.findViewById<android.widget.ImageButton>(R.id.btnHourMinus)
        val btnMinutePlus = view.findViewById<android.widget.ImageButton>(R.id.btnMinutePlus)
        val btnMinuteMinus = view.findViewById<android.widget.ImageButton>(R.id.btnMinuteMinus)
        val tvDescription = view.findViewById<TextView>(R.id.tvTimeDescription)
        
        val prefsManager = PrefsManager(requireContext())
        var hour = prefsManager.getAlertCheckHour()
        var minute = prefsManager.getAlertCheckMinute()
        
        updateDescription(tvDescription, hour, minute)
        tvHour.text = hour.toString().padStart(2, '0')
        tvMinute.text = minute.toString().padStart(2, '0')
        
        btnHourPlus.setOnClickListener {
            hour = (hour + 1) % 24
            tvHour.text = hour.toString().padStart(2, '0')
            prefsManager.setAlertCheckTime(hour, minute)
            updateDescription(tvDescription, hour, minute)
        }
        
        btnHourMinus.setOnClickListener {
            hour = if (hour > 0) hour - 1 else 23
            tvHour.text = hour.toString().padStart(2, '0')
            prefsManager.setAlertCheckTime(hour, minute)
            updateDescription(tvDescription, hour, minute)
        }
        
        btnMinutePlus.setOnClickListener {
            minute = (minute + 5) % 60
            tvMinute.text = minute.toString().padStart(2, '0')
            prefsManager.setAlertCheckTime(hour, minute)
            updateDescription(tvDescription, hour, minute)
        }
        
        btnMinuteMinus.setOnClickListener {
            minute = if (minute > 0) minute - 5 else 55
            tvMinute.text = minute.toString().padStart(2, '0')
            prefsManager.setAlertCheckTime(hour, minute)
            updateDescription(tvDescription, hour, minute)
        }
    }
    
    private fun updateDescription(tv: TextView, hour: Int, minute: Int) {
        tv.text = """
            报警时间说明：
            
            系统会在每天 ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} 开始检测
            如果您的设备在此时间之后没有使用或步数过少
            系统将自动发送警报邮件
            
            请设置您希望的报警检测时间
        """.trimIndent()
    }
}

// ========== 第 4 步：阈值设置 ==========
class ThresholdFragment : SettingsGuideFragment() {
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_guide_threshold, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val tvStepValue = view.findViewById<TextView>(R.id.tvStepValue)
        val tvDurationValue = view.findViewById<TextView>(R.id.tvDurationValue)
        val btnStepPlus = view.findViewById<android.widget.ImageButton>(R.id.btnStepPlus)
        val btnStepMinus = view.findViewById<android.widget.ImageButton>(R.id.btnStepMinus)
        val btnDurationPlus = view.findViewById<android.widget.ImageButton>(R.id.btnDurationPlus)
        val btnDurationMinus = view.findViewById<android.widget.ImageButton>(R.id.btnDurationMinus)
        
        val prefsManager = PrefsManager(requireContext())
        var stepThreshold = prefsManager.getStepThreshold()
        var usageThreshold = prefsManager.getAppUsageThreshold()  // ✅ 修改为使用时长阈值
        
        tvStepValue.text = stepThreshold.toString()
        tvDurationValue.text = usageThreshold.toString()  // ✅ 显示使用时长阈值
        
        btnStepPlus.setOnClickListener {
            stepThreshold += 50
            tvStepValue.text = stepThreshold.toString()
            prefsManager.setStepThreshold(stepThreshold)
        }
        
        btnStepMinus.setOnClickListener {
            if (stepThreshold > 50) {
                stepThreshold -= 50
                tvStepValue.text = stepThreshold.toString()
                prefsManager.setStepThreshold(stepThreshold)
            }
        }
        
        btnDurationPlus.setOnClickListener {
            usageThreshold++
            tvDurationValue.text = usageThreshold.toString()
            prefsManager.setAppUsageThreshold(usageThreshold)  // ✅ 保存为使用时长阈值
        }
        
        btnDurationMinus.setOnClickListener {
            if (usageThreshold > 1) {
                usageThreshold--
                tvDurationValue.text = usageThreshold.toString()
                prefsManager.setAppUsageThreshold(usageThreshold)  // ✅ 保存为使用时长阈值
            }
        }
    }
}

// ========== 第 5 步：报警确认机制 ==========
class ConfirmMechanismFragment : SettingsGuideFragment() {
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_guide_confirm, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val switchConfirm = view.findViewById<SwitchMaterial>(R.id.switchConfirm)
        
        val prefsManager = PrefsManager(requireContext())
        switchConfirm.isChecked = prefsManager.isAlertConfirmEnabled()
        
        switchConfirm.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setAlertConfirmEnabled(isChecked)
        }
    }
}

// ========== 第 6 步：收件人邮箱设置 ==========
class EmailRecipientFragment : SettingsGuideFragment() {
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_guide_email_recipient, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val etToEmail = view.findViewById<TextInputEditText>(R.id.etToEmail)
        val prefsManager = PrefsManager(requireContext())
        
        etToEmail.setText(prefsManager.getEmailTo())
    }
    
    override fun onResume() {
        super.onResume()
        val etToEmail = view?.findViewById<TextInputEditText>(R.id.etToEmail)
        val prefsManager = PrefsManager(requireContext())
        etToEmail?.setText(prefsManager.getEmailTo())
    }
    
    override fun onPause() {
        super.onPause()
        val etToEmail = view?.findViewById<TextInputEditText>(R.id.etToEmail)
        val prefsManager = PrefsManager(requireContext())
        etToEmail?.text?.toString()?.let {
            prefsManager.setEmailTo(it)
        }
    }
}

// ========== 第 7 步：发件人邮箱配置 ==========
class EmailProviderFragment : SettingsGuideFragment() {
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_guide_email_provider, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val radioGroupProvider = view.findViewById<RadioGroup>(R.id.radioGroupProvider)
        val cardQQ = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardQQ)
        val card163 = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card163)
        val btnQQ = view.findViewById<Button>(R.id.btnQQConfig)
        val btn163 = view.findViewById<Button>(R.id.btn163Config)
        val btnTest = view.findViewById<Button>(R.id.btnTestSend)
        
        // ✅ 从 PrefsManager 读取上次配置的邮箱（通过 SMTP host 判断）
        val prefsManager = PrefsManager(requireContext())
        val smtpHost = prefsManager.getEmailSmtpHost()
        
        // ✅ 根据 SMTP host 设置初始选中状态
        val initialCheckedId = if (smtpHost.contains("163")) {
            R.id.radio163
        } else {
            R.id.radioQQ  // 默认 QQ
        }
        
        // ✅ 强制设置 RadioGroup 的选中状态（避免布局文件中的静态 checked 属性干扰）
        radioGroupProvider.check(initialCheckedId)
        
        // ✅ 初始化时只显示选中的邮箱配置按钮
        updateConfigButtonsVisibility(initialCheckedId, btnQQ, btn163)
        
        // ✅ 监听 RadioGroup 选择变化
        radioGroupProvider.setOnCheckedChangeListener { _, checkedId ->
            updateConfigButtonsVisibility(checkedId, btnQQ, btn163)
            updateCardBorderColors(checkedId, cardQQ, card163)
        }
        
        // ✅ 初始化时更新卡片边框颜色
        updateCardBorderColors(initialCheckedId, cardQQ, card163)
        
        // ✅ 为 QQ 邮箱卡片添加点击事件
        cardQQ.setOnClickListener {
            radioGroupProvider.check(R.id.radioQQ)
        }
        
        // ✅ 为 163 邮箱卡片添加点击事件
        card163.setOnClickListener {
            radioGroupProvider.check(R.id.radio163)
        }
        
        btnQQ.setOnClickListener {
            showEmailConfigDialog("QQ 邮箱", "smtp.qq.com", "465")
        }
        
        btn163.setOnClickListener {
            showEmailConfigDialogWithHelp("网易 163 邮箱", "smtp.163.com", "465", "163")
        }
        
        btnTest.setOnClickListener {
            testEmailSend()
        }
    }
    
    /**
     * ✅ 根据选择的邮箱类型显示对应的配置按钮
     */
    private fun updateConfigButtonsVisibility(checkedId: Int, btnQQ: Button, btn163: Button) {
        when (checkedId) {
            R.id.radioQQ -> {
                btnQQ.visibility = View.VISIBLE
                btn163.visibility = View.GONE
            }
            R.id.radio163 -> {
                btnQQ.visibility = View.GONE
                btn163.visibility = View.VISIBLE
            }
        }
    }
    
    /**
     * ✅ 更新卡片边框颜色，选中时红色，未选中时灰色
     */
    private fun updateCardBorderColors(checkedId: Int, cardQQ: com.google.android.material.card.MaterialCardView, card163: com.google.android.material.card.MaterialCardView) {
        val selectedColor = android.graphics.Color.parseColor("#ff6b6b") // 红色
        val unselectedColor = android.graphics.Color.parseColor("#CBD5E1") // 灰色
        
        when (checkedId) {
            R.id.radioQQ -> {
                cardQQ.setStrokeColor(selectedColor)
                card163.setStrokeColor(unselectedColor)
            }
            R.id.radio163 -> {
                cardQQ.setStrokeColor(unselectedColor)
                card163.setStrokeColor(selectedColor)
            }
        }
    }
    
    private fun showEmailConfigDialog(provider: String, defaultHost: String, defaultPort: String) {
        val prefsManager = PrefsManager(requireContext())
        val securePrefs = SecurePrefsManager(requireContext())
        
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle("$provider 配置")
        
        // ✅ 使用 requireContext().layoutInflater 而不是 activity 的 layoutInflater
        val contentView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_email_config, null)
        val etFromEmail = contentView.findViewById<TextInputEditText>(R.id.etFromEmail)
        val etAuthCode = contentView.findViewById<TextInputEditText>(R.id.etAuthCode)
        val etSmtpHost = contentView.findViewById<TextInputEditText>(R.id.etSmtpHost)
        val etSmtpPort = contentView.findViewById<TextInputEditText>(R.id.etSmtpPort)
        val btnHelpQQ = contentView.findViewById<MaterialButton>(R.id.btnHelpQQ)
        val btnHelp163 = contentView.findViewById<MaterialButton>(R.id.btnHelp163)
        
        etFromEmail.setText(securePrefs.getEmailAccount())
        etSmtpHost.setText(securePrefs.getSmtpHost() ?: defaultHost)
        etSmtpPort.setText(securePrefs.getSmtpPort() ?: defaultPort)
        
        // ✅ 根据邮箱类型显示对应的帮助按钮
        if (provider.contains("QQ")) {
            btnHelpQQ.visibility = View.VISIBLE
        } else if (provider.contains("163")) {
            btnHelp163.visibility = View.VISIBLE
        }
        
        // ✅ QQ 邮箱帮助按钮点击事件 - 分步引导
        btnHelpQQ.setOnClickListener {
            val email = etFromEmail.text.toString()
            openEmailHelpPage("qq", email)
        }
        
        // ✅ 163 邮箱帮助按钮点击事件 - 分步引导
        btnHelp163.setOnClickListener {
            val email = etFromEmail.text.toString()
            openEmailHelpPage("163", email)
        }
        
        builder.setView(contentView)
            .setPositiveButton("保存") { _, _ ->
                val fromEmail = etFromEmail.text.toString()
                val authCode = etAuthCode.text.toString()
                val host = etSmtpHost.text.toString()
                val port = etSmtpPort.text.toString()
                
                if (fromEmail.isNotEmpty() && authCode.isNotEmpty()) {
                    securePrefs.saveEmailCredentials(fromEmail, authCode)
                    securePrefs.saveSmtpConfig(host, port)
                    android.widget.Toast.makeText(requireContext(), "$provider 配置已保存", android.widget.Toast.LENGTH_SHORT).show()
                    
                    // ✅ 自动检测剪贴板中的授权码
                    checkClipboardForAuthCode(etAuthCode)
                    
                    // ✅ 显示提示，建议用户发送测试邮件
                    showTestEmailSuggestionDialog()
                }
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("从剪贴板填充", null) // 添加剪贴板填充按钮
        
        // ✅ 创建并显示对话框
        val dialog = builder.create()
        dialog.show()
        
        // ✅ 为中性按钮设置自定义监听器（防止对话框关闭）
        dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            checkClipboardForAuthCode(etAuthCode)
        }
    }
    
    /**
     * ✅ 显示提示，建议用户发送测试邮件
     */
    private fun showTestEmailSuggestionDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("✅ 配置成功")
            .setMessage("邮箱配置已保存！\n\n建议您现在发送一封测试邮件，验证配置是否正确。\n\n是否立即发送测试邮件？")
            .setPositiveButton("发送测试邮件") { _, _ ->
                testEmailSend()
            }
            .setNegativeButton("稍后再说", null)
            .show()
    }
    
    /**
     * 带详细帮助信息的邮箱配置对话框（用于 163 邮箱）
     */
    private fun showEmailConfigDialogWithHelp(provider: String, defaultHost: String, defaultPort: String, emailType: String) {
        val prefsManager = PrefsManager(requireContext())
        val securePrefs = SecurePrefsManager(requireContext())
        
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle("$provider 配置")
        
        // ✅ 使用 requireContext().layoutInflater 而不是 activity 的 layoutInflater
        val contentView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_email_config, null)
        val etFromEmail = contentView.findViewById<TextInputEditText>(R.id.etFromEmail)
        val etAuthCode = contentView.findViewById<TextInputEditText>(R.id.etAuthCode)
        val etSmtpHost = contentView.findViewById<TextInputEditText>(R.id.etSmtpHost)
        val etSmtpPort = contentView.findViewById<TextInputEditText>(R.id.etSmtpPort)
        val btnHelpQQ = contentView.findViewById<MaterialButton>(R.id.btnHelpQQ)
        val btnHelp163 = contentView.findViewById<MaterialButton>(R.id.btnHelp163)
        
        etFromEmail.setText(securePrefs.getEmailAccount())
        etSmtpHost.setText(securePrefs.getSmtpHost() ?: defaultHost)
        etSmtpPort.setText(securePrefs.getSmtpPort() ?: defaultPort)
        
        // ✅ 显示 163 邮箱帮助按钮
        btnHelp163.visibility = View.VISIBLE
        
        // ✅ 163 邮箱帮助按钮点击事件 - 分步引导
        btnHelp163.setOnClickListener {
            val email = etFromEmail.text.toString()
            openEmailHelpPage("163", email)
        }
        
        builder.setView(contentView)
            .setPositiveButton("保存") { _, _ ->
                val fromEmail = etFromEmail.text.toString()
                val authCode = etAuthCode.text.toString()
                val host = etSmtpHost.text.toString()
                val port = etSmtpPort.text.toString()
                
                if (fromEmail.isNotEmpty() && authCode.isNotEmpty()) {
                    securePrefs.saveEmailCredentials(fromEmail, authCode)
                    securePrefs.saveSmtpConfig(host, port)
                    android.widget.Toast.makeText(requireContext(), "$provider 配置已保存", android.widget.Toast.LENGTH_SHORT).show()
                    
                    // ✅ 自动检测剪贴板中的授权码
                    checkClipboardForAuthCode(etAuthCode)
                }
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("从剪贴板填充", null)
        
        // ✅ 创建并显示对话框
        val dialog = builder.create()
        dialog.show()
        
        // ✅ 为中性按钮设置自定义监听器（防止对话框关闭）
        dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            checkClipboardForAuthCode(etAuthCode)
        }
    }
    
    /**
     * 打开邮箱授权码获取帮助页面 - 悬浮窗指引模式
     */
    private fun openEmailHelpPage(emailType: String, email: String) {
        // ✅ 检查无障碍权限是否已授予
        if (!isAccessibilityServiceEnabled()) {
            showAccessibilityPermissionDialog(emailType, email)
            return
        }
        
        // ✅ 已授予权限，启动悬浮窗指引服务
        startOverlayGuideService(emailType, email)
        
        // ✅ 打开邮箱登录页
        val loginUrl = when (emailType) {
            "qq" -> "https://mail.qq.com/"
            "163" -> "https://mail.163.com/"
            else -> return
        }
        
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        
        customTabsIntent.launchUrl(requireContext(), android.net.Uri.parse(loginUrl))
        
        Toast.makeText(requireContext(), "💡 悬浮窗指引已启动，请按照提示操作", Toast.LENGTH_LONG).show()
    }
    
    /**
     * 检查无障碍服务是否已启用
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = requireContext().getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == requireContext().packageName) {
                return true
            }
        }
        return false
    }
    
    /**
     * 显示无障碍权限申请对话框
     */
    private fun showAccessibilityPermissionDialog(emailType: String, email: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🔐 需要无障碍权限")
            .setMessage("""为了提供更好的引导体验，我们需要申请无障碍权限。

✅ 权限用途：
• 检测您当前所在的页面
• 自动切换到对应的操作指引
• 显示悬浮窗指导您每一步操作

🔒 安全承诺：
• 仅用于邮箱配置引导
• 不会读取任何隐私内容
• 不会执行任何危险操作

⚙️ 授权步骤：
1. 点击“去设置”按钮
2. 找到“安守”应用
3. 开启“已下载的服务”开关
4. 返回应用继续使用
            """.trimIndent())
            .setPositiveButton("去设置") { _, _ ->
                openAccessibilitySettings()
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("稍后再说") { _, _ ->
                // 用户选择稍后，显示普通指引
                showUltraDetailedGuideDialog(emailType, email)
            }
            .show()
    }
    
    /**
     * 打开无障碍服务设置页面
     */
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
    
    /**
     * 启动悬浮窗指引服务
     */
    private fun startOverlayGuideService(emailType: String, email: String) {
        val intent = Intent(requireContext(), EmailGuideOverlayService::class.java)
        intent.putExtra("email_type", emailType)
        intent.putExtra("email_address", email)
        ContextCompat.startForegroundService(requireContext(), intent)
    }
    
    /**
     * 显示超详细的图文指引对话框（终极版本）
     */
    private fun showUltraDetailedGuideDialog(emailType: String, email: String) {
        val providerName = getProviderName(emailType)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📧 $providerName - 授权码获取完整指引")
            .setMessage(getUltraDetailedMessage(emailType, email))
            .setPositiveButton("我已了解，开始操作") { _, _ ->
                Toast.makeText(requireContext(), "💡 提示：在浏览器中点击右上角菜单，选择「桌面版网站」可获得更好体验", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("查看常见问题", null)
            .show()
            // ✅ 为中性按钮设置监听器
            .getButton(android.app.AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                showFAQForEmailAuth(emailType)
            }
    }
    
    /**
     * 获取超详细的指引消息
     */
    private fun getUltraDetailedMessage(emailType: String, email: String): String {
        return when (emailType) {
            "qq" -> """📮 账号：${if (email.isNotEmpty()) email else "(请先输入}"}

━━━━━━━━━━━━━━━━━━━━━━━
⚠️ 重要提示（请先阅读）
━━━━━━━━━━━━━━━━━━━━━━━

🔹 本应用需要「授权码」而非「密码」
🔹 授权码是 QQ 邮箱提供的专用安全凭证
🔹 获取授权码完全免费，只需 2 分钟

━━━━━━━━━━━━━━━━━━━━━━━
✅ 详细操作步骤（请按顺序执行）
━━━━━━━━━━━━━━━━━━━━━━━

【第 1 步】登录邮箱
━━━━━━━━━━━━━━━━━━━━━━━
1. 在打开的浏览器中输入账号和密码
2. 点击「登录」按钮
3. 可能需要短信验证（按提示操作即可）

【第 2 步】切换到电脑版界面（关键！）
━━━━━━━━━━━━━━━━━━━━━━━
❗ 手机版界面找不到授权码入口
❗ 必须切换到电脑版才能继续

切换方法：
• 点击浏览器右上角的「⋮」或「≡」菜单
• 找到并勾选「桌面版网站」或「电脑版」
• 页面会自动刷新为电脑版界面

【第 3 步】进入设置页面
━━━━━━━━━━━━━━━━━━━━━━━
1. 登录后，点击页面顶部的「设置」标签
   （在邮箱名称下方的一排按钮中）

2. 在设置页面，确保选中了「账户」选项卡
   （不是「常规」或「其他」）

【第 4 步】开启 POP3/SMTP 服务
━━━━━━━━━━━━━━━━━━━━━━━
1. 向下滚动页面，找到「POP3/IMAP/SMTP/Exchange」服务
   （通常在页面中部偏下的位置）

2. 你会看到一个灰色的「关闭」按钮
   点击它，将其变为绿色的「开启」状态

3. 系统可能会要求你：
   • 发送短信到指定号码（免费）
   • 或使用手机号获取验证码
   按提示完成验证即可

【第 5 步】生成授权码
━━━━━━━━━━━━━━━━━━━━━━━
1. 开启服务后，会出现「生成授权码」按钮

2. 点击该按钮，系统会生成一串字母 + 数字的组合
   例如：abcd efgh ijkl mnop

3. 点击「复制」按钮，将授权码复制到剪贴板
   （或者手动记下来也可以）

【第 6 步】返回应用填写
━━━━━━━━━━━━━━━━━━━━━━━
1. 切换回本应用（从多任务中选择）

2. 授权码会自动填充到输入框
   （如果没有自动填充，长按输入框选择「粘贴」）

3. 点击「保存」按钮完成配置

━━━━━━━━━━━━━━━━━━━━━━━
💡 常见问题解答
━━━━━━━━━━━━━━━━━━━━━━━

Q1: 找不到「设置」按钮？
A1: 请确保已切换到电脑版界面（见第 2 步）

Q2: 提示「服务已开启」但没有授权码？
A2: 如果服务已经开启，点击「管理授权码」查看

Q3: 收不到短信验证码？
A3: 稍等 1-2 分钟后重试，或检查手机信号

Q4: 授权码格式是什么样的？
A4: 通常是 16 位字母 + 数字组合，不含空格

━━━━━━━━━━━━━━━━━━━━━━━
⏰ 预计耗时：2-3 分钟
━━━━━━━━━━━━━━━━━━━━━━━

如有任何问题，请点击下方的「查看常见问题」按钮。
            """.trimIndent()
            
            "163" -> """📮 账号：${if (email.isNotEmpty()) email else "(请先输入}"}

━━━━━━━━━━━━━━━━━━━━━━━
⚠️ 重要提示（请先阅读）
━━━━━━━━━━━━━━━━━━━━━━━

🔹 本应用需要「授权码」而非「密码」
🔹 授权码是网易邮箱提供的专用安全凭证
🔹 获取授权码完全免费，只需 2 分钟

━━━━━━━━━━━━━━━━━━━━━━━
✅ 详细操作步骤（请按顺序执行）
━━━━━━━━━━━━━━━━━━━━━━━

【第 1 步】登录邮箱
━━━━━━━━━━━━━━━━━━━━━━━
1. 在打开的浏览器中输入账号和密码
2. 点击「登录」按钮
3. 可能需要短信验证（按提示操作即可）

【第 2 步】切换到电脑版界面（关键！）
━━━━━━━━━━━━━━━━━━━━━━━
❗ 手机版界面找不到授权码入口
❗ 必须切换到电脑版才能继续

切换方法：
• 点击浏览器右上角的「⋮」或「≡」菜单
• 找到并勾选「桌面版网站」或「电脑版」
• 页面会自动刷新为电脑版界面

【第 3 步】进入设置页面
━━━━━━━━━━━━━━━━━━━━━━━
1. 登录后，点击页面顶部的「设置」
   （通常在右上角齿轮图标）

2. 在下拉菜单中选择「POP3/SMTP/IMAP」
   （或「客户端设置」）

【第 4 步】开启 SMTP 服务
━━━━━━━━━━━━━━━━━━━━━━━
1. 找到「POP3/SMTP 服务」或「IMAP/SMTP 服务」

2. 点击右侧的开关，将其开启
   （从灰色变为蓝色或绿色）

3. 系统可能会要求你：
   • 发送短信到指定号码（免费）
   • 或使用手机号获取验证码
   按提示完成验证即可

【第 5 步】获取授权码
━━━━━━━━━━━━━━━━━━━━━━━
1. 开启服务后，点击「客户端授权码」

2. 输入验证码后，会显示你的授权码
   例如：ABCDEFGHIJKL

3. 点击「复制」按钮，或手动记录下来

【第 6 步】返回应用填写
━━━━━━━━━━━━━━━━━━━━━━━
1. 切换回本应用（从多任务中选择）

2. 授权码会自动填充到输入框
   （如果没有自动填充，长按输入框选择「粘贴」）

3. 点击「保存」按钮完成配置

━━━━━━━━━━━━━━━━━━━━━━━
💡 常见问题解答
━━━━━━━━━━━━━━━━━━━━━━━

Q1: 找不到「设置」按钮？
A1: 请确保已切换到电脑版界面（见第 2 步）

Q2: 提示「已开通」但没有授权码？
A2: 点击「查看授权码」或「管理授权码」查看

Q3: 收不到短信验证码？
A3: 稍等 1-2 分钟后重试，或检查手机信号

Q4: 授权码格式是什么样的？
A4: 通常是大写字母 + 数字组合，10-20 位

━━━━━━━━━━━━━━━━━━━━━━━
⏰ 预计耗时：2-3 分钟
━━━━━━━━━━━━━━━━━━━━━━━

如有任何问题，请点击下方的「查看常见问题」按钮。
            """.trimIndent()
            
            else -> "请按照页面指引操作"
        }
    }
    
    /**
     * 显示邮箱授权相关的常见问题
     */
    private fun showFAQForEmailAuth(emailType: String) {
        val faqItems = listOf(
            FAQItem(
                "为什么一定要用电脑版界面？",
                "因为手机版界面功能精简，隐藏了授权码相关的高级设置。只有电脑版界面才能完整看到所有设置选项。"
            ),
            FAQItem(
                "授权码和密码有什么区别？",
                "授权码是专门给第三方应用（如本应用）使用的安全凭证。\n\n• 更安全：即使泄露也不会影响邮箱主密码\n• 可撤销：可以随时在邮箱中删除某个授权码\n• 有限权：只能用于收发邮件，不能做其他操作"
            ),
            FAQItem(
                "获取授权码收费吗？",
                "完全免费！QQ 邮箱和网易邮箱都免费提供授权码服务，不会收取任何费用。"
            ),
            FAQItem(
                "授权码多久会过期？",
                "授权码默认永久有效，除非：\n• 你手动在邮箱中删除了它\n• 你修改了邮箱密码\n• 邮箱服务商调整了策略"
            ),
            FAQItem(
                "可以生成多个授权码吗？",
                "可以！你可以为不同的应用生成不同的授权码，方便管理和单独撤销。"
            )
        )
        
        val adapter = FAQAdapter(faqItems)
        val recyclerView = androidx.recyclerview.widget.RecyclerView(requireContext()).apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
            this.adapter = adapter
            setPadding(32, 32, 32, 32)
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📚 邮箱授权常见问题")
            .setView(recyclerView)
            .setPositiveButton("知道了", null)
            .show()
    }
    
    /**
     * 显示下一步操作引导
     */
    /**
     * 直接打开邮箱设置页面
     */
    private fun openSettingPage(emailType: String, url: String) {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        
        customTabsIntent.launchUrl(requireContext(), android.net.Uri.parse(url))
        
        Toast.makeText(requireContext(), "请在设置页面找到\"生成授权码\"按钮，复制后返回", Toast.LENGTH_LONG).show()
    }
    
    /**
     * 获取邮箱服务商名称
     */
    private fun getProviderName(emailType: String): String {
        return when (emailType) {
            "qq" -> "QQ 邮箱"
            "163" -> "网易 163 邮箱"
            else -> "邮箱"
        }
    }
    
    /**
     * 检查剪贴板中是否有授权码并自动填充 - 增强版
     */
    private fun checkClipboardForAuthCode(editText: TextInputEditText) {
        try {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).coerceToText(requireContext()).toString()
                
                // ✅ 简单的授权码格式验证（通常是字母 + 数字的组合，12-20 位）
                if (text.matches(Regex("[a-zA-Z0-9]{12,20}"))) {
                    editText.setText(text)
                    android.widget.Toast.makeText(requireContext(), "✅ 已从剪贴板自动填充授权码", android.widget.Toast.LENGTH_SHORT).show()
                    return
                }
                
                // ✅ 如果剪贴板内容看起来像授权码但格式不完全匹配，也提示用户
                if (text.length >= 8 && text.contains(Regex("[a-zA-Z]")) && text.contains(Regex("[0-9]"))) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("📋 检测到可能的授权码")
                        .setMessage("剪贴板中的内容可能是授权码，是否要填充？\n\n内容：$text")
                        .setPositiveButton("填充") { _, _ ->
                            editText.setText(text)
                            android.widget.Toast.makeText(requireContext(), "已填充授权码", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
        } catch (e: Exception) {
            // 忽略剪贴板读取失败
        }
    }
    
    private fun testEmailSend() {
        val prefsManager = PrefsManager(requireContext())
        val securePrefs = SecurePrefsManager(requireContext())
        
        val toEmail = prefsManager.getEmailTo()
        val fromEmail = securePrefs.getEmailAccount()
        val authCode = securePrefs.getEmailAuthCode()
        val host = securePrefs.getSmtpHost()
        val port = securePrefs.getSmtpPort()
        
        if (toEmail.isNullOrEmpty()) {
            android.widget.Toast.makeText(requireContext(), "请先设置收件人邮箱", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        if (fromEmail.isNullOrEmpty() || authCode.isNullOrEmpty()) {
            android.widget.Toast.makeText(requireContext(), "请先配置发件人邮箱", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        val mailSender = MailSender()
        mailSender.sendEmail(
            host = host ?: "smtp.qq.com",
            port = port ?: "465",  // ✅ 默认端口改为 465 (SSL)
            fromEmail = fromEmail,
            authCode = authCode,
            toEmail = toEmail,
            subject = "安守 - 测试邮件",
            content = "这是一封测试邮件，用于验证邮箱配置是否正确。",
            callback = object : MailSender.SendCallback {
                override fun onSuccess() {
                    requireActivity().runOnUiThread {
                        android.widget.Toast.makeText(requireContext(), "测试邮件发送成功！", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                
                override fun onError(error: String) {
                    requireActivity().runOnUiThread {
                        android.widget.Toast.makeText(requireContext(), "发送失败：$error", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            },
            context = requireContext()
        )
    }
}

// ========== 第 8 步：睡眠监测设置 ==========
class SleepMonitorFragment : SettingsGuideFragment() {
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_guide_sleep, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val switchSleep = view.findViewById<SwitchMaterial>(R.id.switchSleepMonitor)
        val tvWakeHour = view.findViewById<TextView>(R.id.tvWakeHour)
        val tvWakeMinute = view.findViewById<TextView>(R.id.tvWakeMinute)
        val tvTolerance = view.findViewById<TextView>(R.id.tvTolerance)
        val btnHourPlus = view.findViewById<android.widget.ImageButton>(R.id.btnWakeHourPlus)
        val btnHourMinus = view.findViewById<android.widget.ImageButton>(R.id.btnWakeHourMinus)
        val btnMinutePlus = view.findViewById<android.widget.ImageButton>(R.id.btnWakeMinutePlus)
        val btnMinuteMinus = view.findViewById<android.widget.ImageButton>(R.id.btnWakeMinuteMinus)
        val btnTolerancePlus = view.findViewById<android.widget.ImageButton>(R.id.btnTolerancePlus)
        val btnToleranceMinus = view.findViewById<android.widget.ImageButton>(R.id.btnToleranceMinus)
        
        val prefsManager = PrefsManager(requireContext())
        switchSleep.isChecked = prefsManager.isSleepMonitorEnabled()
        
        var wakeHour = prefsManager.getWakeUpHour()
        var wakeMinute = prefsManager.getWakeUpMinute()
        var tolerance = prefsManager.getWakeUpToleranceHours()
        
        tvWakeHour.text = wakeHour.toString().padStart(2, '0')
        tvWakeMinute.text = wakeMinute.toString().padStart(2, '0')
        tvTolerance.text = tolerance.toString()
        
        switchSleep.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setSleepMonitorEnabled(isChecked)
        }
        
        btnHourPlus.setOnClickListener {
            wakeHour = (wakeHour + 1) % 24
            tvWakeHour.text = wakeHour.toString().padStart(2, '0')
            prefsManager.setWakeUpTime(wakeHour, wakeMinute)
        }
        
        btnHourMinus.setOnClickListener {
            wakeHour = if (wakeHour > 0) wakeHour - 1 else 23
            tvWakeHour.text = wakeHour.toString().padStart(2, '0')
            prefsManager.setWakeUpTime(wakeHour, wakeMinute)
        }
        
        btnMinutePlus.setOnClickListener {
            wakeMinute = (wakeMinute + 5) % 60
            tvWakeMinute.text = wakeMinute.toString().padStart(2, '0')
            prefsManager.setWakeUpTime(wakeHour, wakeMinute)
        }
        
        btnMinuteMinus.setOnClickListener {
            wakeMinute = if (wakeMinute > 0) wakeMinute - 5 else 55
            tvWakeMinute.text = wakeMinute.toString().padStart(2, '0')
            prefsManager.setWakeUpTime(wakeHour, wakeMinute)
        }
        
        btnTolerancePlus.setOnClickListener {
            tolerance++
            tvTolerance.text = tolerance.toString()
            prefsManager.setWakeUpToleranceHours(tolerance)
        }
        
        btnToleranceMinus.setOnClickListener {
            if (tolerance > 0) {
                tolerance--
                tvTolerance.text = tolerance.toString()
                prefsManager.setWakeUpToleranceHours(tolerance)
            }
        }
    }
}

/**
 * 模式选择 Fragment - 第一个界面
 */
class ModeSelectionFragment : SettingsGuideFragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_mode_selection, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // ✅ 直接查找所有 MaterialCardView
        val allCards = mutableListOf<com.google.android.material.card.MaterialCardView>()
        findCardsInLayout(view, allCards)
        
        if (allCards.size >= 3) {
            setupModeCard(allCards[0], "被守护模式", "监测本机用户状态，失能时发送警报给紧急联系人", R.drawable.ic_alert, PrefsManager.MODE_GUARDIAN)
            setupModeCard(allCards[1], "守护模式", "定期检测邮箱，接收他人发来的警报", R.drawable.ic_guardian, PrefsManager.MODE_RECEIVER)
            setupModeCard(allCards[2], "混合模式", "同时具备被守护和守护功能", R.drawable.ic_settings, PrefsManager.MODE_MIXED)
        }
    }
    
    /**
     * 递归查找所有 MaterialCardView
     */
    private fun findCardsInLayout(view: View, cards: MutableList<com.google.android.material.card.MaterialCardView>) {
        if (view is com.google.android.material.card.MaterialCardView) {
            cards.add(view)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findCardsInLayout(view.getChildAt(i), cards)
            }
        }
    }
    
    /**
     * 设置模式卡片
     */
    private fun setupModeCard(
        card: com.google.android.material.card.MaterialCardView,
        title: String,
        description: String,
        iconResId: Int,
        mode: String
    ) {
        val ivIcon = card.findViewById<android.widget.ImageView>(R.id.ivModeIcon)
        val tvTitle = card.findViewById<TextView>(R.id.tvModeTitle)
        val tvDesc = card.findViewById<TextView>(R.id.tvModeDescription)
        val rbSelect = card.findViewById<android.widget.RadioButton>(R.id.rbModeSelect)
        
        ivIcon.setImageResource(iconResId)
        tvTitle.text = title
        tvDesc.text = description
        
        // ✅ 初始状态下不选中任何模式（即使 SharedPreferences 中有值）
        // 只有当 Activity 的 selectedMode 与当前卡片模式匹配时才选中
        val activitySelectedMode = (activity as? SettingsGuideActivity)?.selectedMode
        rbSelect.isChecked = (activitySelectedMode == mode)
        
        // ✅ 选中状态样式
        updateCardStyle(card, rbSelect.isChecked)
        
        // ✅ 点击卡片选择模式
        card.setOnClickListener {
            // ✅ 先显示 Toast（在 Fragment 被替换之前）
            val context = context
            if (context != null && isAdded) {
                Toast.makeText(context, "已选择：$title", Toast.LENGTH_SHORT).show()
            }
            
            // 取消其他卡片的选中状态
            deselectAllModes(card.parent as? ViewGroup)
            
            // 选中当前卡片
            rbSelect.isChecked = true
            updateCardStyle(card, true)
            
            // ✅ 保存选择的模式到 PrefsManager
            prefsManager.saveAppMode(mode)
            
            // ✅ 同时更新 Activity 的 selectedMode 变量（关键修复！）
            val guideActivity = activity as? SettingsGuideActivity
            if (guideActivity != null) {
                guideActivity.selectedMode = mode
                android.util.Log.d("ModeSelection", "✅ 模式已选择: $mode, selectedMode = ${guideActivity.selectedMode}")
                
                // ✅ 关键修复：选择模式后，重新加载 Fragment 列表
                android.util.Log.d("ModeSelection", "🔄 开始重新加载 Fragment 列表...")
                guideActivity.loadFragmentsForMode(mode)
            } else {
                android.util.Log.e("ModeSelection", "❌ activity 为 null，无法更新 selectedMode")
            }
        }
    }
    
    /**
     * 更新卡片样式（选中/未选中）
     */
    private fun updateCardStyle(card: com.google.android.material.card.MaterialCardView, isSelected: Boolean) {
        if (isSelected) {
            card.strokeWidth = 4
            card.strokeColor = ContextCompat.getColor(requireContext(), R.color.primary)
            card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface))
        } else {
            card.strokeWidth = 2
            card.strokeColor = ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
            card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        }
    }
    
    /**
     * 取消所有模式的选中状态
     */
    private fun deselectAllModes(parent: ViewGroup?) {
        parent?.let {
            for (i in 0 until it.childCount) {
                val child = it.getChildAt(i)
                if (child is com.google.android.material.card.MaterialCardView) {
                    val rb = child.findViewById<android.widget.RadioButton>(R.id.rbModeSelect)
                    rb?.isChecked = false
                    updateCardStyle(child, false)
                }
            }
        }
    }
    
    /**
     * 提示用户必须选择模式
     */
    private fun showModeSelectionRequiredDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("提示")
            .setMessage("请先选择一个使用模式，然后继续下一步")
            .setPositiveButton("确定", null)
            .show()
    }
}

/**
 * 添加被守护人 Fragment
 */
class GuardianTargetsFragment : SettingsGuideFragment() {
    
    private var adapter: GuardianTargetAdapter? = null
    private val targetsList = mutableListOf<GuardianTarget>()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_guardian_targets, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val btnAddTarget = view.findViewById<android.widget.LinearLayout>(R.id.btnAddTarget)
        val tvHint = view.findViewById<TextView>(R.id.tvHint)
        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerViewTargets)
        
        // 设置提示文本
        tvHint.text = "后续您可以在：\n主页下方导航栏 - 守护按钮 - 左下角 + 号按钮\n或\n右上角设置界面 - 添加被守护人"
        
        // 初始化 RecyclerView
        setupRecyclerView(recyclerView)
        
        // 加载已添加的被守护人
        loadGuardianTargets()
        
        // ✅ 为添加成员卡片添加点击事件
        btnAddTarget.setOnClickListener {
            android.util.Log.d("GuardianTargets", "✅ 点击添加成员按钮")
            showAddTargetDialog()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 每次返回时刷新列表
        loadGuardianTargets()
    }
    
    private fun setupRecyclerView(recyclerView: androidx.recyclerview.widget.RecyclerView) {
        adapter = GuardianTargetAdapter(
            targets = targetsList,
            onEditClick = { target -> showEditTargetDialog(target) },
            onDeleteClick = { target -> showDeleteConfirmation(target) },
            onToggleClick = { target -> toggleTargetStatus(target) }
        )
        
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }
    
    private fun loadGuardianTargets() {
        val savedTargets = prefsManager.getGuardianTargets()
        targetsList.clear()
        targetsList.addAll(savedTargets)
        
        // 更新 UI 显示状态
        updateUIVisibility()
    }
    
    private fun updateUIVisibility() {
        val tvListTitle = view?.findViewById<TextView>(R.id.tvListTitle)
        val tvMemberCount = view?.findViewById<TextView>(R.id.tvMemberCount)
        val recyclerView = view?.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerViewTargets)
        
        if (targetsList.isNotEmpty()) {
            tvListTitle?.visibility = View.VISIBLE
            tvMemberCount?.visibility = View.VISIBLE
            tvMemberCount?.text = "${targetsList.size} 个人"
            recyclerView?.visibility = View.VISIBLE
            adapter?.notifyDataSetChanged()
        } else {
            tvListTitle?.visibility = View.GONE
            tvMemberCount?.visibility = View.GONE
            recyclerView?.visibility = View.GONE
        }
    }
    
    private fun showAddTargetDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_guardian_target, null)
        
        val etName = dialogView.findViewById<android.widget.EditText>(R.id.etTargetName)
        val etEmail = dialogView.findViewById<android.widget.EditText>(R.id.etTargetEmail)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("添加被守护人")
            .setView(dialogView)
            .setPositiveButton("添加") { _, _ ->
                val name = etName.text.toString().trim()
                val email = etEmail.text.toString().trim()
                
                if (name.isNotEmpty() && email.isNotEmpty()) {
                    val target = GuardianTarget(name = name, email = email)
                    prefsManager.addGuardianTarget(target)
                    
                    android.widget.Toast.makeText(requireContext(), "已添加被守护人：$name", android.widget.Toast.LENGTH_SHORT).show()
                    
                    // 刷新列表
                    loadGuardianTargets()
                } else {
                    android.widget.Toast.makeText(requireContext(), "请填写完整信息", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showEditTargetDialog(target: GuardianTarget) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_guardian_target, null)
        
        val etName = dialogView.findViewById<android.widget.EditText>(R.id.etTargetName)
        val etEmail = dialogView.findViewById<android.widget.EditText>(R.id.etTargetEmail)
        
        etName.setText(target.name)
        etEmail.setText(target.email)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("编辑被守护人")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                val email = etEmail.text.toString().trim()
                
                if (name.isNotEmpty() && email.isNotEmpty()) {
                    val updatedTarget = target.copy(name = name, email = email)
                    prefsManager.updateGuardianTarget(updatedTarget)
                    
                    android.widget.Toast.makeText(requireContext(), "已更新", android.widget.Toast.LENGTH_SHORT).show()
                    
                    // 刷新列表
                    loadGuardianTargets()
                } else {
                    android.widget.Toast.makeText(requireContext(), "请填写完整信息", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDeleteConfirmation(target: GuardianTarget) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("确认删除")
            .setMessage("确定要删除被守护人 \"${target.name}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                prefsManager.removeGuardianTarget(target.id)
                
                android.widget.Toast.makeText(requireContext(), "已删除", android.widget.Toast.LENGTH_SHORT).show()
                
                // 刷新列表
                loadGuardianTargets()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun toggleTargetStatus(target: GuardianTarget) {
        val updatedTarget = target.copy(isEnabled = !target.isEnabled)
        prefsManager.updateGuardianTarget(updatedTarget)
        
        // 刷新列表
        loadGuardianTargets()
    }
}
