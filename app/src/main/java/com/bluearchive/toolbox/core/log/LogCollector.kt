package com.bluearchive.toolbox.core.log

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轻量日志收集器。
 * - 内存中保留最近 N 条日志，供 UI 展示/导出
 * - 同时追加写入应用缓存目录下的 log.txt，便于长期排查
 *
 * 日志格式：[时间] [级别] [标签] 消息
 */
object LogCollector {

    enum class Level { D, I, W, E }

    data class Entry(
        val time: Long,
        val level: Level,
        val tag: String,
        val message: String,
    )

    private const val MAX_MEMORY_ENTRIES = 500
    private val entries = ArrayDeque<Entry>(MAX_MEMORY_ENTRIES)
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private var logFile: File? = null

    /** 在 Application 初始化时调用，指定日志文件路径 */
    fun init(context: Context) {
        runCatching {
            val dir = File(context.cacheDir, "logs").apply { mkdirs() }
            logFile = File(dir, "toolbox.log")
        }
    }

    fun d(tag: String, message: String) = append(Level.D, tag, message)
    fun i(tag: String, message: String) = append(Level.I, tag, message)
    fun w(tag: String, message: String) = append(Level.W, tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val msg = if (throwable != null) "$message\n${throwable.stackTraceToString().take(500)}" else message
        append(Level.E, tag, msg)
    }

    @Synchronized
    private fun append(level: Level, tag: String, message: String) {
        val entry = Entry(System.currentTimeMillis(), level, tag, message)
        if (entries.size >= MAX_MEMORY_ENTRIES) entries.removeFirst()
        entries.addLast(entry)
        // 异步写文件，避免阻塞调用线程
        runCatching {
            logFile?.appendText("${formatTime(entry.time)} [${level.name}] [$tag] ${entry.message}\n")
        }
    }

    private fun formatTime(time: Long): String = dateFormat.format(Date(time))

    /** 获取所有内存日志的文本形式 */
    @Synchronized
    fun getLogsText(): String = entries.joinToString("\n") {
        "${formatTime(it.time)} [${it.level.name}] [${it.tag}] ${it.message}"
    }

    /** 获取日志文件 */
    fun getLogFile(): File? = logFile

    /** 清空内存日志和日志文件 */
    @Synchronized
    fun clear() {
        entries.clear()
        runCatching { logFile?.writeText("") }
    }
}
