package com.example.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.example.MainActivity
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

data class LogItem(
    val timestamp: String,
    val level: String,
    val tag: String,
    val message: String,
    val stackTrace: String? = null
)

data class DiagnosticsReport(
    val isHealthy: Boolean,
    val uptimeFormatted: String,
    val totalStarts: Int,
    val crashRecoveryCount: Int,
    val abnormalTerminationCount: Int,
    val lastCrashTime: String?,
    val lastCrashMessage: String?,
    val lastAbnormalTerminationTime: String?,
    val lastAbnormalTerminationMessage: String?,
    val usedMemoryMb: Long,
    val maxMemoryMb: Long,
    val freeMemoryMb: Long,
    val osVersion: String,
    val deviceModel: String,
    val crashLogCount: Int,
    val totalLogCount: Int
)

object CrashLogManager {
    private const val TAG = "CrashLogManager"
    private const val PREFS_NAME = "desk_clock_stability_prefs"
    private const val KEY_TOTAL_STARTS = "total_starts"
    private const val KEY_CRASH_RECOVERIES = "crash_recoveries"
    private const val KEY_ABNORMAL_TERMINATIONS = "abnormal_terminations"
    private const val KEY_LAST_CRASH_TIME = "last_crash_time"
    private const val KEY_LAST_CRASH_MSG = "last_crash_msg"
    private const val KEY_LAST_ABNORMAL_TIME = "last_abnormal_time"
    private const val KEY_LAST_ABNORMAL_MSG = "last_abnormal_msg"
    private const val KEY_APP_START_TIME = "app_start_time"
    private const val KEY_LAST_HEARTBEAT_MS = "last_heartbeat_ms"
    private const val KEY_PROCESS_IS_RUNNING = "process_is_running"
    private const val KEY_CLEAN_EXIT = "clean_exit"

    private const val CRASH_LOG_FILE = "crash_logs.txt"
    private const val SYSTEM_LOG_FILE = "system_logs.txt"
    private const val MAX_IN_MEMORY_LOGS = 400
    private const val MAX_LOG_FILE_BYTES = 1024 * 1024 // 1MB limit for log file

    private val inMemoryLogs = ConcurrentLinkedQueue<LogItem>()
    private var appContext: Context? = null
    private val appStartTimeMs = System.currentTimeMillis()
    private val isHeartbeatActive = AtomicBoolean(false)
    private val isForeground = AtomicBoolean(false)
    private val fileWriteExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DeskClock-LogWriter").apply { isDaemon = true }
    }

    private val dateFormat: SimpleDateFormat
        get() {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.JAPAN)
            sdf.timeZone = TimeZone.getTimeZone("Asia/Tokyo")
            return sdf
        }

    fun isAppForeground(): Boolean = isForeground.get()

    fun setAppForeground(foreground: Boolean) {
        isForeground.set(foreground)
    }

    fun isCleanExit(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CLEAN_EXIT, false)
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentStarts = prefs.getInt(KEY_TOTAL_STARTS, 0) + 1

        // Check if the previous run terminated abruptly without clean exit (e.g. OS Low Memory Killer, SIGKILL, Sleep)
        val prevRunning = prefs.getBoolean(KEY_PROCESS_IS_RUNNING, false)
        val prevCleanExit = prefs.getBoolean(KEY_CLEAN_EXIT, false)
        val prevLastHeartbeat = prefs.getLong(KEY_LAST_HEARTBEAT_MS, 0L)
        val recoveries = prefs.getInt(KEY_CRASH_RECOVERIES, 0)
        var abnormalTerminations = prefs.getInt(KEY_ABNORMAL_TERMINATIONS, 0)
        var lastAbnormalTime = prefs.getString(KEY_LAST_ABNORMAL_TIME, null)

        val editor = prefs.edit()
            .putInt(KEY_TOTAL_STARTS, currentStarts)
            .putLong(KEY_APP_START_TIME, appStartTimeMs)
            .putBoolean(KEY_PROCESS_IS_RUNNING, true)
            .putBoolean(KEY_CLEAN_EXIT, false)
            .putLong(KEY_LAST_HEARTBEAT_MS, System.currentTimeMillis())

        if (prevRunning && !prevCleanExit && prevLastHeartbeat > 0L) {
            val gapSec = (System.currentTimeMillis() - prevLastHeartbeat) / 1000
            val lastHbFormatted = dateFormat.format(Date(prevLastHeartbeat))
            abnormalTerminations++
            lastAbnormalTime = lastHbFormatted
            val abnormalMsg = "OS強制終了(LMK/スリープ等)を検知。最終生存: $lastHbFormatted (停止から再起動まで: ${gapSec}秒)"
            editor.putInt(KEY_ABNORMAL_TERMINATIONS, abnormalTerminations)
            editor.putString(KEY_LAST_ABNORMAL_TIME, lastAbnormalTime)
            editor.putString(KEY_LAST_ABNORMAL_MSG, abnormalMsg)

            editor.apply()

            logWarn("ProcessWatchdog", "⚠️ 前回セッションのOS強制終了を検知しました: 最終生存時刻 $lastHbFormatted (停止から再起動まで ${gapSec}秒)。Java例外ではなくAndroid/Fire OSによるLow-Memory Killer(SIGKILL)またはスリープによるActivity破棄と推定されます。")
        } else {
            editor.apply()
        }

        val lastCrashTime = prefs.getString(KEY_LAST_CRASH_TIME, null)
        val startMsg = buildString {
            append("Desk Clock Application initialized successfully (Launch #$currentStarts).")
            if (recoveries > 0 && lastCrashTime != null) {
                append(" [Javaクラッシュ復帰履歴: $recoveries 回, 最終: $lastCrashTime]")
            }
            if (abnormalTerminations > 0 && lastAbnormalTime != null) {
                append(" [OS強制キル検知履歴: $abnormalTerminations 回, 最終: $lastAbnormalTime]")
            }
        }
        logInfo("AppLifecycle", startMsg)

        startHeartbeat()

        // Start 24/7 periodic watchdog alarm
        try {
            com.example.service.WatchdogReceiver.scheduleNextWatchdog(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule initial watchdog: ${e.message}")
        }
    }

    private fun startHeartbeat() {
        if (isHeartbeatActive.compareAndSet(false, true)) {
            Thread {
                while (isHeartbeatActive.get()) {
                    try {
                        Thread.sleep(15000) // Heartbeat every 15 seconds
                        val ctx = appContext ?: continue
                        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        prefs.edit()
                            .putLong(KEY_LAST_HEARTBEAT_MS, System.currentTimeMillis())
                            .putBoolean(KEY_PROCESS_IS_RUNNING, true)
                            .apply()
                    } catch (_: InterruptedException) {
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "Heartbeat update failed: ${e.message}")
                    }
                }
            }.apply {
                isDaemon = true
                name = "DeskClock-Heartbeat"
                start()
            }
        }
    }

    fun notifyCleanExit() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_CLEAN_EXIT, true)
            .putBoolean(KEY_PROCESS_IS_RUNNING, false)
            .apply()
        logInfo("AppLifecycle", "Clean app shutdown recorded by user action.")
    }

    fun logLifecycle(activityName: String, event: String, details: String = "") {
        val detailStr = if (details.isNotEmpty()) " - $details" else ""
        logInfo("Lifecycle", "$activityName :: $event$detailStr")
    }

    fun logTrimMemory(level: Int) {
        val levelName = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "TRIM_MEMORY_RUNNING_MODERATE (端末メモリやや逼迫)"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "TRIM_MEMORY_RUNNING_LOW (端末メモリ危険領域)"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "TRIM_MEMORY_RUNNING_CRITICAL (極度のメモリ枯渇! LMK寸前)"
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "TRIM_MEMORY_UI_HIDDEN (UIが非表示化)"
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "TRIM_MEMORY_BACKGROUND (バックグラウンド化)"
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "TRIM_MEMORY_MODERATE (バックグラウンド中程度)"
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "TRIM_MEMORY_COMPLETE (完全バックグラウンド・優先削除対象)"
            else -> "TRIM_MEMORY Level $level"
        }
        logWarn("MemoryWatchdog", "⚠️ システムメモリ警告受信: $levelName")
    }

    fun logInfo(tag: String, message: String) {
        log("INFO", tag, message, null)
    }

    fun logWarn(tag: String, message: String, throwable: Throwable? = null) {
        log("WARN", tag, message, throwable)
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        log("ERROR", tag, message, throwable)
    }

    fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val stackTrace = throwable?.let { getStackTraceString(it) }
        val timeStr = dateFormat.format(Date())

        when (level) {
            "INFO" -> Log.i(tag, message, throwable)
            "WARN" -> Log.w(tag, message, throwable)
            "ERROR" -> Log.e(tag, message, throwable)
            "CRASH" -> Log.wtf(tag, message, throwable)
            else -> Log.d(tag, message, throwable)
        }

        val item = LogItem(
            timestamp = timeStr,
            level = level,
            tag = tag,
            message = message,
            stackTrace = stackTrace
        )

        inMemoryLogs.add(item)
        while (inMemoryLogs.size > MAX_IN_MEMORY_LOGS) {
            inMemoryLogs.poll()
        }

        // Persist to system_logs.txt asynchronously
        writeToFileAsync(SYSTEM_LOG_FILE, formatLogLine(item))
    }

    @Synchronized
    fun recordCrashAndRestart(context: Context, thread: Thread, throwable: Throwable) {
        val timestamp = dateFormat.format(Date())
        val stackTrace = getStackTraceString(throwable)
        val errorMsg = throwable.message ?: throwable.javaClass.simpleName

        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMem = runtime.maxMemory() / (1024 * 1024)
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        val crashReport = buildString {
            appendLine("================================================================================")
            appendLine("CRASH DETECTED AT: $timestamp")
            appendLine("Thread: ${thread.name} (ID: ${thread.id}, Priority: ${thread.priority})")
            appendLine("Device: $deviceModel | OS: $osVersion")
            appendLine("Memory: Used ${usedMem}MB / Max ${maxMem}MB | Uptime: ${SystemClock.elapsedRealtime() / 1000}s")
            appendLine("Exception: ${throwable.javaClass.name}: $errorMsg")
            appendLine("Stack Trace:")
            appendLine(stackTrace)
            appendLine("================================================================================")
            appendLine()
        }

        Log.e(TAG, "FATAL CRASH RECORDED:\n$crashReport")

        // Synchronously save crash report
        try {
            val file = File(context.filesDir, CRASH_LOG_FILE)
            FileOutputStream(file, true).use { out ->
                out.write(crashReport.toByteArray(Charsets.UTF_8))
                out.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash log file", e)
        }

        // Also update shared preferences
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val recoveries = prefs.getInt(KEY_CRASH_RECOVERIES, 0) + 1
            prefs.edit()
                .putInt(KEY_CRASH_RECOVERIES, recoveries)
                .putString(KEY_LAST_CRASH_TIME, timestamp)
                .putString(KEY_LAST_CRASH_MSG, "${throwable.javaClass.simpleName}: $errorMsg")
                .putBoolean(KEY_PROCESS_IS_RUNNING, false)
                .commit() // Synchronous commit
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update crash prefs", e)
        }

        // Trigger automatic restart: short delay if running stably, or 5s backoff if rapid startup crash
        val isStartupCrash = (System.currentTimeMillis() - appStartTimeMs) < 3000L
        val delayMs = if (isStartupCrash) 5000L else 600L
        scheduleImmediateRestart(context, delayMs)

        // Terminate dying process
        Process.killProcess(Process.myPid())
        System.exit(10)
    }

    fun scheduleImmediateRestart(context: Context, delayMs: Long = 600L) {
        try {
            val restartIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("RESTART_REASON", "AUTO_RECOVERED_FROM_CRASH")
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                1001,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager != null) {
                val triggerAt = System.currentTimeMillis() + delayMs
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
                Log.i(TAG, "Scheduled auto-restart via AlarmManager in ${delayMs}ms")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule restart", e)
        }
    }

    fun getDiagnosticsReport(): DiagnosticsReport {
        val ctx = appContext
        val prefs = ctx?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val totalStarts = prefs?.getInt(KEY_TOTAL_STARTS, 1) ?: 1
        val crashRecoveries = prefs?.getInt(KEY_CRASH_RECOVERIES, 0) ?: 0
        val abnormalTerminations = prefs?.getInt(KEY_ABNORMAL_TERMINATIONS, 0) ?: 0
        val lastCrashTime = prefs?.getString(KEY_LAST_CRASH_TIME, null)
        val lastCrashMsg = prefs?.getString(KEY_LAST_CRASH_MSG, null)
        val lastAbnormalTime = prefs?.getString(KEY_LAST_ABNORMAL_TIME, null)
        val lastAbnormalMsg = prefs?.getString(KEY_LAST_ABNORMAL_MSG, null)

        val runtime = Runtime.getRuntime()
        val totalMem = runtime.totalMemory() / (1024 * 1024)
        val freeMem = runtime.freeMemory() / (1024 * 1024)
        val usedMem = totalMem - freeMem
        val maxMem = runtime.maxMemory() / (1024 * 1024)

        val uptimeSec = (System.currentTimeMillis() - appStartTimeMs) / 1000
        val hours = uptimeSec / 3600
        val minutes = (uptimeSec % 3600) / 60
        val seconds = uptimeSec % 60
        val uptimeFormatted = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

        val crashLogFile = ctx?.let { File(it.filesDir, CRASH_LOG_FILE) }
        val crashCount = if (crashLogFile != null && crashLogFile.exists()) {
            val text = crashLogFile.readText()
            text.split("CRASH DETECTED AT:").size - 1
        } else {
            0
        }

        return DiagnosticsReport(
            isHealthy = (crashRecoveries == 0 && abnormalTerminations == 0),
            uptimeFormatted = uptimeFormatted,
            totalStarts = totalStarts,
            crashRecoveryCount = crashRecoveries,
            abnormalTerminationCount = abnormalTerminations,
            lastCrashTime = lastCrashTime,
            lastCrashMessage = lastCrashMsg,
            lastAbnormalTerminationTime = lastAbnormalTime,
            lastAbnormalTerminationMessage = lastAbnormalMsg,
            usedMemoryMb = usedMem,
            maxMemoryMb = maxMem,
            freeMemoryMb = freeMem,
            osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            crashLogCount = crashCount,
            totalLogCount = inMemoryLogs.size
        )
    }

    fun getCrashLogsText(): String {
        val ctx = appContext ?: return "Log storage not initialized"
        val file = File(ctx.filesDir, CRASH_LOG_FILE)
        return if (file.exists()) {
            file.readText()
        } else {
            "No crash logs recorded. System is running cleanly."
        }
    }

    fun getRecentLogs(limit: Int = 100): List<LogItem> {
        val list = inMemoryLogs.toList()
        return if (list.size <= limit) list.reversed() else list.takeLast(limit).reversed()
    }

    fun getAllLogsText(): String {
        val sb = StringBuilder()
        val report = getDiagnosticsReport()
        sb.appendLine("=== DESK CLOCK DIAGNOSTICS REPORT ===")
        sb.appendLine("Uptime: ${report.uptimeFormatted} | Total Starts: ${report.totalStarts} | Java Crash Recoveries: ${report.crashRecoveryCount} | OS Forced Kills: ${report.abnormalTerminationCount}")
        sb.appendLine("Memory: ${report.usedMemoryMb}MB used / ${report.maxMemoryMb}MB max | Device: ${report.deviceModel} (${report.osVersion})")
        if (report.lastCrashTime != null) {
            sb.appendLine("Last Java Crash: ${report.lastCrashTime} (${report.lastCrashMessage})")
        }
        if (report.lastAbnormalTerminationTime != null) {
            sb.appendLine("Last OS Force-Kill / Sleep Eviction: ${report.lastAbnormalTerminationTime} (${report.lastAbnormalTerminationMessage})")
        }
        sb.appendLine()
        sb.appendLine("=== CRASH LOG HISTORY ===")
        sb.appendLine(getCrashLogsText())
        sb.appendLine()
        sb.appendLine("=== RECENT SYSTEM EVENT LOGS ===")
        inMemoryLogs.forEach { item ->
            sb.appendLine(formatLogLine(item))
            if (item.stackTrace != null) {
                sb.appendLine(item.stackTrace)
            }
        }
        return sb.toString()
    }

    fun clearAllLogs() {
        inMemoryLogs.clear()
        val ctx = appContext ?: return
        try {
            val crashFile = File(ctx.filesDir, CRASH_LOG_FILE)
            if (crashFile.exists()) crashFile.delete()

            val sysFile = File(ctx.filesDir, SYSTEM_LOG_FILE)
            if (sysFile.exists()) sysFile.delete()

            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt(KEY_CRASH_RECOVERIES, 0)
                .putInt(KEY_ABNORMAL_TERMINATIONS, 0)
                .remove(KEY_LAST_CRASH_TIME)
                .remove(KEY_LAST_CRASH_MSG)
                .remove(KEY_LAST_ABNORMAL_TIME)
                .remove(KEY_LAST_ABNORMAL_MSG)
                .apply()

            logInfo("Diagnostics", "All system and crash logs have been cleared.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear logs", e)
        }
    }

    fun triggerTestCrash() {
        logWarn("Diagnostics", "Test crash initiated by user request to verify auto-restart & logging mechanism.")
        Thread {
            throw RuntimeException("MANUAL TEST CRASH: Verifying automated recovery and persistent diagnostic logging.")
        }.start()
    }

    private fun formatLogLine(item: LogItem): String {
        return "[${item.timestamp}] [${item.level}] [${item.tag}] ${item.message}"
    }

    private fun writeToFileAsync(filename: String, line: String) {
        val ctx = appContext ?: return
        fileWriteExecutor.execute {
            try {
                val file = File(ctx.filesDir, filename)
                if (file.exists() && file.length() > MAX_LOG_FILE_BYTES) {
                    // Truncate older half of the file
                    val lines = file.readLines()
                    val keepLines = lines.takeLast(lines.size / 2)
                    file.writeText(keepLines.joinToString("\n") + "\n")
                }
                FileOutputStream(file, true).use { out ->
                    out.write((line + "\n").toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error writing to log file: ${e.message}")
            }
        }
    }

    private fun getStackTraceString(t: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        t.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }
}
