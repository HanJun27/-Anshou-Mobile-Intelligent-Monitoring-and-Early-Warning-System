package com.livewell.untils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SecurePrefsManager(context: Context) {
    
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    
    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        "secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
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