package com.livewell

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

/**
 * 附件列表适配器
 */
class AttachmentAdapter(
    private val attachments: List<String>,
    private val isEditMode: Boolean = true,
    private val onRemoveClick: ((String) -> Unit)? = null,
    private val onItemClick: ((String) -> Unit)? = null
) : RecyclerView.Adapter<AttachmentAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView = itemView.findViewById(R.id.ivAttachmentIcon)
        val tvName: TextView = itemView.findViewById(R.id.tvAttachmentName)
        val btnRemove: ImageButton = itemView.findViewById(R.id.btnRemoveAttachment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attachment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val path = attachments[position]
        val fileName = path.substringAfterLast("/")
        
        holder.tvName.text = fileName
        
        // 根据文件类型设置图标
        val iconRes = if (fileName.endsWith(".jpg") || fileName.endsWith(".png") || fileName.endsWith(".jpeg")) {
            android.R.drawable.ic_menu_gallery
        } else if (fileName.endsWith(".mp4") || fileName.endsWith(".3gp")) {
            android.R.drawable.ic_menu_view
        } else {
            android.R.drawable.ic_menu_share
        }
        holder.ivIcon.setImageResource(iconRes)
        
        // 编辑模式下显示删除按钮
        holder.btnRemove.visibility = if (isEditMode) View.VISIBLE else View.GONE
        
        // 删除按钮点击事件
        holder.btnRemove.setOnClickListener {
            onRemoveClick?.invoke(path)
        }
        
        // 文件项点击事件
        holder.itemView.setOnClickListener {
            onItemClick?.invoke(path)
        }
    }

    override fun getItemCount() = attachments.size
}
