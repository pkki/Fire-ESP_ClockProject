package com.example

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.example.data.CrashLogManager

class DeskClockApplication : Application() {
    companion object {
        private const val TAG = "DeskClockApp"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize persistent crash, heartbeat and operational logging system
        CrashLogManager.initialize(this)

        // Install global uncaught exception watchdog
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "Uncaught exception intercepted in thread ${thread.name}: ${throwable.message}", throwable)
                CrashLogManager.recordCrashAndRestart(this, thread, throwable)
            } catch (t: Throwable) {
                Log.e(TAG, "Fatal error in crash handler", t)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        // Register Activity Lifecycle Callbacks to track all UI state transitions
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                CrashLogManager.logLifecycle(activity.javaClass.simpleName, "onCreated", "savedState=${savedInstanceState != null}")
            }

            override fun onActivityStarted(activity: Activity) {
                CrashLogManager.logLifecycle(activity.javaClass.simpleName, "onStarted")
            }

            override fun onActivityResumed(activity: Activity) {
                CrashLogManager.logLifecycle(activity.javaClass.simpleName, "onResumed", "isFinishing=${activity.isFinishing}")
            }

            override fun onActivityPaused(activity: Activity) {
                CrashLogManager.logLifecycle(activity.javaClass.simpleName, "onPaused", "isFinishing=${activity.isFinishing}")
            }

            override fun onActivityStopped(activity: Activity) {
                CrashLogManager.logLifecycle(activity.javaClass.simpleName, "onStopped", "isFinishing=${activity.isFinishing}")
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                CrashLogManager.logLifecycle(activity.javaClass.simpleName, "onSaveInstanceState")
            }

            override fun onActivityDestroyed(activity: Activity) {
                CrashLogManager.logLifecycle(activity.javaClass.simpleName, "onDestroyed", "isFinishing=${activity.isFinishing}")
            }
        })

        CrashLogManager.logInfo("System", "UncaughtExceptionHandler & Watchdog active. 24/7 continuous operation enabled.")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        CrashLogManager.logTrimMemory(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        CrashLogManager.logWarn("MemoryWatchdog", "⚠️ onLowMemory() 受信: システム全体のRAMが極めて逼迫しています。")
    }
}
