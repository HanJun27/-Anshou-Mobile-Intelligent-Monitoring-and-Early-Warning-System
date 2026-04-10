package com.livewell.untils

import android.content.Context
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * ✅ 全局日志记录工具
 * 将所有日志写入文件，方便查看和调试
 */
object LogWriter {
    
    private var logFile: File? = null
    private var writer: BufferedWriter? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    /**
     * 初始化日志文件
     */
    fun init(context: Context) {
        try {
            logFile = File(context.filesDir, "app_logs.txt")
            
            // 如果文件超过 1MB，清空旧日志
            if (logFile!!.exists() && logFile!!.length() > 1024 * 1024) {
                logFile!!.delete()
            }
            
            writer = BufferedWriter(FileWriter(logFile, true)) // append mode
            writeLog("SYSTEM", "日志系统已初始化")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 写入日志
     */
    @Synchronized
    fun writeLog(tag: String, message: String) {
        try {
            if (writer == null) {
                return
            }
            
            val timestamp = dateFormat.format(Date())
            val logLine = "[$timestamp] [$tag] $message\n"
            
            writer?.write(logLine)
            writer?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 关闭日志写入器
     */
    @Synchronized
    fun close() {
        try {
            writer?.close()
            writer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * ✅ 辅助函数：同时写入 Logcat 和文件日志
 * 使用方式：logI("TAG", "message")
 */
fun logI(tag: String, message: String) {
    android.util.Log.i(tag, message)
    com.livewell.untils.LogWriter.writeLog(tag, message)
}

fun logD(tag: String, message: String) {
    android.util.Log.d(tag, message)
    com.livewell.untils.LogWriter.writeLog(tag, message)
}

fun logW(tag: String, message: String) {
    android.util.Log.w(tag, message)
    com.livewell.untils.LogWriter.writeLog(tag, message)
}

fun logE(tag: String, message: String, throwable: Throwable? = null) {
    android.util.Log.e(tag, message, throwable)
    com.livewell.untils.LogWriter.writeLog(tag, if (throwable != null) "$message - ${throwable.message}" else message)
}

/**
 * ✅ 统一日志工具类（同时输出到 Logcat 和文件）
 */
object AppLogger {
    
    fun i(tag: String, message: String) {
        android.util.Log.i(tag, message)
        LogWriter.writeLog(tag, message)
    }
    
    fun d(tag: String, message: String) {
        android.util.Log.d(tag, message)
        LogWriter.writeLog(tag, message)
    }
    
    fun w(tag: String, message: String) {
        android.util.Log.w(tag, message)
        LogWriter.writeLog(tag, message)
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        android.util.Log.e(tag, message, throwable)
        val errorMsg = if (throwable != null) "$message - ${throwable.message}" else message
        LogWriter.writeLog(tag, errorMsg)
    }
}
