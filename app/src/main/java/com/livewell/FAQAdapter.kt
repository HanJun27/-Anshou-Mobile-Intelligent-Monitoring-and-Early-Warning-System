package com.livewell

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

/**
 * 常见问题数据类
 */
data class FAQItem(
    val question: String,
    val answer: String
)

/**
 * 问题合集适配器
 */
class FAQAdapter(
    private val items: List<FAQItem>
) : RecyclerView.Adapter<FAQAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardQuestion: MaterialCardView = view.findViewById(R.id.cardQuestion)
        val tvQuestion: TextView = view.findViewById(R.id.tvQuestion)
        val tvAnswer: TextView = view.findViewById(R.id.tvAnswer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_faq, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        
        holder.tvQuestion.text = item.question
        holder.tvAnswer.text = item.answer
        
        // 点击卡片展开/收起答案
        holder.cardQuestion.setOnClickListener {
            if (holder.tvAnswer.visibility == View.VISIBLE) {
                holder.tvAnswer.visibility = View.GONE
            } else {
                holder.tvAnswer.visibility = View.VISIBLE
            }
        }
        
        // 初始状态：答案隐藏
        holder.tvAnswer.visibility = View.GONE
    }

    override fun getItemCount() = items.size
}
