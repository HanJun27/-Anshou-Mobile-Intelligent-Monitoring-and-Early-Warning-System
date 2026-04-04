package com.livewell

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.livewell.model.GuardianTarget

class GuardianTargetAdapter(
    private val targets: MutableList<GuardianTarget>,
    private val onEditClick: (GuardianTarget) -> Unit,
    private val onDeleteClick: (GuardianTarget) -> Unit,
    private val onToggleClick: (GuardianTarget) -> Unit
) : RecyclerView.Adapter<GuardianTargetAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTargetName: TextView = view.findViewById(R.id.tvTargetName)
        val tvTargetEmail: TextView = view.findViewById(R.id.tvTargetEmail)
        val tvTargetStatus: TextView = view.findViewById(R.id.tvTargetStatus)
        val btnEdit: MaterialButton = view.findViewById(R.id.btnEdit)
        val btnDelete: MaterialButton = view.findViewById(R.id.btnDelete)
        val btnToggle: MaterialButton = view.findViewById(R.id.btnToggle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_guardian_target, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val target = targets[position]
        
        holder.tvTargetName.text = target.name
        holder.tvTargetEmail.text = target.email
        holder.tvTargetStatus.text = if (target.isEnabled) "已启用" else "已禁用"
        holder.tvTargetStatus.setTextColor(
            if (target.isEnabled) 
                holder.itemView.context.getColor(R.color.success)
            else 
                holder.itemView.context.getColor(R.color.text_secondary)
        )
        
        holder.btnToggle.text = if (target.isEnabled) "禁用" else "启用"
        
        holder.btnEdit.setOnClickListener { onEditClick(target) }
        holder.btnDelete.setOnClickListener { onDeleteClick(target) }
        holder.btnToggle.setOnClickListener { onToggleClick(target) }
    }

    override fun getItemCount() = targets.size

    fun updateTargets(newTargets: List<GuardianTarget>) {
        targets.clear()
        targets.addAll(newTargets)
        notifyDataSetChanged()
    }
}
