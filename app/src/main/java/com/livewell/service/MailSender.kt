package com.livewell.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import kotlin.concurrent.thread

class MailSender {
    
    private val tag = "MailSender"
    
    interface SendCallback {
        fun onSuccess()
        fun onError(error: String)
    }
    
    // ✅ 添加 Context 参数
    fun sendEmail(
        host: String,
        port: String,
        fromEmail: String,
        authCode: String,
        toEmail: String,
        subject: String,
        content: String,
        callback: SendCallback,
        context: Context  // ✅ 新增参数
    ) {
        thread {
            // ✅ 检查网络状态
            if (!isNetworkAvailable(context)) {
                Log.e(tag, "网络不可用，邮件发送失败")
                Handler(Looper.getMainLooper()).post {
                    callback.onError("网络不可用")
                }
                return@thread
            }
            
            Log.i(tag, "====== 开始发送邮件 ======")
            Log.i(tag, "SMTP 主机：$host")
            Log.i(tag, "SMTP 端口：$port")
            Log.i(tag, "发件邮箱：$fromEmail")
            Log.i(tag, "收件邮箱：$toEmail")
            Log.i(tag, "主题：$subject")
            
            try {
                val props = Properties().apply {
                    put("mail.smtp.host", host)
                    put("mail.smtp.port", port)
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.ssl.trust", host)
                    
                    // 对于 465 端口使用 SSL
                    if (port == "465") {
                        put("mail.smtp.socketFactory.port", port)
                        put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                    }
                }
                
                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(fromEmail, authCode)
                    }
                })
                
                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(fromEmail))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
                    setSubject(subject)
                    setText(content)
                    sentDate = Date()
                }
                
                Log.i(tag, "正在发送...")
                Transport.send(message)
                Log.i(tag, "邮件发送成功")
                
                // 回调到主线程
                Handler(Looper.getMainLooper()).post {
                    callback.onSuccess()
                }
                
            } catch (e: MessagingException) {
                e.printStackTrace()
                Log.e(tag, "邮件发送失败：${e.message}")
                Handler(Looper.getMainLooper()).post {
                    callback.onError("邮件发送失败：${e.message}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(tag, "未知错误：${e.message}")
                Handler(Looper.getMainLooper()).post {
                    callback.onError("未知错误：${e.message}")
                }
            }
        }
    }
    
    // ✅ 添加网络检查方法
    private fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                )
            } else {
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo != null && networkInfo.isConnected
            }
        } catch (e: Exception) {
            Log.e(tag, "网络检查失败：${e.message}")
            false
        }
    }
}