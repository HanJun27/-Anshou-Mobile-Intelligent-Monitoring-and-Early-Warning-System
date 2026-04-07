package com.livewell

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.livewell.model.GuardianAlert
import java.text.SimpleDateFormat
import java.util.*

class GuardianAlertAdapter(
    private val alerts: List<GuardianAlert>,
    private val onItemClick: (GuardianAlert) -> Unit
) : RecyclerView.Adapter<GuardianAlertAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val flAlertIcon: FrameLayout = itemView.findViewById(R.id.flAlertIcon)
        val ivAlertType: ImageView = itemView.findViewById(R.id.ivAlertType)
        val tvAlertTitle: TextView = itemView.findViewById(R.id.tvAlertTitle)
        val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        val tvSummary: TextView = itemView.findViewById(R.id.tvSummary)
        val tvStepCount: TextView = itemView.findViewById(R.id.tvStepCount)
        val tvUsageMinutes: TextView = itemView.findViewById(R.id.tvUsageMinutes)
        val viewUnreadDot: View = itemView.findViewById(R.id.viewUnreadDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_guardian_alert, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val alert = alerts[position]
        
        // 设置警报类型图标和标题
        when (alert.alertType) {
            "emergency" -> {
                holder.flAlertIcon.setBackgroundResource(R.drawable.bg_emergency_icon)
                holder.ivAlertType.setImageResource(android.R.drawable.ic_dialog_alert)
                holder.ivAlertType.setColorFilter(holder.itemView.context.getColor(R.color.primary))
                holder.tvAlertTitle.text = "紧急警报"
            }
            "sleep" -> {
                holder.flAlertIcon.setBackgroundResource(R.drawable.bg_sleep_icon)
                holder.ivAlertType.setImageResource(android.R.drawable.ic_menu_recent_history)
                holder.ivAlertType.setColorFilter(holder.itemView.context.getColor(android.R.color.holo_purple))
                holder.tvAlertTitle.text = "睡眠监测"
            }
            else -> {
                holder.flAlertIcon.setBackgroundResource(R.drawable.bg_emergency_icon)
                holder.ivAlertType.setImageResource(android.R.drawable.ic_dialog_alert)
                holder.ivAlertType.setColorFilter(holder.itemView.context.getColor(R.color.primary))
                holder.tvAlertTitle.text = "未知警报"
            }
        }
        
        // 时间戳
        holder.tvTimestamp.text = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            .format(Date(alert.timestamp))
        
        // 摘要信息
        holder.tvSummary.text = alert.userMessage.ifEmpty { alert.content.take(50) }
        
        // 步数和使用时长（始终显示）
        holder.tvStepCount.visibility = View.VISIBLE
        holder.tvStepCount.text = "步数：${alert.stepCount}"
        
        holder.tvUsageMinutes.visibility = View.VISIBLE
        holder.tvUsageMinutes.text = "使用：${alert.usageMinutes}分钟"
        
        // 未读标记
        holder.viewUnreadDot.visibility = if (!alert.isRead) View.VISIBLE else View.GONE
        
        // 点击事件
        holder.itemView.setOnClickListener {
            onItemClick(alert)
        }
    }

    override fun getItemCount(): Int {
        return alerts.size
    }
}
