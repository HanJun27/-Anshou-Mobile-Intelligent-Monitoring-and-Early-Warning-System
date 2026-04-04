package com.livewell.untils

import android.content.Context
import android.telephony.SmsManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import com.livewell.receiver.SmsSentReceiver

class SmsSender(private val context: Context) {
    
    interface SmsSendCallback {
        fun onSuccess()
        fun onError(error: String)
    }
    
    fun sendAlertSms(phoneNumber: String, message: String, callback: SmsSendCallback? = null): Boolean {
        return try {
            val smsManager = SmsManager.getDefault()
            
            val sentIntent = PendingIntent.getBroadcast(
                context,
                System.currentTimeMillis().toInt(),
                Intent("SMS_SENT").apply {
                    putExtra("message", message)
                    putExtra("phone_number", phoneNumber)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val messages = smsManager.divideMessage(message)
            
            if (messages.size > 1) {
                val sentIntents = ArrayList<PendingIntent>()
                for (i in messages.indices) {
                    sentIntents.add(PendingIntent.getBroadcast(
                        context,
                        (System.currentTimeMillis() + i).toInt(),
                        Intent("SMS_SENT").apply {
                            putExtra("message", message)
                            putExtra("phone_number", phoneNumber)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    ))
                }
                smsManager.sendMultipartTextMessage(phoneNumber, null, messages, sentIntents, null)
            } else {
                smsManager.sendTextMessage(
                    phoneNumber,
                    null,
                    message,
                    sentIntent,
                    null
                )
            }
            
            Log.i("SmsSender", "短信已加入发送队列：$phoneNumber")
            true
        } catch (e: Exception) {
            Log.e("SmsSender", "短信发送失败：${e.message}")
            callback?.onError(e.message ?: "未知错误")
            false
        }
    }
}