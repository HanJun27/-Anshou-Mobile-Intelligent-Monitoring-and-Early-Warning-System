package com.livewell.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import com.livewell.untils.PrefsManager
import com.livewell.model.AlertHistoryRecord
import com.livewell.model.AlertType
import com.livewell.model.AlertStatus
import com.livewell.model.AlertMethod

class SmsSentReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val phoneNumber = intent.getStringExtra("phone_number") ?: ""
        val message = intent.getStringExtra("message") ?: ""
        
        val status = when (resultCode) {
            android.app.Activity.RESULT_OK -> {
                Log.i("SmsSentReceiver", "短信发送成功")
                Toast.makeText(context, "短信已发送", Toast.LENGTH_SHORT).show()
                AlertStatus.SUCCESS
            }
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                Log.e("SmsSentReceiver", "短信发送失败：通用错误")
                Toast.makeText(context, "短信发送失败", Toast.LENGTH_SHORT).show()
                AlertStatus.FAILED
            }
            SmsManager.RESULT_ERROR_NO_SERVICE -> {
                Log.e("SmsSentReceiver", "短信发送失败：无服务")
                Toast.makeText(context, "无网络服务", Toast.LENGTH_SHORT).show()
                AlertStatus.FAILED
            }
            SmsManager.RESULT_ERROR_NULL_PDU -> {
                Log.e("SmsSentReceiver", "短信发送失败：PDU 错误")
                Toast.makeText(context, "短信格式错误", Toast.LENGTH_SHORT).show()
                AlertStatus.FAILED
            }
            SmsManager.RESULT_ERROR_RADIO_OFF -> {
                Log.e("SmsSentReceiver", "短信发送失败：飞行模式")
                Toast.makeText(context, "飞行模式", Toast.LENGTH_SHORT).show()
                AlertStatus.FAILED
            }
            else -> {
                Log.e("SmsSentReceiver", "短信发送失败：未知错误")
                AlertStatus.FAILED
            }
        }
        
        val prefsManager = PrefsManager(context)
        prefsManager.addAlertHistory(AlertHistoryRecord(
            timestamp = System.currentTimeMillis(),
            type = AlertType.CHECKIN,
            status = status,
            method = AlertMethod.SMS,
            content = message.take(50),
            reason = ""
        ))
    }
}