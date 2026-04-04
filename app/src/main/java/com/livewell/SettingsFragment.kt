package com.livewell

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.livewell.untils.PrefsManager

class SettingsFragment : Fragment() {
    
    private lateinit var prefsManager: PrefsManager
    private lateinit var btnEmergencyContact: MaterialButton
    private lateinit var btnSmartSettings: MaterialButton
    private lateinit var btnSettingsGuide: MaterialButton
    private lateinit var btnFAQ: MaterialButton
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        prefsManager = PrefsManager(requireContext())
        
        btnEmergencyContact = view.findViewById(R.id.btnEmergencyContact)
        btnSmartSettings = view.findViewById(R.id.btnSmartSettings)
        btnSettingsGuide = view.findViewById(R.id.btnSettingsGuide)
        btnFAQ = view.findViewById(R.id.btnFAQ)
        
        setupListeners()
    }
    
    private fun setupListeners() {
        // 紧急联系人设置
        btnEmergencyContact.setOnClickListener {
            (activity as? MainActivity)?.let { mainActivity ->
                mainActivity.showEmergencyContactSettingsDialogPublic()
            }
        }
        
        // 功能设置
        btnSmartSettings.setOnClickListener {
            (activity as? MainActivity)?.let { mainActivity ->
                mainActivity.showSmartSettingsDialogPublic()
            }
        }
        
        // 设置引导
        btnSettingsGuide.setOnClickListener {
            (activity as? MainActivity)?.let { mainActivity ->
                mainActivity.showSettingsGuidePublic()
            }
        }
        
        // 问题合集
        btnFAQ.setOnClickListener {
            showFAQDialog()
        }
    }
    
    /**
     * 显示问题合集对话框
     */
    private fun showFAQDialog() {
        val faqItems = listOf(
            FAQItem(
                "为什么收不到警报邮件？",
                "可能原因：\n1. 未配置邮箱或授权码错误\n2. 网络连接不稳定\n3. 邮件服务器设置不正确\n\n解决方法：\n1. 检查邮箱配置是否正确\n2. 确保网络畅通\n3. 验证 SMTP 服务器地址和端口"
            ),
            FAQItem(
                "如何添加多个被守护人？",
                "操作步骤：\n1. 点击底部导航栏的「守护」按钮\n2. 点击右上角的设置图标\n3. 选择「配置守护成员」\n4. 输入被守护人姓名、邮箱和您的发件邮箱\n5. 重复以上步骤添加更多人\n\n注意：可以添加任意数量的被守护人"
            ),
            FAQItem(
                "签到失败怎么办？",
                "可能原因：\n1. 未授予使用统计权限\n2. 未关闭电池优化\n3. 应用后台被清理\n\n解决方法：\n1. 前往设置授予使用统计权限\n2. 关闭电池优化\n3. 锁定应用防止被清理"
            ),
            FAQItem(
                "步数数据不准确？",
                "可能原因：\n1. 健康数据权限未授予\n2. 手机传感器异常\n3. 数据同步延迟\n\n解决方法：\n1. 检查是否授予健康数据权限\n2. 重启手机试试\n3. 下拉刷新步数数据"
            ),
            FAQItem(
                "如何切换应用模式？",
                "操作步骤：\n1. 打开主页\n2. 点击左上角的模式显示区域\n3. 在弹出的对话框中选择需要的模式\n4. 点击「确定」保存\n\n模式说明：\n- 被守护模式：发送警报给他人\n- 守护模式：接收他人的警报\n- 混合模式：两者兼备"
            ),
            FAQItem(
                "警报阈值如何设置？",
                "建议设置：\n1. 使用时长阈值：30-60 分钟\n2. 步数阈值：50-200 步\n3. 起床时间：8:00-10:00\n\n调整方法：\n进入「功能设置」→「设备使用时长设置」或「每日步数设置」进行调整"
            ),
            FAQItem(
                "应用频繁退出怎么办？",
                "可能原因：\n1. 内存不足\n2. 系统自动清理\n3. 权限不足\n\n解决方法：\n1. 关闭电池优化\n2. 锁定应用防止被清理\n3. 授予所有必要权限\n4. 重启手机后重试"
            ),
            FAQItem(
                "如何查看历史警报记录？",
                "查看路径：\n1. 目前版本暂不支持历史记录查看\n2. 警报会通过邮件发送到配置的邮箱\n3. 可在邮箱中查看历史警报邮件\n\n后续版本会添加历史记录功能"
            )
        )
        
        val adapter = FAQAdapter(faqItems)
        val recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
            setPadding(16, 16, 16, 16)
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("常见问题合集")
            .setView(recyclerView)
            .setPositiveButton("知道了", null)
            .show()
    }
}
