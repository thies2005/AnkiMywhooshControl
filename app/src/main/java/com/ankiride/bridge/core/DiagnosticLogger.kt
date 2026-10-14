package com.ankiride.bridge.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogCategory {
    SYSTEM,
    PERMS,
    BLE,
    PACKET,
    ROUTER,
    ANKI,
    MYWHOOSH
}

enum class LogLevel {
    INFO,
    WARN,
    ERROR
}

data class LogEntry(
    val id: Long,
    val timestampMs: Long,
    val formattedTime: String,
    val category: LogCategory,
    val level: LogLevel,
    val message: String
)

object DiagnosticLogger {
    const val MAX_LOG_ENTRIES = 500
    private var nextId = 1L
    private val lock = Any()
    private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun log(
        category: LogCategory,
        message: String,
        level: LogLevel = LogLevel.INFO,
        nowMs: Long = System.currentTimeMillis()
    ) {
        synchronized(lock) {
            val formatted = synchronized(timeFormatter) {
                timeFormatter.format(Date(nowMs))
            }
            val entry = LogEntry(
                id = nextId++,
                timestampMs = nowMs,
                formattedTime = formatted,
                category = category,
                level = level,
                message = message
            )
            val current = _logs.value
            val updated = if (current.size >= MAX_LOG_ENTRIES) {
                current.drop(current.size - MAX_LOG_ENTRIES + 1) + entry
            } else {
                current + entry
            }
            _logs.value = updated
        }
    }

    fun info(category: LogCategory, message: String) = log(category, message, LogLevel.INFO)
    fun warn(category: LogCategory, message: String) = log(category, message, LogLevel.WARN)
    fun error(category: LogCategory, message: String) = log(category, message, LogLevel.ERROR)

    fun clear() {
        synchronized(lock) {
            _logs.value = emptyList()
        }
    }

    fun formatForClipboard(filterCategory: LogCategory? = null): String {
        val snapshot = _logs.value.let { list ->
            if (filterCategory == null) list else list.filter { it.category == filterCategory }
        }
        if (snapshot.isEmpty()) return "[AnkiRide Bridge Log Empty]"
        val sb = StringBuilder()
        sb.appendLine("=== AnkiRide Bridge Diagnostic Log (${snapshot.size} entries) ===")
        for (item in snapshot) {
            sb.append('[')
                .append(item.formattedTime)
                .append("] [")
                .append(item.level.name)
                .append("] [")
                .append(item.category.name)
                .append("] ")
                .appendLine(item.message)
        }
        return sb.toString()
    }
}
