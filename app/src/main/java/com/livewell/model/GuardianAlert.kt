package com.livewell.model

import java.util.UUID

/**
 * 守护警报数据类
 */
data class GuardianAlert(
    val id: String = UUID.randomUUID().toString(),
    val targetId: String,       // 关联的守护对象 ID
    val alertType: String,      // 警报类型：emergency(紧急)/sleep(睡眠)
    val timestamp: Long,        // 警报时间
    val title: String,          // 标题
    val content: String,        // 完整内容
    val userMessage: String = "",  // 用户自定义消息
    val stepCount: Int = 0,     // 步数
    val usageMinutes: Int = 0,  // 使用时长
    val isRead: Boolean = false // 是否已读
) {
    /**
     * 获取简要摘要
     */
    fun getSummary(): String {
        return buildString {
            if (stepCount > 0 || usageMinutes > 0) {
                append("步数：${stepCount} | 使用：${usageMinutes}分钟")
            } else {
                append(content.take(50))
            }
        }
    }
    
    /**
     * 获取警报类型显示文本
     */
    fun getTypeDisplay(): String {
        return when (alertType) {
            "emergency" -> "紧急警报"
            "sleep" -> "睡眠监测"
            else -> "未知警报"
        }
    }
}
