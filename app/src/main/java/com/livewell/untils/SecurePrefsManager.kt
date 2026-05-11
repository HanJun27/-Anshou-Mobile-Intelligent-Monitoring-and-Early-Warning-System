package com.livewell.untils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SecurePrefsManager(context: Context) {
    
    private val tag = "SecurePrefsManager"
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    
    private val encryptedPrefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            "secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(tag, "❌ EncryptedSharedPreferences 初始化失败：${e.message}", e)
        // ✅ 降级方案：使用普通 SharedPreferences
        context.getSharedPreferences("secure_prefs_fallback", Context.MODE_PRIVATE)
    }
    
    // ✅ 降级方案：普通 SharedPreferences
    private val fallbackPrefs: SharedPreferences = context.getSharedPreferences("secure_prefs_fallback", Context.MODE_PRIVATE)
    
    fun saveEmailCredentials(email: String, authCode: String) {
        encryptedPrefs.edit().putString("email_account", email).apply()
        encryptedPrefs.edit().putString("email_auth_code", authCode).apply()
    }
    
    fun getEmailAccount(): String? {
        return encryptedPrefs.getString("email_account", null)
    }
    
    fun getEmailAuthCode(): String? {
        return encryptedPrefs.getString("email_auth_code", null)
    }
    
    fun saveSmtpConfig(host: String, port: String) {
        encryptedPrefs.edit().putString("smtp_host", host).apply()
        encryptedPrefs.edit().putString("smtp_port", port).apply()
    }
    
    fun getSmtpHost(): String? {
        return encryptedPrefs.getString("smtp_host", null)
    }
    
    fun getSmtpPort(): String? {
        return encryptedPrefs.getString("smtp_port", null)
    }

    // ✅ 报警时间设置 - 使用 encryptedPrefs
    fun setAlertCheckTime(hour: Int, minute: Int) {
        encryptedPrefs.edit().putInt("alert_check_hour", hour).apply()
        encryptedPrefs.edit().putInt("alert_check_minute", minute).apply()
    }

    fun getAlertCheckHour(): Int {
        return encryptedPrefs.getInt("alert_check_hour", 23)
    }

    fun getAlertCheckMinute(): Int {
        return encryptedPrefs.getInt("alert_check_minute", 0)
    }
}