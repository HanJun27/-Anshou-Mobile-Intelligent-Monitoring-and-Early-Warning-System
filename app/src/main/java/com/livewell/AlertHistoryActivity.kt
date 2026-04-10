package com.livewell

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.livewell.untils.PrefsManager
import com.livewell.model.AlertHistoryRecord
import com.livewell.model.AlertType
import com.livewell.model.AlertStatus
import com.livewell.model.AlertMethod





class AlertHistoryActivity : AppCompatActivity() {
    
    private lateinit var prefsManager: PrefsManager
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvTotalCount: TextView
    private lateinit var tvSuccessCount: TextView
    private lateinit var tvFailedCount: TextView
    private lateinit var btnClearHistory: MaterialButton
    private lateinit var btnBack: android.widget.ImageButton
    
    private var historyList = mutableListOf<AlertHistoryRecord>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alert_history)
        
        prefsManager = PrefsManager(this)
        
        initViews()
        loadHistory()
    }
    
    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        rvHistory = findViewById(R.id.rvHistory)
        tvTotalCount = findViewById(R.id.tvTotalCount)
        tvSuccessCount = findViewById(R.id.tvSuccessCount)
        tvFailedCount = findViewById(R.id.tvFailedCount)
        btnClearHistory = findViewById(R.id.btnClearHistory)
        
        // 设置返回按钮点击事件
        btnBack.setOnClickListener {
            finish()
        }
        
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = HistoryAdapter()
        
        btnClearHistory.setOnClickListener {
            prefsManager.clearAlertHistory()
            loadHistory()
        }
    }
    
    private fun loadHistory() {
        historyList = prefsManager.getAlertHistory()
        rvHistory.adapter?.notifyDataSetChanged()
        updateStats()
    }
    
    private fun updateStats() {
        val total = historyList.size
        val success = historyList.count { it.status == AlertStatus.SUCCESS }
        val failed = historyList.count { it.status == AlertStatus.FAILED }
        
        tvTotalCount.text = "总计：$total"
        tvSuccessCount.text = "成功：$success"
        tvFailedCount.text = "失败：$failed"
    }
    
    inner class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvType: TextView = view.findViewById(R.id.tvType)
            val tvStatus: TextView = view.findViewById(R.id.tvStatus)
            val tvTime: TextView = view.findViewById(R.id.tvTime)
            val tvMethod: TextView = view.findViewById(R.id.tvMethod)
            val tvReason: TextView = view.findViewById(R.id.tvReason)
            val tvContent: TextView = view.findViewById(R.id.tvContent)
            val tvSteps: TextView = view.findViewById(R.id.tvSteps)  // ✅ 步数
            val tvUsage: TextView = view.findViewById(R.id.tvUsage)  // ✅ 使用时长
            val btnViewLog: MaterialButton = view.findViewById(R.id.btnViewLog)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_alert_history, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val record = historyList[position]
            
            holder.tvType.text = record.getTypeText()
            holder.tvStatus.text = record.getStatusText()
            holder.tvStatus.setTextColor(getStatusColor(record.status))
            holder.tvTime.text = record.getFormattedTime()
            holder.tvMethod.text = when (record.method) {
                AlertMethod.SMS -> "短信"
                AlertMethod.EMAIL -> "邮件"
                AlertMethod.NOTIFICATION -> "通知"
                AlertMethod.BOTH -> "短信 + 邮件"
            }
            holder.tvReason.text = record.reason
            holder.tvContent.text = record.content
            
            // ✅ 显示步数和使用时长
            holder.tvSteps.text = "步数：${record.steps}"
            holder.tvUsage.text = "使用时长：${record.usageMinutes}分钟"
            
            // ✅ 仅在失败状态时显示查看日志按钮
            if (record.status == AlertStatus.FAILED) {
                holder.btnViewLog.visibility = View.VISIBLE
                holder.btnViewLog.setOnClickListener {
                    showLogDialog(record)
                }
            } else {
                holder.btnViewLog.visibility = View.GONE
            }
        }
        
        /**
         * 显示日志对话框
         */
        private fun showLogDialog(record: AlertHistoryRecord) {
            val builder = androidx.appcompat.app.AlertDialog.Builder(this@AlertHistoryActivity)
            builder.setTitle("📋 发送失败日志")
            
            // 构建日志内容
            val logContent = buildLogContent(record)
            
            builder.setMessage(logContent)
            builder.setPositiveButton("复制日志") { _, _ ->
                copyToClipboard(logContent)
            }
            builder.setNegativeButton("关闭", null)
            builder.show()
        }
        
        /**
         * 构建日志内容
         */
        private fun buildLogContent(record: AlertHistoryRecord): String {
            val sb = StringBuilder()
            sb.append("════════════════════════\n")
            sb.append("警报类型：${record.getTypeText()}\n")
            sb.append("════════════════════════\n\n")
            
            sb.append("【基本信息】\n")
            sb.append("时间：${record.getFormattedTime()}\n")
            sb.append("状态：${record.getStatusText()}\n")
            sb.append("方式：${
                when (record.method) {
                    AlertMethod.SMS -> "短信"
                    AlertMethod.EMAIL -> "邮件"
                    AlertMethod.NOTIFICATION -> "通知"
                    AlertMethod.BOTH -> "短信 + 邮件"
                }
            }\n\n")
            
            // ✅ 添加步数和使用时长信息
            sb.append("【健康数据】\n")
            sb.append("当日步数：${record.steps} 步\n")
            sb.append("使用时长：${record.usageMinutes} 分钟\n\n")
            
            sb.append("【警报原因】\n")
            sb.append("${record.reason}\n\n")
            
            sb.append("【警报内容】\n")
            sb.append("${record.content}\n\n")
            
            sb.append("════════════════════════\n")
            sb.append("提示：如果问题持续，请检查网络设置或联系技术支持")
            
            return sb.toString()
        }
        
        /**
         * 复制到剪贴板
         */
        private fun copyToClipboard(content: String) {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("alert_log", content)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(this@AlertHistoryActivity, "日志已复制", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        private fun getStatusColor(status: AlertStatus): Int {
            return when (status) {
                AlertStatus.SUCCESS -> getColor(R.color.success)
                AlertStatus.FAILED -> getColor(R.color.error)
                AlertStatus.PENDING -> getColor(R.color.warning)
                AlertStatus.CANCELLED -> getColor(R.color.text_secondary)
            }
        }
        
        override fun getItemCount() = historyList.size
    }
}