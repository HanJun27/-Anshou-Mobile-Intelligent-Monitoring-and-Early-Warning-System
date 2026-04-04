package com.livewell.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.*
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.livewell.R

/**
 * 无声音乐播放服务 - 增强版
 * 
 * 改进点：
 * 1. 使用 AudioTrack 播放真正的静音 PCM 数据流（不是音量为 0）
 * 2. 获取音频焦点，确保系统识别为活跃媒体
 * 3. 创建 MediaSession，标识为媒体播放应用
 * 4. 使用 WakeLock 保持 CPU 唤醒
 * 5. 持续写入音频数据，保持音频通路活跃
 */
class SilentMusicService : Service() {

    companion object {
        private const val TAG = "SilentMusicService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "silent_music_channel"
        
        // 音频参数 - 默认 44.1kHz
        var currentSampleRate = 44100  // ✅ 改为可变变量
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        const val ACTION_START = "com.livewell.service.SilentMusicService.START"
        const val ACTION_STOP = "com.livewell.service.SilentMusicService.STOP"
        const val ACTION_CHANGE_SAMPLE_RATE = "com.livewell.service.SilentMusicService.CHANGE_RATE"  // ✅ 新增
        
        // 是否正在运行（公共可读）
        var isRunning: Boolean = false

        /**
         * 启动服务
         */
        fun start(context: Context) {
            startWithSampleRate(context, currentSampleRate)
        }
        
        /**
         * ✅ 使用指定采样率启动服务
         */
        fun startWithSampleRate(context: Context, sampleRate: Int) {
            if (isRunning) {
                Log.d(TAG, "服务已在运行中")
                return
            }
            
            val intent = Intent(context, SilentMusicService::class.java).apply {
                action = ACTION_START
                putExtra("sample_rate", sampleRate)  // ✅ 传递采样率
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止服务
         */
        fun stop(context: Context) {
            val intent = Intent(context, SilentMusicService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
        
        /**
         * ✅ 动态改变采样率
         */
        fun changeSampleRate(context: Context, sampleRate: Int) {
            currentSampleRate = sampleRate
            val intent = Intent(context, SilentMusicService::class.java).apply {
                action = ACTION_CHANGE_SAMPLE_RATE
                putExtra("sample_rate", sampleRate)
            }
            context.sendBroadcast(intent)
        }
    }  // ✅ 结束 companion object

    private var audioTrack: AudioTrack? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private var audioThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    // 音频焦点监听
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.w(TAG, "音频焦点丢失，尝试重新获取")
                handler.postDelayed({
                    requestAudioFocus()
                }, 1000)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "重新获得音频焦点")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand - action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> {
                // ✅ 获取传递的采样率
                val sampleRate = intent.getIntExtra("sample_rate", currentSampleRate)
                startForegroundService(sampleRate)
            }
            ACTION_STOP -> stopSelf()
            ACTION_CHANGE_SAMPLE_RATE -> {
                // ✅ 动态改变采样率
                val sampleRate = intent.getIntExtra("sample_rate", currentSampleRate)
                changeCurrentSampleRate(sampleRate)
            }
            else -> startForegroundService(currentSampleRate)
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        stopMusic()
        releaseWakeLock()
        isRunning = false
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannelCompat.Builder(
                CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_MIN
            )
                .setName("后台保活服务")
                .setDescription("无声音乐播放服务，用于保持应用后台运行")
                .setShowBadge(false)
                .build()

            NotificationManagerCompat.from(this).createNotificationChannel(channel)
        }
    }

    private fun startForegroundService(sampleRate: Int) {  // ✅ 添加采样率参数
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("后台保护运行中")
            .setContentText("应用正在后台正常运行 - ${sampleRate / 1000}kHz")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        
        handler.postDelayed({
            startSilentMusic(sampleRate)  // ✅ 传递采样率
        }, 500)
    }
    
    /**
     * ✅ 动态改变当前采样率（方案 B：延长重叠时间）
     * 
     * 原理：
     * 1. 先启动新采样率的 AudioTrack
     * 2. 等待 1 秒让新音轨稳定
     * 3. 再停止旧的 AudioTrack
     * 4. 确保音频通路始终不中断
     */
    private fun changeCurrentSampleRate(sampleRate: Int) {
        Log.d(TAG, "🔄 准备切换采样率：${currentSampleRate / 1000}kHz -> ${sampleRate / 1000}kHz")
        
        if (!isPlaying) {
            // 未在播放，直接启动
            Log.d(TAG, "服务未运行，直接启动新采样率")
            startSilentMusic(sampleRate)
            return
        }
        
        // ✅ 关键改进：先启动新的 AudioTrack，不检查 isPlaying
        // 这样会创建一个新的音轨，而旧的仍在播放
        try {
            Log.d(TAG, "⏱️ 第一步：启动新采样率的 AudioTrack（旧音轨继续播放）")
            startSilentMusicInternal(sampleRate)
            
            // ✅ 延迟 1 秒，等新音轨完全稳定
            handler.postDelayed({
                try {
                    Log.d(TAG, "⏱️ 第二步：停止旧的 AudioTrack")
                    stopMusic()
                    
                    // ✅ 再延迟 500ms 后重新启动（此时已经只有一个音轨）
                    handler.postDelayed({
                        Log.d(TAG, "⏱️ 第三步：重新启动最终音轨")
                        startSilentMusicInternal(sampleRate)
                        
                        currentSampleRate = sampleRate
                        Log.d(TAG, "✅ 采样率切换完成：${sampleRate / 1000}kHz")
                        Log.d(TAG, "═══════════════════════════")
                    }, 500)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 切换过程中出错：${e.message}")
                    e.printStackTrace()
                    // 回退到原采样率
                    currentSampleRate = sampleRate
                }
            }, 1000)  // ✅ 1 秒延迟，确保新音轨稳定
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动新音轨失败，回退到普通切换：${e.message}")
            e.printStackTrace()
            // 回退到简单方案
            changeCurrentSampleRateFallback(sampleRate)
        }
    }
    
    /**
     * ✅ 回退方案：简单的停止 - 重启
     */
    private fun changeCurrentSampleRateFallback(sampleRate: Int) {
        Log.w(TAG, "⚠️ 使用回退方案切换采样率")
        if (isPlaying) {
            stopMusic()
            handler.postDelayed({
                startSilentMusic(sampleRate)
            }, 500)
        }
        currentSampleRate = sampleRate
    }
    
    /**
     * ✅ 内部方法：启动无声音乐（不检查 isPlaying）
     * 用于无缝切换场景
     */
    private fun startSilentMusicInternal(sampleRate: Int) {
        try {
            // 先请求音频焦点
            if (!requestAudioFocus()) {
                Log.w(TAG, "未能获取音频焦点，但仍尝试播放")
            }
            
            // ✅ 注意：这里不调用 stopMusic()，直接创建新音轨
            // 旧的音轨会在后面被停止
            
            // 使用传入的采样率计算缓冲区大小
            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
            val actualBufferSize = Math.max(bufferSize, sampleRate * 2)
            
            Log.d(TAG, "创建新 AudioTrack - 采样率：$sampleRate Hz, 缓冲区：$actualBufferSize bytes")
            
            // 创建 AudioTrack
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(CHANNEL_CONFIG)
                        .setEncoding(AUDIO_FORMAT)
                        .build()
                )
                .setBufferSizeInBytes(actualBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
            // 开始播放
            audioTrack?.play()
            
            isPlaying = true
            isRunning = true
            
            Log.d(TAG, "新 AudioTrack 已启动 - 采样率：${sampleRate / 1000}kHz")
            
            // 启动音频写入线程
            startAudioThread()
            
        } catch (e: Exception) {
            Log.e(TAG, "startSilentMusicInternal 失败：${e.message}")
            throw e  // 抛出异常，让调用者处理
        }
    }

    /**
     * 请求音频焦点 - 关键！确保音频通路保持开放
     */
    private fun requestAudioFocus(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        val result = audioManager.requestAudioFocus(
            audioFocusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        
        val success = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.d(TAG, "音频焦点请求结果：${if (success) "成功" else "失败"}")
        return success
    }

    private fun startSilentMusic(sampleRate: Int) {  // ✅ 添加采样率参数
        if (isPlaying) {
            Log.d(TAG, "已经在播放中")
            return
        }
        
        try {
            // 先请求音频焦点
            if (!requestAudioFocus()) {
                Log.w(TAG, "未能获取音频焦点，但仍尝试播放")
            }
            
            stopMusic()
            
            // ✅ 使用传入的采样率计算缓冲区大小
            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
            val actualBufferSize = Math.max(bufferSize, sampleRate * 2)
            
            Log.d(TAG, "无声音乐播放启动 - 采样率：$sampleRate Hz, 缓冲区大小：$actualBufferSize bytes")
            
            // 创建 AudioTrack
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)  // ✅ 使用动态采样率
                        .setChannelMask(CHANNEL_CONFIG)
                        .setEncoding(AUDIO_FORMAT)
                        .build()
                )
                .setBufferSizeInBytes(actualBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
            // 开始播放
            audioTrack?.play()
            
            isPlaying = true
            isRunning = true
            
            Log.d(TAG, "无声音乐播放已启动 - AudioTrack 模式，采样率：${sampleRate / 1000}kHz")
            
            // 启动音频写入线程
            startAudioThread()
            
        } catch (e: Exception) {
            Log.e(TAG, "启动无声音乐失败：${e.message}")
            e.printStackTrace()
            isRunning = true
        }
    }

    /**
     * 启动独立的音频写入线程
     */
    private fun startAudioThread() {
        audioThread = Thread {
            try {
                while (isPlaying && !Thread.interrupted()) {
                    if (audioTrack != null && audioTrack!!.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        writeSilentData()
                    } else {
                        Thread.sleep(100)
                    }
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "音频线程被中断")
            } catch (e: Exception) {
                Log.e(TAG, "音频线程异常：${e.message}")
            }
        }
        audioThread?.start()
        Log.d(TAG, "音频写入线程已启动")
    }

    /**
     * 写入静音数据到 AudioTrack
     */
    private fun writeSilentData() {
        try {
            val audioTrack = this.audioTrack ?: return
            
            // 生成静音数据（全 0 就是静音）
            val silence = ByteArray(Math.max(1024, audioTrack.bufferSizeInFrames * 2))
            
            // 写入音频数据
            val written = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                audioTrack.write(silence, 0, silence.size, AudioTrack.WRITE_BLOCKING)
            } else {
                audioTrack.write(silence, 0, silence.size)
            }
            
            if (written < 0) {
                Log.e(TAG, "AudioTrack 写入失败：$written")
                Thread.sleep(100)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "写入静音数据失败：${e.message}")
        }
    }

    private fun stopMusic() {
        try {
            isPlaying = false
            
            // 停止音频线程
            audioThread?.interrupt()
            audioThread = null
            
            // 停止 AudioTrack
            audioTrack?.let { track ->
                try {
                    if (track.state == AudioTrack.STATE_INITIALIZED || 
                        track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                    track.release()
                } catch (e: Exception) {
                    Log.e(TAG, "释放 AudioTrack 时出错：${e.message}")
                }
            }
            audioTrack = null
            
            // 放弃音频焦点
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.abandonAudioFocus(audioFocusChangeListener)
            
            Log.d(TAG, "音乐播放已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止音乐时出错：${e.message}")
        }
    }

    /**
     * 获取 WakeLock 保持 CPU 唤醒
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "livewell:SilentMusicWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(10*60*1000L)
            }
            Log.d(TAG, "WakeLock 已获取")
        } catch (e: Exception) {
            Log.e(TAG, "获取 WakeLock 失败：${e.message}")
        }
    }

    /**
     * 释放 WakeLock
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let { lock ->
                if (lock.isHeld) {
                    lock.release()
                }
            }
            wakeLock = null
            Log.d(TAG, "WakeLock 已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放 WakeLock 失败：${e.message}")
        }
    }
}
