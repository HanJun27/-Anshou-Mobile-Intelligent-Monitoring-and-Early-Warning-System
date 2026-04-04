package com.livewell.model

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.*


enum class AlertType {
    @SerializedName("checkin") CHECKIN,
    @SerializedName("sleep") SLEEP,
    @SerializedName("usage") USAGE,
    @SerializedName("step") STEP
}

enum class AlertStatus {
    @SerializedName("pending") PENDING,
    @SerializedName("success") SUCCESS,
    @SerializedName("failed") FAILED,
    @SerializedName("cancelled") CANCELLED
}

enum class AlertMethod {
    @SerializedName("sms") SMS,
    @SerializedName("email") EMAIL,
    @SerializedName("notification") NOTIFICATION,
    @SerializedName("both") BOTH
}

data class AlertHistoryRecord(
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("type") val type: AlertType,
    @SerializedName("status") val status: AlertStatus,
    @SerializedName("method") val method: AlertMethod,
    @SerializedName("content") val content: String,
    @SerializedName("reason") val reason: String,
    @SerializedName("steps") val steps: Int = 0,  // ✅ 添加步数字段
    @SerializedName("usageMinutes") val usageMinutes: Int = 0  // ✅ 添加使用时长字段
) {
    fun getTypeText(): String {
        return when (type) {
            AlertType.CHECKIN -> "签到警报"
            AlertType.SLEEP -> "睡眠警报"
            AlertType.USAGE -> "使用时长警报"
            AlertType.STEP -> "步数警报"
        }
    }
    
    fun getStatusText(): String {
        return when (status) {
            AlertStatus.PENDING -> "待发送"
            AlertStatus.SUCCESS -> "发送成功"
            AlertStatus.FAILED -> "发送失败"
            AlertStatus.CANCELLED -> "已取消"
        }
    }
    
    fun getFormattedTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date(timestamp))
    }
}