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
import java.net.InetAddress
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
        // ✅ 主动等待网络的最长时间。凌晨 Doze 下唤起蜂窝网络通常需要 5-20 秒。
        private const val NETWORK_WAIT_TIMEOUT_MS = 45_000L
        // ✅ 单次 SMTP 连接超时（毫秒）。第二轮里设的 20 秒，从最新日志看仍在凌晨 Doze 下被打满，
        //   碰到的是 "Couldn't connect to host, ... timeout 20000"。提到 60 秒，
        //   给从 Doze 唤起后的蜂窝数据通道一个真正完整的握手窗口。
        private const val SMTP_CONNECT_TIMEOUT_MS = 60_000
        private const val SMTP_READ_TIMEOUT_MS = 60_000
        private const val SMTP_WRITE_TIMEOUT_MS = 60_000
        // ✅ WakeLock 持有时间：网络等待 + 两次发送尝试 + 5 秒等待 + 余量
        private const val WAKELOCK_TIMEOUT_MS = 240_000L
        // ✅ 单次发送失败后的重试间隔
        private const val RETRY_DELAY_MS = 5_000L
        // ✅ 最多尝试次数（含首次）
        private const val MAX_ATTEMPTS = 2
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

            // ✅ 关键：PARTIAL_WAKE_LOCK 保证 Doze 下 CPU 不被冻结
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

            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            try {
                // ✅ 1. 主动等待 / 唤起网络
                val network = awaitUsableNetwork(cm)
                if (network == null) {
                    Log.e(tag, "❌ 等待 ${NETWORK_WAIT_TIMEOUT_MS / 1000} 秒后网络仍不可用")
                    com.livewell.untils.AppLogger.e(tag, "❌ 等待 ${NETWORK_WAIT_TIMEOUT_MS / 1000} 秒后网络仍不可用")
                    postError(callback, "网络不可用")
                    return@thread
                }

                // ✅ 2. 诊断日志：网络类型/能力/SMTP 主机 DNS 解析（直接进系统日志查看器）
                logNetworkDiagnostics(cm, network, host)

                // ✅ 3. 最多 MAX_ATTEMPTS 次尝试：首次绑定到拿到的网络；如失败且属于"连接类"错误，
                //    等 RETRY_DELAY_MS 后再做一次"不绑定进程网络"的回退尝试——避免某些机型
                //    bindProcessToNetwork 锁死在还没真正"通"的接口上。
                var lastError: String? = null
                for (attempt in 1..MAX_ATTEMPTS) {
                    val useBind = (attempt == 1)
                    var didBindProcess = false
                    try {
                        if (useBind && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                didBindProcess = cm.bindProcessToNetwork(network)
                                com.livewell.untils.AppLogger.i(tag, "🔗 [尝试$attempt] 绑定进程到网络：$didBindProcess")
                            } catch (e: Exception) {
                                com.livewell.untils.AppLogger.w(tag, "🔗 [尝试$attempt] 绑定网络失败：${e.message}")
                            }
                        } else {
                            com.livewell.untils.AppLogger.i(tag, "🔗 [尝试$attempt] 不绑定进程，走默认路由")
                        }

                        com.livewell.untils.AppLogger.i(tag, "📧 [尝试$attempt/${MAX_ATTEMPTS}] 开始 SMTP 发送（connectTimeout=${SMTP_CONNECT_TIMEOUT_MS / 1000}s）")
                        doSmtpSend(host, port, fromEmail, authCode, toEmail, subject, content)

                        Log.i(tag, "邮件发送成功")
                        com.livewell.untils.AppLogger.i(tag, "✅ [尝试$attempt] Transport.send() 执行成功，邮件已发送")
                        postSuccess(callback)
                        return@thread
                    } catch (e: MessagingException) {
                        lastError = e.message ?: "未知 SMTP 错误"
                        // ✅ 把根因异常类型也打出来，区分"立即拒绝(ConnectException)"还是"真的超时(SocketTimeoutException)"
                        val rootCause = generateSequence<Throwable>(e) { it.cause }.lastOrNull() ?: e
                        Log.e(tag, "[尝试$attempt] MessagingException：$lastError")
                        com.livewell.untils.AppLogger.e(tag, "❌ [尝试$attempt] SMTP 失败：$lastError")
                        com.livewell.untils.AppLogger.e(tag, "↳ 根因异常类型：${rootCause.javaClass.simpleName}：${rootCause.message}")
                        if (!isRetryableSmtp(lastError)) {
                            com.livewell.untils.AppLogger.e(tag, "↳ 错误非\"连接超时类\"，不再重试")
                            break
                        }
                    } catch (e: Exception) {
                        lastError = e.message ?: "未知异常"
                        Log.e(tag, "[尝试$attempt] 未知异常：$lastError")
                        com.livewell.untils.AppLogger.e(tag, "❌ [尝试$attempt] 未知异常：$lastError")
                        break
                    } finally {
                        if (didBindProcess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try { cm.bindProcessToNetwork(null) } catch (_: Exception) {}
                        }
                    }

                    if (attempt < MAX_ATTEMPTS) {
                        com.livewell.untils.AppLogger.w(tag, "⏳ 等待 ${RETRY_DELAY_MS / 1000} 秒后做最后一次重试（不绑定进程网络）")
                        try {
                            Thread.sleep(RETRY_DELAY_MS)
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        }
                    }
                }

                // ✅ 关键新增：端口回退。若用户配置的是 SSL 端口 465，但两次都失败，
                //   尝试一次"端口 587 + STARTTLS"。许多移动运营商在凌晨低功耗时会拦截 SMTPS(465)
                //   但仍允许标准 Submission(587)。163.com 同时支持这两个端口。
                if (port == "465" && lastError != null) {
                    com.livewell.untils.AppLogger.w(tag, "🔁 端口 465 全部失败，回退尝试 587 + STARTTLS（许多运营商凌晨会挡 465 但放行 587）")
                    try {
                        doSmtpSend(host, "587", fromEmail, authCode, toEmail, subject, content)
                        Log.i(tag, "邮件发送成功（587 端口回退）")
                        com.livewell.untils.AppLogger.i(tag, "✅ [回退587] Transport.send() 执行成功，邮件已发送")
                        postSuccess(callback)
                        return@thread
                    } catch (e: Exception) {
                        val msg = e.message ?: "未知错误"
                        val rootCause = generateSequence<Throwable>(e) { it.cause }.lastOrNull() ?: e
                        com.livewell.untils.AppLogger.e(tag, "❌ [回退587] 失败：$msg")
                        com.livewell.untils.AppLogger.e(tag, "↳ 根因异常类型：${rootCause.javaClass.simpleName}：${rootCause.message}")
                        lastError = "$lastError ；587 回退也失败：$msg"
                    }
                }

                postError(callback, "邮件发送失败：${lastError ?: "未知错误"}")
            } finally {
                if (wakeLock.isHeld) {
                    try { wakeLock.release() } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * 单次 SMTP 发送（同步、抛异常）
     */
    private fun doSmtpSend(
        host: String,
        port: String,
        fromEmail: String,
        authCode: String,
        toEmail: String,
        subject: String,
        content: String
    ) {
        val props = Properties().apply {
            put("mail.smtp.host", host)
            put("mail.smtp.port", port)
            put("mail.smtp.auth", "true")
            put("mail.smtp.ssl.trust", host)
            // ✅ 60 秒级超时，覆盖凌晨从 Doze 唤起后蜂窝数据通道完全可用的窗口
            put("mail.smtp.connectiontimeout", SMTP_CONNECT_TIMEOUT_MS.toString())
            put("mail.smtp.timeout", SMTP_READ_TIMEOUT_MS.toString())
            put("mail.smtp.writetimeout", SMTP_WRITE_TIMEOUT_MS.toString())

            when (port) {
                "465" -> {
                    // SMTPS：连上即 SSL，STARTTLS 不适用
                    put("mail.smtp.starttls.enable", "false")
                    put("mail.smtp.socketFactory.port", port)
                    put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                    put("mail.smtp.socketFactory.fallback", "false")
                }
                "587", "25" -> {
                    // 标准 Submission 端口：先明文 TCP，再用 STARTTLS 升级到 TLS
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.starttls.required", "true")
                }
                else -> {
                    put("mail.smtp.starttls.enable", "true")
                }
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

        Transport.send(message)
    }

    /**
     * 判定 SMTP 错误是否值得"等几秒再试一次"
     */
    private fun isRetryableSmtp(msg: String): Boolean {
        val low = msg.lowercase(Locale.ROOT)
        return low.contains("couldn't connect") ||
            low.contains("connect to host") ||
            low.contains("timeout") ||
            low.contains("timed out") ||
            low.contains("connection reset") ||
            low.contains("network is unreachable") ||
            low.contains("no route to host") ||
            low.contains("sslhandshake") ||
            low.contains("unknownhost")
    }

    /**
     * 主动等待可用网络（先快速路径、再 requestNetwork、最后轮询兜底）
     */
    private fun awaitUsableNetwork(cm: ConnectivityManager): Network? {
        currentInternetNetwork(cm)?.let {
            Log.i(tag, "🔍 已有可用网络，直接使用")
            com.livewell.untils.AppLogger.i(tag, "🔍 已有可用网络，直接使用")
            return it
        }

        Log.w(tag, "⚠️ 当前无可用网络，主动请求唤起网络（最长 ${NETWORK_WAIT_TIMEOUT_MS / 1000} 秒）")
        com.livewell.untils.AppLogger.w(tag, "⚠️ 当前无可用网络，主动请求唤起网络...")

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
            Log.e(tag, "requestNetwork 失败，退化为轮询：${e.message}")
            com.livewell.untils.AppLogger.e(tag, "requestNetwork 失败，退化为轮询：${e.message}")
        } finally {
            if (requested) {
                try { cm.unregisterNetworkCallback(netCallback) } catch (_: Exception) {}
            }
        }

        result.get()?.let { return it }

        val deadline = System.currentTimeMillis() + 5_000L
        while (System.currentTimeMillis() < deadline) {
            currentInternetNetwork(cm)?.let { return it }
            try { Thread.sleep(500) } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        return currentInternetNetwork(cm)
    }

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

    /**
     * 把"拿到的是哪种网"、"是否被系统验证过"、"SMTP 主机 DNS 解析结果"
     * 全部写进系统日志查看器，方便事后排查为什么凌晨连不上。
     */
    private fun logNetworkDiagnostics(cm: ConnectivityManager, network: Network, host: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val caps = cm.getNetworkCapabilities(network)
                val transport = when {
                    caps == null -> "未知"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "蜂窝"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
                    else -> "其他"
                }
                val validated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && caps != null)
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) else false
                val notMetered = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && caps != null)
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) else false
                com.livewell.untils.AppLogger.i(tag, "🌐 网络类型：$transport，已验证：$validated，非计量：$notMetered")
            }
        } catch (e: Exception) {
            com.livewell.untils.AppLogger.w(tag, "诊断网络能力失败：${e.message}")
        }

        // DNS 解析诊断（用绑定的 network 解析，反映这条网真的能查到 SMTP 主机）
        try {
            val addrs: Array<InetAddress>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try { network.getAllByName(host) } catch (e: Exception) { null }
            } else null
            if (addrs != null && addrs.isNotEmpty()) {
                val ipList = addrs.joinToString { it.hostAddress ?: "?" }
                com.livewell.untils.AppLogger.i(tag, "🌐 $host 解析到：$ipList")
            } else {
                // fallback：用默认 DNS
                val def = try { InetAddress.getAllByName(host) } catch (e: Exception) { null }
                if (def != null && def.isNotEmpty()) {
                    com.livewell.untils.AppLogger.i(tag, "🌐 $host 默认 DNS 解析：${def.joinToString { it.hostAddress ?: "?" }}")
                } else {
                    com.livewell.untils.AppLogger.w(tag, "🌐 $host DNS 解析失败")
                }
            }
        } catch (e: Exception) {
            com.livewell.untils.AppLogger.w(tag, "诊断 DNS 失败：${e.message}")
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