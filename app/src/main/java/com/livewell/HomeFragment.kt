package com.livewell

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
    private lateinit var ivCheckinIcon: android.widget.ImageView
    // 新增卡片引用（替代按钮）
    private lateinit var cardModeSelect: MaterialCardView
    private lateinit var cardAlertHistory: MaterialCardView
    private lateinit var cardTimeCapsule: MaterialCardView
    
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
        ivCheckinIcon = view.findViewById(R.id.ivCheckinIcon)
        // 获取功能卡片
        cardModeSelect = view.findViewById(R.id.cardModeSelect)
        cardAlertHistory = view.findViewById(R.id.cardAlertHistory)
        cardTimeCapsule = view.findViewById(R.id.cardTimeCapsule)
        
        // ✅ 设置标题卡片点击事件（连续点击 10 次进入开发者界面）
        setupTitleClickListener(view)
        
        updateUI()
        setupListeners()
    }
    
    private fun setupListeners() {
        btnCheckin.setOnClickListener {
            performCheckin()
        }
        
        // 模式选择卡片
        cardModeSelect.setOnClickListener {
            (activity as? MainActivity)?.let { mainActivity ->
                mainActivity.showModeSelectDialogPublic()
            }
        }
        
        // 警报历史记录卡片
        cardAlertHistory.setOnClickListener {
            val intent = Intent(requireContext(), AlertHistoryActivity::class.java)
            startActivity(intent)
        }
        
        // 时光胶囊卡片
        cardTimeCapsule.setOnClickListener {
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
        
        // ✅ 保存签到日期和时间
        prefsManager.saveLastCheckinDate(today)
        prefsManager.saveCheckinTime(System.currentTimeMillis())
        
        android.util.Log.i("HomeFragment", "====== 签到成功 ======")
        android.util.Log.i("HomeFragment", "签到日期：$today")
        android.util.Log.i("HomeFragment", "签到时间：${System.currentTimeMillis()}")
        
        tvCheckinStatus.text = "今日已完成签到"
        tvCheckinStatus.setTextColor(requireContext().getColor(R.color.success))
        ivCheckinIcon.setImageResource(android.R.drawable.ic_menu_send)
        ivCheckinIcon.setColorFilter(requireContext().getColor(R.color.success))
        btnCheckin.isEnabled = false
        btnCheckin.text = "已签到"
        btnCheckin.setBackgroundColor(requireContext().getColor(R.color.divider))
        btnCheckin.setTextColor(requireContext().getColor(R.color.text_hint))
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
        
        Log.d("HomeFragment", "====== 签到状态检查 ======")
        Log.d("HomeFragment", "今日日期：$today")
        Log.d("HomeFragment", "最后签到：${lastCheckin ?: "null"}")
        Log.d("HomeFragment", "是否匹配：${today == lastCheckin}")
        
        if (today == lastCheckin) {
            tvCheckinStatus.text = "今日已完成签到"
            tvCheckinStatus.setTextColor(requireContext().getColor(R.color.success))
            ivCheckinIcon.setImageResource(android.R.drawable.ic_menu_send)
            ivCheckinIcon.setColorFilter(requireContext().getColor(R.color.success))
            btnCheckin.isEnabled = false
            btnCheckin.text = "已签到"
            btnCheckin.setBackgroundColor(requireContext().getColor(R.color.divider))
            btnCheckin.setTextColor(requireContext().getColor(R.color.text_hint))
        } else {
            tvCheckinStatus.text = "今日尚未签到"
            tvCheckinStatus.setTextColor(requireContext().getColor(R.color.warning))
            ivCheckinIcon.setImageResource(android.R.drawable.ic_dialog_alert)
            ivCheckinIcon.setColorFilter(requireContext().getColor(R.color.warning))
            btnCheckin.isEnabled = true
            btnCheckin.text = "立即签到"
            btnCheckin.setBackgroundColor(requireContext().getColor(R.color.primary))
            btnCheckin.setTextColor(requireContext().getColor(R.color.white))
        }
    }
}
