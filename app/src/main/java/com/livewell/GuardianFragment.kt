package com.livewell

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.livewell.model.GuardianAlert
import com.livewell.model.GuardianTarget
import com.livewell.service.EmailReceiverService
import com.livewell.untils.PrefsManager
import java.text.SimpleDateFormat
import java.util.*

class GuardianFragment : Fragment() {

    private lateinit var prefsManager: PrefsManager
    private var currentTargetId: String? = null
    
    // UI 组件
    private lateinit var tvGuardianTitle: TextView
    private lateinit var btnManageTargets: ImageButton
    private lateinit var spinnerGuardianTargets: Spinner
    private lateinit var btnRefresh: MaterialButton
    private lateinit var tvAlertStats: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerViewAlerts: RecyclerView
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptySubtitle: TextView
    private lateinit var fabAddTarget: FloatingActionButton
    
    // 数据
    private var guardianTargets = mutableListOf<GuardianTarget>()
    private var alerts = mutableListOf<GuardianAlert>()
    private var adapter: GuardianAlertAdapter? = null
    
    // "全部"选项的标识
    private val ALL_TARGETS_ID = "__ALL__"
    
    private val handler = Handler(Looper.getMainLooper())
    
    companion object {
        private const val TAG = "GuardianFragment"
        
        fun newInstance(): GuardianFragment {
            return GuardianFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_guardian, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        prefsManager = PrefsManager(requireContext())
        
        initViews(view)
        setupRecyclerView()
        setupListeners()
        loadData()
    }
    
    private fun initViews(view: View) {
        tvGuardianTitle = view.findViewById(R.id.tvGuardianTitle)
        btnManageTargets = view.findViewById(R.id.btnManageTargets)
        spinnerGuardianTargets = view.findViewById(R.id.spinnerGuardianTargets)
        btnRefresh = view.findViewById(R.id.btnRefresh)
        tvAlertStats = view.findViewById(R.id.tvAlertStats)
        swipeRefreshLayout = view.findViewById<View>(R.id.swipeRefreshLayout) as SwipeRefreshLayout
        recyclerViewAlerts = view.findViewById(R.id.recyclerViewAlerts)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)
        tvEmptyTitle = view.findViewById(R.id.tvEmptyTitle)
        tvEmptySubtitle = view.findViewById(R.id.tvEmptySubtitle)
        fabAddTarget = view.findViewById(R.id.fabAddTarget)
    }
    
    private fun setupRecyclerView() {
        adapter = GuardianAlertAdapter(alerts) { alert ->
            showAlertDialog(alert)
        }
        
        recyclerViewAlerts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@GuardianFragment.adapter
        }
    }
    
    private fun setupListeners() {
        // 管理守护对象按钮 - 改为显示设置对话框
        btnManageTargets.setOnClickListener {
            showSettingsDialog()
        }
        
        // 刷新按钮
        btnRefresh.setOnClickListener {
            refreshAlerts()
        }
        
        // 长按刷新按钮同步历史邮件
        btnRefresh.setOnLongClickListener {
            val options = arrayOf(
                "重置检查时间（重新检查所有邮件）",
                "同步最近 30 天的历史邮件"
            )
            
            AlertDialog.Builder(requireContext())
                .setTitle("邮件同步选项")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            // 重置检查时间
                            prefsManager.resetEmailCheckTime()
                            Toast.makeText(
                                requireContext(),
                                "已重置检查时间，请点击刷新按钮重新检查所有邮件",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        1 -> {
                            // 同步 30 天历史
                            val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                            prefsManager.saveLastEmailCheckTime(thirtyDaysAgo)
                            Toast.makeText(
                                requireContext(),
                                "已设置为同步最近 30 天的邮件，请点击刷新按钮",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }
        
        // 下拉刷新
        swipeRefreshLayout.setOnRefreshListener {
            refreshAlerts()
        }
        
        // 添加守护对象浮动按钮
        fabAddTarget.setOnClickListener {
            showAddTargetDialog()
        }
        
        // 守护对象选择器
        spinnerGuardianTargets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (position == 0) {
                    // 选中"全部"
                    currentTargetId = ALL_TARGETS_ID
                    prefsManager.setCurrentTargetId(currentTargetId)
                    loadAllAlerts()
                } else if (position - 1 < guardianTargets.size) {
                    // 选中具体某个守护对象
                    val selectedTarget = guardianTargets[position - 1]
                    currentTargetId = selectedTarget.id
                    prefsManager.setCurrentTargetId(currentTargetId)
                    loadAlertsForTarget(currentTargetId!!)
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // 不需要处理
            }
        }
    }
    
    private fun loadData() {
        // 加载守护对象列表
        guardianTargets.clear()
        guardianTargets.addAll(prefsManager.getGuardianTargets())
        
        if (guardianTargets.isEmpty()) {
            // 没有守护对象，显示提示
            showEmptyTargetsState()
            return
        }
        
        // 设置 Spinner 适配器（添加"全部"选项）
        setupTargetSpinner()
        
        // 默认选中"全部"
        currentTargetId = ALL_TARGETS_ID
        prefsManager.setCurrentTargetId(currentTargetId)
        
        // 加载所有警报
        loadAllAlerts()
    }
    
    private fun setupTargetSpinner() {
        // 添加"全部"选项
        val targetNames = listOf("全部") + guardianTargets.map { it.name }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            targetNames
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGuardianTargets.adapter = adapter
        
        // 设置当前选中项（默认选中"全部"）
        spinnerGuardianTargets.setSelection(0)
    }
    
    private fun loadAlertsForTarget(targetId: String) {
        alerts.clear()
        alerts.addAll(prefsManager.getGuardianAlerts(targetId))
        
        updateAlertStats()
        updateEmptyState()
        adapter?.notifyDataSetChanged()
    }
    
    /**
     * 加载所有被守护人的报警记录
     */
    private fun loadAllAlerts() {
        alerts.clear()
        
        // 获取所有被守护人的警报并合并
        val allAlerts = mutableListOf<GuardianAlert>()
        for (target in guardianTargets) {
            allAlerts.addAll(prefsManager.getGuardianAlerts(target.id))
        }
        
        // 按时间戳倒序排序（最新的在前）
        alerts.addAll(allAlerts.sortedByDescending { it.timestamp })
        
        updateAlertStats()
        updateEmptyState()
        adapter?.notifyDataSetChanged()
    }
    
    private fun refreshAlerts() {
        swipeRefreshLayout.isRefreshing = true
        
        // ✅ 触发强制邮件检查（立即执行）
        EmailReceiverService.forceCheck(requireContext())
        
        // 延迟刷新列表（等待邮件检查完成）
        // ✅ 增加延迟时间到 5 秒，因为 IMAP 连接可能需要时间
        handler.postDelayed({
            if (currentTargetId == ALL_TARGETS_ID) {
                loadAllAlerts()
            } else if (currentTargetId != null) {
                loadAlertsForTarget(currentTargetId!!)
            }
            swipeRefreshLayout.isRefreshing = false
            Toast.makeText(requireContext(), "刷新完成", Toast.LENGTH_SHORT).show()
        }, 5000)
    }
    
    private fun updateAlertStats() {
        val totalCount = alerts.size
        val unreadCount = alerts.count { !it.isRead }
        tvAlertStats.text = "共 $totalCount 条警报 | 未读 $unreadCount 条"
    }
    
    private fun updateEmptyState() {
        if (alerts.isEmpty()) {
            recyclerViewAlerts.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
        } else {
            recyclerViewAlerts.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE
        }
    }
    
    private fun showEmptyTargetsState() {
        recyclerViewAlerts.visibility = View.GONE
        layoutEmptyState.visibility = View.VISIBLE
        tvEmptyTitle.text = "暂无守护成员"
        tvEmptySubtitle.text = "点击右下角按钮配置第一个守护成员"
        spinnerGuardianTargets.visibility = View.GONE
    }
    
    private fun showAddTargetDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_guardian_target, null)
        
        val etName = dialogView.findViewById<EditText>(R.id.etTargetName)
        val etEmail = dialogView.findViewById<EditText>(R.id.etTargetEmail)
        val etSenderEmail = dialogView.findViewById<EditText>(R.id.etSenderEmail)  // ✅ 新增
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("配置守护关系")
            .setView(dialogView)
            .setPositiveButton("添加") { _, _ ->
                val name = etName.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val senderEmail = etSenderEmail.text.toString().trim()  // ✅ 新增
                
                if (name.isNotEmpty() && email.isNotEmpty()) {
                    val target = GuardianTarget(
                        name = name, 
                        email = email,
                        senderEmail = senderEmail.ifEmpty { email }  // ✅ 如果没有填写，默认使用接收邮箱
                    )
                    prefsManager.addGuardianTarget(target)
                    
                    // 重新加载数据
                    guardianTargets.clear()
                    guardianTargets.addAll(prefsManager.getGuardianTargets())
                    setupTargetSpinner()
                    
                    // 显示 spinner
                    spinnerGuardianTargets.visibility = View.VISIBLE
                    recyclerViewAlerts.visibility = View.VISIBLE
                    layoutEmptyState.visibility = View.GONE
                    
                    // 保持选中"全部"
                    currentTargetId = ALL_TARGETS_ID
                    prefsManager.setCurrentTargetId(currentTargetId)
                    loadAllAlerts()
                    
                    // 检查当前模式并提示
                    checkAndPromptModeChange()
                } else {
                    Toast.makeText(requireContext(), "请填写完整信息", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showManageTargetsDialog() {
        if (guardianTargets.isEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle("守护对象管理")
                .setMessage("暂无守护对象，是否添加？")
                .setPositiveButton("添加") { _, _ ->
                    showAddTargetDialog()
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        
        val targetNames = guardianTargets.map { "${it.name} (${it.email})" }.toTypedArray()
        
        AlertDialog.Builder(requireContext())
            .setTitle("守护对象管理")
            .setItems(targetNames) { _, which ->
                showTargetOptionsDialog(guardianTargets[which])
            }
            .setPositiveButton("添加新的") { _, _ ->
                showAddTargetDialog()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showTargetOptionsDialog(target: GuardianTarget) {
        val options = arrayOf("编辑", "删除", if (target.isEnabled) "禁用" else "启用")
        
        AlertDialog.Builder(requireContext())
            .setTitle(target.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditTargetDialog(target)
                    1 -> confirmDeleteTarget(target)
                    2 -> toggleTargetStatus(target)
                }
            }
            .show()
    }
    
    private fun showAlertDialog(alert: GuardianAlert) {
        // 标记为已读
        prefsManager.markAlertAsRead(alert.id)
        
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_alert_detail, null)
        
        val tvAlertType = dialogView.findViewById<TextView>(R.id.tvDetailAlertType)
        val tvTimestamp = dialogView.findViewById<TextView>(R.id.tvDetailTimestamp)
        val tvUserMessage = dialogView.findViewById<TextView>(R.id.tvDetailUserMessage)
        val tvStepCount = dialogView.findViewById<TextView>(R.id.tvDetailStepCount)
        val tvUsageMinutes = dialogView.findViewById<TextView>(R.id.tvDetailUsageMinutes)
        val tvFullContent = dialogView.findViewById<TextView>(R.id.tvDetailFullContent)
        
        // 填充数据
        tvAlertType.text = alert.getTypeDisplay()
        tvTimestamp.text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(alert.timestamp))
        tvUserMessage.text = alert.userMessage.ifEmpty { "无自定义消息" }
        tvStepCount.text = "${alert.stepCount} 步"
        tvUsageMinutes.text = "${alert.usageMinutes} 分钟"
        tvFullContent.text = alert.content
        
        // 根据数据类型决定是否显示
        tvStepCount.visibility = if (alert.stepCount > 0) View.VISIBLE else View.GONE
        tvUsageMinutes.visibility = if (alert.usageMinutes > 0) View.VISIBLE else View.GONE
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("警报详情")
            .setView(dialogView)
            .setPositiveButton("知道了", null)
            .setNeutralButton("清除已读") { _, _ ->
                prefsManager.clearReadAlerts(currentTargetId)
                loadAlertsForTarget(currentTargetId ?: return@setNeutralButton)
            }
            .show()
    }
    
    override fun onResume() {
        super.onResume()
        if (currentTargetId == ALL_TARGETS_ID) {
            loadAllAlerts()
        } else if (currentTargetId != null) {
            loadAlertsForTarget(currentTargetId!!)
        }
    }
    
    // ========== 新增的对话框方法 ==========
    
    /**
     * 显示设置对话框
     */
    private fun showSettingsDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_guardian_settings, null)
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()
        
        dialogView.findViewById<MaterialButton>(R.id.btnAddTarget).setOnClickListener {
            dialog.dismiss()
            showAddTargetDialog()
        }
        
        dialogView.findViewById<MaterialButton>(R.id.btnManageTargets).setOnClickListener {
            dialog.dismiss()
            showManageTargetsListDialog()
        }
        
        dialogView.findViewById<MaterialButton>(R.id.btnReceiverConfig).setOnClickListener {
            dialog.dismiss()
            showReceiverConfigDialog()
        }
        
        dialog.show()
    }
    
    /**
     * 显示守护对象管理列表对话框
     */
    private fun showManageTargetsListDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_manage_targets, null)
        
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerViewTargets)
        val targets = guardianTargets.toMutableList()
        val adapter = GuardianTargetAdapter(
            targets,
            onEditClick = { target ->
                showEditTargetDialog(target)
            },
            onDeleteClick = { target ->
                confirmDeleteTarget(target)
            },
            onToggleClick = { target ->
                toggleTargetStatus(target)
            }
        )
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("完成", null)
            .show()
    }
    
    /**
     * 显示编辑守护对象对话框
     */
    private fun showEditTargetDialog(target: GuardianTarget) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_guardian_target, null)
        
        val etName = dialogView.findViewById<EditText>(R.id.etTargetName)
        val etEmail = dialogView.findViewById<EditText>(R.id.etTargetEmail)
        val etSenderEmail = dialogView.findViewById<EditText>(R.id.etSenderEmail)  // ✅ 新增
        
        etName.setText(target.name)
        etEmail.setText(target.email)
        etSenderEmail.setText(target.senderEmail)  // ✅ 新增：加载发件邮箱
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("编辑守护关系")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val senderEmail = etSenderEmail.text.toString().trim()  // ✅ 新增
                
                if (name.isNotEmpty() && email.isNotEmpty()) {
                    val updatedTarget = target.copy(
                        name = name,
                        email = email,
                        senderEmail = senderEmail.ifEmpty { email }  // ✅ 如果没有填写，默认使用接收邮箱
                    )
                    prefsManager.updateGuardianTarget(updatedTarget)
                    refreshGuardianTargets()
                } else {
                    Toast.makeText(requireContext(), "请填写完整信息", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 确认删除守护对象
     */
    private fun confirmDeleteTarget(target: GuardianTarget) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("确认删除")
            .setMessage("确定要删除守护对象 \"${target.name}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                prefsManager.removeGuardianTarget(target.id)
                refreshGuardianTargets()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 切换守护对象状态
     */
    private fun toggleTargetStatus(target: GuardianTarget) {
        val updatedTarget = target.copy(isEnabled = !target.isEnabled)
        prefsManager.updateGuardianTarget(updatedTarget)
        refreshGuardianTargets()
    }
    
    /**
     * 显示接收配置对话框
     */
    private fun showReceiverConfigDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_receiver_config, null)
        
        val etEmailAddress = dialogView.findViewById<EditText>(R.id.etEmailAddress)
        val etAuthCode = dialogView.findViewById<EditText>(R.id.etAuthCode)
        
        // ✅ 从 SecurePrefsManager 读取配置（与 EmailReceiverService 保持一致）
        val securePrefs = com.livewell.untils.SecurePrefsManager(requireContext())
        val email = securePrefs.getEmailAccount() ?: ""
        val authCode = securePrefs.getEmailAuthCode() ?: ""
        val smtpHost = securePrefs.getSmtpHost() ?: ""
        val smtpPort = securePrefs.getSmtpPort() ?: ""
        
        etEmailAddress.setText(email)
        etAuthCode.setText(authCode)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("接收配置")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val emailAddress = etEmailAddress.text.toString().trim()
                val code = etAuthCode.text.toString().trim()
                
                if (emailAddress.isNotEmpty() && code.isNotEmpty()) {
                    // ✅ 保存到 SecurePrefsManager（加密存储）
                    securePrefs.saveEmailCredentials(emailAddress, code)
                    
                    // ✅ 同时保存 SMTP 配置（如果之前有）
                    if (smtpHost.isNotEmpty()) {
                        securePrefs.saveSmtpConfig(smtpHost, smtpPort)
                    }
                    
                    Toast.makeText(requireContext(), "配置已保存到加密存储", Toast.LENGTH_SHORT).show()
                    Log.i("GuardianFragment", "邮箱配置已更新：$emailAddress")
                } else {
                    Toast.makeText(requireContext(), "请填写完整信息", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 刷新守护对象列表
     */
    private fun refreshGuardianTargets() {
        guardianTargets.clear()
        guardianTargets.addAll(prefsManager.getGuardianTargets())
        setupTargetSpinner()
        
        // 如果有守护对象，显示 spinner
        if (guardianTargets.isNotEmpty()) {
            spinnerGuardianTargets.visibility = View.VISIBLE
            recyclerViewAlerts.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE
        } else {
            showEmptyTargetsState()
        }
        
        // 保持在"全部"选项
        currentTargetId = ALL_TARGETS_ID
        prefsManager.setCurrentTargetId(currentTargetId)
        loadAllAlerts()
    }
    
    /**
     * 检查当前模式并提示用户切换
     */
    private fun checkAndPromptModeChange() {
        val currentMode = prefsManager.getAppMode()
        
        // 如果是被守护模式，提示用户切换
        if (currentMode == "guardian") {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("提示")
                .setMessage("您已添加守护对象，但当前为【被守护模式】，只会发送警报，不会接收邮件。\n\n是否切换到【混合模式】或【守护模式】以接收警报邮件？")
                .setPositiveButton("混合模式") { _, _ ->
                    switchToMode("mixed")
                }
                .setNeutralButton("守护模式") { _, _ ->
                    switchToMode("receiver")
                }
                .setNegativeButton("暂不切换") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }
    
    /**
     * 切换应用模式
     */
    private fun switchToMode(mode: String) {
        // 保存新模式
        prefsManager.saveAppMode(mode)
        
        // 显示成功提示
        val modeName = when (mode) {
            "mixed" -> "混合模式"
            "receiver" -> "守护模式"
            else -> "新方式"
        }
        Toast.makeText(requireContext(), "已切换到$modeName", Toast.LENGTH_SHORT).show()
        
        // 通知 MainActivity 更新 UI
        (activity as? MainActivity)?.let { mainActivity ->
            mainActivity.updateModeDisplayPublic()
        }
    }
}
