package com.livewell.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
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

    companion object {
        // ✅ 主动等待网络的最长时间。凌晨 Doze 下唤起蜂窝网络通常需要 5-20 秒，
        //   给到 45 秒留足余量。
        private const val NETWORK_WAIT_TIMEOUT_MS = 45_000L
        // ✅ WakeLock 持有时间：网络等待 + 邮件发送，给 90 秒上限防止泄漏
        private const val WAKELOCK_TIMEOUT_MS = 90_000L
    }

    fun sendEmail(
        host: String,
        port: String,
        fromEmail: String,
        authCode: String,
        toEmail: String,
        subject: String,
        content: String,
        callback: SendCallback,
        context: Context
    ) {
        Log.i(tag, "🚀 sendEmail 方法被调用，准备启动线程")
        com.livewell.untils.AppLogger.i(tag, "🚀 sendEmail 方法被调用，准备启动线程")

        val appContext = context.applicationContext

        thread {
            Log.i(tag, "✅ 邮件发送线程已启动")
            com.livewell.untils.AppLogger.i(tag, "✅ 邮件发送线程已启动")

            // ✅ 关键修复 1：获取 PARTIAL_WAKE_LOCK，保证凌晨 Doze 下 CPU 不被挂起，
            //   否则 setExactAndAllowWhileIdle 给的执行窗口极短，网络还没起来线程就被冻结。
            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "LiveWell:MailSender"
            )
            try {
                wakeLock.acquire(WAKELOCK_TIMEOUT_MS)
            } catch (e: Exception) {
                Log.w(tag, "WakeLock 获取失败：${e.message}")
            }

            val connectivityManager =
                appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            var boundNetwork: Network? = null
            var didBindProcess = false

            try {
                // ✅ 关键修复 2：主动等待 / 唤起网络，而不是只等 2 秒被动检查
                val network = awaitUsableNetwork(connectivityManager)

                if (network == null) {
                    Log.e(tag, "❌ 等待 ${NETWORK_WAIT_TIMEOUT_MS / 1000} 秒后网络仍不可用，邮件发送失败")
                    com.livewell.untils.AppLogger.e(tag, "❌ 等待 ${NETWORK_WAIT_TIMEOUT_MS / 1000} 秒后网络仍不可用")
                    postError(callback, "网络不可用")
                    return@thread
                }

                boundNetwork = network

                // ✅ 关键修复 3：把本进程的网络流量绑定到拿到的这张网（通常是被唤起的蜂窝网），
                //   否则即使网络已 available，默认路由仍可能指向被挂起的 WiFi。
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        didBindProcess = connectivityManager.bindProcessToNetwork(network)
                        Log.i(tag, "🔗 已绑定进程到可用网络：$didBindProcess")
                        com.livewell.untils.AppLogger.i(tag, "🔗 已绑定进程到可用网络：$didBindProcess")
                    } catch (e: Exception) {
                        Log.w(tag, "绑定进程到网络失败（继续尝试默认路由）：${e.message}")
                    }
                }

                Log.i(tag, "✅ 网络可用，开始发送邮件")
                com.livewell.untils.AppLogger.i(tag, "✅ 网络可用，开始发送邮件")

                Log.i(tag, "====== 开始发送邮件 ======")
                Log.i(tag, "SMTP 主机：$host")
                Log.i(tag, "SMTP 端口：$port")
                Log.i(tag, "发件邮箱：$fromEmail")
                Log.i(tag, "收件邮箱：$toEmail")
                Log.i(tag, "主题：$subject")

                val props = Properties().apply {
                    put("mail.smtp.host", host)
                    put("mail.smtp.port", port)
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.ssl.trust", host)

                    // ✅ 关键修复 4：加上连接/读/写超时，避免凌晨半连接的 socket 永久挂死，
                    //   也让重试机制能在合理时间内拿到失败结果而不是卡住。
                    put("mail.smtp.connectiontimeout", "20000")
                    put("mail.smtp.timeout", "30000")
                    put("mail.smtp.writetimeout", "30000")

                    // 对于 465 端口使用 SSL
                    if (port == "465") {
                        put("mail.smtp.socketFactory.port", port)
                        put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                        put("mail.smtp.socketFactory.fallback", "false")
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
                com.livewell.untils.AppLogger.i(tag, "📧 正在调用 Transport.send()...")
                Transport.send(message)
                Log.i(tag, "邮件发送成功")
                com.livewell.untils.AppLogger.i(tag, "✅ Transport.send() 执行成功，邮件已发送")

                postSuccess(callback)

            } catch (e: MessagingException) {
                e.printStackTrace()
                Log.e(tag, "邮件发送失败：${e.message}")
                com.livewell.untils.AppLogger.e(tag, "❌ MessagingException：${e.message}")
                com.livewell.untils.AppLogger.e(tag, "异常堆栈：${e.stackTraceToString()}")
                postError(callback, "邮件发送失败：${e.message}")
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(tag, "未知错误：${e.message}")
                com.livewell.untils.AppLogger.e(tag, "❌ 未知异常：${e.message}")
                com.livewell.untils.AppLogger.e(tag, "异常堆栈：${e.stackTraceToString()}")
                postError(callback, "未知错误：${e.message}")
            } finally {
                // ✅ 恢复默认网络路由，避免影响 App 其他网络请求
                if (didBindProcess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        connectivityManager.bindProcessToNetwork(null)
                    } catch (e: Exception) {
                        Log.w(tag, "解绑进程网络失败：${e.message}")
                    }
                }
                if (wakeLock.isHeld) {
                    try {
                        wakeLock.release()
                    } catch (e: Exception) {
                        Log.w(tag, "释放 WakeLock 失败：${e.message}")
                    }
                }
            }
        }

        Log.i(tag, "✅ sendEmail 方法执行完毕（线程已启动）")
        com.livewell.untils.AppLogger.i(tag, "✅ sendEmail 方法执行完毕（线程已启动）")
    }

    /**
     * ✅ 主动等待一张可用（具备 INTERNET 能力）的网络。
     *
     * 1. 快速路径：当前已有具备 INTERNET 能力的网络，直接返回。
     * 2. 慢速路径：通过 requestNetwork() 主动请求系统唤起一张网络（凌晨 Doze 下能唤起蜂窝），
     *    用 CountDownLatch 阻塞等待回调，最长 NETWORK_WAIT_TIMEOUT_MS。
     * 3. 兜底：若 requestNetwork 抛异常（如缺权限），退化为轮询 activeNetwork。
     */
    private fun awaitUsableNetwork(cm: ConnectivityManager): Network? {
        // 1. 快速路径
        currentInternetNetwork(cm)?.let {
            Log.i(tag, "🔍 已有可用网络，直接使用")
            com.livewell.untils.AppLogger.i(tag, "🔍 已有可用网络，直接使用")
            return it
        }

        Log.w(tag, "⚠️ 当前无可用网络，主动请求唤起网络（最长等待 ${NETWORK_WAIT_TIMEOUT_MS / 1000} 秒）...")
        com.livewell.untils.AppLogger.w(tag, "⚠️ 当前无可用网络，主动请求唤起网络...")

        // 2. 慢速路径：主动请求网络
        val latch = CountDownLatch(1)
        val result = AtomicReference<Network?>(null)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val netCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                result.set(network)
                latch.countDown()
            }
        }

        var requested = false
        try {
            cm.requestNetwork(request, netCallback)
            requested = true
            latch.await(NETWORK_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            // 例如缺少 CHANGE_NETWORK_STATE 权限时抛 SecurityException
            Log.e(tag, "requestNetwork 失败，退化为轮询：${e.message}")
            com.livewell.untils.AppLogger.e(tag, "requestNetwork 失败，退化为轮询：${e.message}")
        } finally {
            if (requested) {
                try {
                    cm.unregisterNetworkCallback(netCallback)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }

        result.get()?.let { return it }

        // 3. 兜底：轮询一段时间，看默认网络是否恢复
        val deadline = System.currentTimeMillis() + 5_000L
        while (System.currentTimeMillis() < deadline) {
            currentInternetNetwork(cm)?.let { return it }
            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        return currentInternetNetwork(cm)
    }

    /**
     * 返回当前具备 INTERNET 能力的活动网络（无则返回 null）
     */
    private fun currentInternetNetwork(cm: ConnectivityManager): Network? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return null
                val capabilities = cm.getNetworkCapabilities(network) ?: return null
                val ok = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                if (ok) network else null
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = cm.activeNetworkInfo
                @Suppress("DEPRECATION")
                if (networkInfo != null && networkInfo.isConnected) cm.activeNetwork else null
            }
        } catch (e: Exception) {
            Log.e(tag, "网络检查失败：${e.message}")
            null
        }
    }

    private fun postSuccess(callback: SendCallback) {
        Handler(Looper.getMainLooper()).post {
            Log.i(tag, "🔔 调用 callback.onSuccess()")
            com.livewell.untils.AppLogger.i(tag, "🔔 调用 callback.onSuccess()")
            callback.onSuccess()
        }
    }

    private fun postError(callback: SendCallback, error: String) {
        Handler(Looper.getMainLooper()).post {
            Log.i(tag, "🔔 调用 callback.onError()：$error")
            com.livewell.untils.AppLogger.i(tag, "🔔 调用 callback.onError()：$error")
            callback.onError(error)
        }
    }
}
