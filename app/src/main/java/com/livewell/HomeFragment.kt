package com.livewell

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.livewell.untils.PrefsManager
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {
    
    private lateinit var prefsManager: PrefsManager
    private lateinit var tvModeIndicator: android.widget.TextView
    private lateinit var tvCheckinStatus: android.widget.TextView
    private lateinit var btnCheckin: MaterialButton
    private lateinit var cardCheckin: MaterialCardView
    // 新增按钮引用
    private lateinit var btnModeSelect: MaterialButton
    private lateinit var btnAlertHistory: MaterialButton
    private lateinit var btnTimeCapsule: MaterialButton
    
    // ✅ 连续点击标题相关
    private var titleClickCount = 0
    private val TITLE_CLICK_THRESHOLD = 10
    private var lastClickTime: Long = 0
    private val CLICK_TIME_WINDOW = 3000L
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        prefsManager = PrefsManager(requireContext())
        
        tvModeIndicator = view.findViewById(R.id.tvModeIndicator)
        tvCheckinStatus = view.findViewById(R.id.tvCheckinStatus)
        btnCheckin = view.findViewById(R.id.btnCheckin)
        cardCheckin = view.findViewById(R.id.cardCheckin)
        // 获取模式选择和警报历史按钮
        btnModeSelect = view.findViewById(R.id.btnModeSelect)
        btnAlertHistory = view.findViewById(R.id.btnAlertHistory)
        btnTimeCapsule = view.findViewById(R.id.btnTimeCapsule)
        
        // ✅ 设置标题卡片点击事件（连续点击 10 次进入开发者界面）
        setupTitleClickListener(view)
        
        updateUI()
        setupListeners()
    }
    
    private fun setupListeners() {
        btnCheckin.setOnClickListener {
            performCheckin()
        }
        
        // 模式选择按钮
        btnModeSelect.setOnClickListener {
            (activity as? MainActivity)?.let { mainActivity ->
                mainActivity.showModeSelectDialogPublic()
            }
        }
        
        // 警报历史记录按钮
        btnAlertHistory.setOnClickListener {
            val intent = Intent(requireContext(), AlertHistoryActivity::class.java)
            startActivity(intent)
        }
        
        // 时光胶囊按钮
        btnTimeCapsule.setOnClickListener {
            (activity as? MainActivity)?.let { mainActivity ->
                mainActivity.showTimeCapsuleDialog()
            }
        }
    }
    
    /**
     * ✅ 设置标题卡片连续点击监听（连续 10 次进入开发者界面）
     */
    private fun setupTitleClickListener(view: View) {
        val cardHeader = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardHeader)
        cardHeader.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            
            if (currentTime - lastClickTime < CLICK_TIME_WINDOW) {
                titleClickCount++
                if (titleClickCount >= TITLE_CLICK_THRESHOLD) {
                    // 触发开发者界面
                    (activity as? MainActivity)?.let { mainActivity ->
                        mainActivity.showDeveloperDialog()
                    }
                    titleClickCount = 0
                } else {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "再点击 ${TITLE_CLICK_THRESHOLD - titleClickCount} 次进入开发者界面",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                titleClickCount = 1
            }
            
            lastClickTime = currentTime
        }
    }
    
    private fun performCheckin() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        prefsManager.saveCheckinTime(System.currentTimeMillis())
        
        tvCheckinStatus.text = "今日已签到"
        tvCheckinStatus.setTextColor(requireContext().getColor(R.color.success))
        btnCheckin.isEnabled = false
        btnCheckin.text = "已完成"
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
    }
    
    /**
     * 更新模式显示（供 MainActivity 调用）
     */
    fun updateModeDisplay() {
        val currentMode = prefsManager.getAppMode()
        tvModeIndicator.text = when (currentMode) {
            "guardian" -> "当前模式：被守护模式"
            "receiver" -> "当前模式：守护模式"
            "mixed" -> "当前模式：混合模式"
            "personal" -> "当前模式：个人守护模式"
            "community" -> "当前模式：社区守护模式"
            else -> "当前模式：未知"
        }
    }
    
    /**
     * 更新 UI（供 MainActivity 调用）
     */
    fun updateUI() {
        // 更新模式指示器
        val currentMode = prefsManager.getAppMode()
        tvModeIndicator.text = when (currentMode) {
            "guardian" -> "当前模式：被守护模式"
            "receiver" -> "当前模式：守护模式"
            "mixed" -> "当前模式：混合模式"
            "personal" -> "当前模式：个人守护模式"
            "community" -> "当前模式：社区守护模式"
            else -> "当前模式：未知"
        }
        
        // 更新签到状态
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastCheckin = prefsManager.getLastCheckinDate()
        
        if (today == lastCheckin) {
            tvCheckinStatus.text = "今日已签到"
            tvCheckinStatus.setTextColor(requireContext().getColor(R.color.success))
            btnCheckin.isEnabled = false
            btnCheckin.text = "已完成"
        } else {
            tvCheckinStatus.text = "今日尚未签到"
            tvCheckinStatus.setTextColor(requireContext().getColor(R.color.warning))
            btnCheckin.isEnabled = true
            btnCheckin.text = "立即签到"
        }
    }
}
