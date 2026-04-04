package com.livewell.model

import java.util.UUID

/**
 * 守护对象数据类
 */
data class GuardianTarget(
    val id: String = UUID.randomUUID().toString(),
    val name: String,           // 守护对象名称
    val email: String,          // 接收警报的邮箱地址（被守护者邮箱）
    val senderEmail: String = "",  // ✅ 新增：发送警报的邮箱地址（守护者邮箱）
    val isEnabled: Boolean = true,  // 是否启用
    val createTime: Long = System.currentTimeMillis()
)
