package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.example.MainActivity
import com.example.data.CrashLogManager

class WatchdogReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_WATCHDOG_PING = "com.example.action.WATCHDOG_PING"
        private const val WATCHDOG_INTERVAL_MS = 90 * 1000L // 90 seconds keep-alive

        fun scheduleNextWatchdog(context: Context, delayMs: Long = WATCHDOG_INTERVAL_MS) {
            try {
                val intent = Intent(context, WatchdogReceiver::class.java).apply {
                    action = ACTION_WATCHDOG_PING
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    2002,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                )
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val triggerAt = SystemClock.elapsedRealtime() + delayMs

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
                }
            } catch (e: Exception) {
                CrashLogManager.logWarn("Watchdog", "Failed to schedule next watchdog: ${e.message}")
            }
        }

        fun cancelWatchdog(context: Context) {
            try {
                val intent = Intent(context, WatchdogReceiver::class.java).apply {
                    action = ACTION_WATCHDOG_PING
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    2002,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                )
                if (pendingIntent != null) {
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                    alarmManager?.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            } catch (_: Exception) {}
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        // Re-arm watchdog timer for 24/7 continuous resilience
        scheduleNextWatchdog(context)

        val isCleanExit = CrashLogManager.isCleanExit(context)
        val isAppInForeground = CrashLogManager.isAppForeground()

        if (!isCleanExit && !isAppInForeground) {
            CrashLogManager.logWarn("Watchdog", "Watchdog triggered: Desk Clock not in foreground. Relaunching...")

            // Wake up CPU and screen briefly to ensure UI surfaces
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                @Suppress("DEPRECATION")
                val wakeLock = powerManager?.newWakeLock(
                    android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    android.os.PowerManager.ON_AFTER_RELEASE,
                    "DeskClock:WatchdogWakeLock"
                )
                wakeLock?.acquire(4000L)
            } catch (e: Exception) {
                CrashLogManager.logWarn("Watchdog", "Failed to acquire temporary wake lock: ${e.message}")
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("RESTART_REASON", "WATCHDOG_AUTO_RECOVERY")
            }
            try {
                context.startActivity(launchIntent)
            } catch (e: Exception) {
                CrashLogManager.logError("Watchdog", "Failed to relaunch MainActivity from watchdog", e)
            }
        }
    }
}
