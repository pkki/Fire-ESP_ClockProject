package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.MainActivity
import com.example.data.CrashLogManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        CrashLogManager.logInfo("BootReceiver", "Received broadcast action: $action")

        // Ensure watchdog is scheduled on any system event
        WatchdogReceiver.scheduleNextWatchdog(context)

        val isCleanExit = CrashLogManager.isCleanExit(context)
        if (isCleanExit && action != Intent.ACTION_BOOT_COMPLETED) {
            // User specifically exited app, don't force launch on power connect
            return
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("RESTART_REASON", "BROADCAST_$action")
        }
        try {
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            CrashLogManager.logWarn("BootReceiver", "Failed to launch MainActivity on $action: ${e.message}")
        }
    }
}
