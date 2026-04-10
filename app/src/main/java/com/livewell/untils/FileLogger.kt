package com.livewell.untils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * 文件日志工具类
 * 将日志同时输出到 logcat 和文件
 */
object FileLogger {
    
    private const val LOG_FILE_NAME = "app_logs.txt"
    private const val MAX_LOG_LINES = 1000  // 最大保留行数
    
    @Volatile
    private var context: Context? = null
    
    /**
     * 初始化日志工具
     */
    fun init(appContext: Context) {
        context = appContext.applicationContext
    }
    
    /**
     * 记录 INFO 级别日志
     */
    fun i(tag: String, message: String) {
        Log.i(tag, message)
        writeLog("I", tag, message)
    }
    
    /**
     * 记录 DEBUG 级别日志
     */
    fun d(tag: String, message: String) {
        Log.d(tag, message)
        writeLog("D", tag, message)
    }
    
    /**
     * 记录 WARN 级别日志
     */
    fun w(tag: String, message: String) {
        Log.w(tag, message)
        writeLog("W", tag, message)
    }
    
    /**
     * 记录 ERROR 级别日志
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        writeLog("E", tag, message)
        if (throwable != null) {
            writeLog("E", tag, "异常: ${throwable.message}")
        }
    }
    
    /**
     * 写入日志到文件
     */
    private fun writeLog(level: String, tag: String, message: String) {
        val ctx = context ?: return
        
        try {
            val logFile = File(ctx.filesDir, LOG_FILE_NAME)
            
            // 检查文件大小，如果太大则清空
            if (logFile.exists() && logFile.length() > 5 * 1024 * 1024) { // 5MB
                clearLogs()
            }
            
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                .format(Date())
            
            val logLine = "$timestamp [$level] $tag: $message\n"
            
            // 追加写入
            FileWriter(logFile, true).use { writer ->
                writer.append(logLine)
            }
            
            // 限制行数
            trimLogFile(logFile)
            
        } catch (e: Exception) {
            Log.e("FileLogger", "写入日志失败: ${e.message}", e)
        }
    }
    
    /**
     * 修剪日志文件，保留最新的 MAX_LOG_LINES 行
     */
    private fun trimLogFile(logFile: File) {
        try {
            if (!logFile.exists()) return
            
            val lines = logFile.readLines()
            
            if (lines.size > MAX_LOG_LINES) {
                // 保留最新的 MAX_LOG_LINES 行
                val trimmedLines = lines.takeLast(MAX_LOG_LINES)
                logFile.writeText(trimmedLines.joinToString("\n") + "\n")
            }
        } catch (e: Exception) {
            Log.e("FileLogger", "修剪日志文件失败: ${e.message}", e)
        }
    }
    
    /**
     * 清空日志文件
     */
    fun clearLogs() {
        val ctx = context ?: return
        
        try {
            val logFile = File(ctx.filesDir, LOG_FILE_NAME)
            if (logFile.exists()) {
                logFile.delete()
            }
            Log.i("FileLogger", "日志已清空")
        } catch (e: Exception) {
            Log.e("FileLogger", "清空日志失败: ${e.message}", e)
        }
    }
    
    /**
     * 获取日志文件
     */
    fun getLogFile(): File? {
        val ctx = context ?: return null
        val logFile = File(ctx.filesDir, LOG_FILE_NAME)
        return if (logFile.exists()) logFile else null
    }
}
