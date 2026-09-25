package com.example

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.ui.DeskClockMainScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: ClockViewModel by viewModels()

    private val requestBluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val scanGranted = permissions[android.Manifest.permission.BLUETOOTH_SCAN] ?: true
        val connectGranted = permissions[android.Manifest.permission.BLUETOOTH_CONNECT] ?: true
        if (scanGranted && connectGranted) {
            Log.i(TAG, "Bluetooth permissions granted. Retrying sensor connection...")
            viewModel.retryEspSensorConnection()
        }
    }

    private fun checkAndRequestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissionsNeeded = mutableListOf<String>()
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.BLUETOOTH_SCAN)
            }
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (permissionsNeeded.isNotEmpty()) {
                requestBluetoothPermissionLauncher.launch(permissionsNeeded.toTypedArray())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestBluetoothPermissions()

        val restartReason = intent?.getStringExtra("RESTART_REASON")
        if (restartReason != null) {
            com.example.data.CrashLogManager.logWarn("MainActivity", "MainActivity launched via automated recovery: $restartReason")
        } else {
            com.example.data.CrashLogManager.logInfo("MainActivity", "MainActivity onCreate (standard launch)")
        }

        // Lock strictly to landscape orientation for dedicated tabletop kiosk use
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Keep screen on continuously for tabletop desk clock mode
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Show over lockscreen and turn screen on automatically if device was turned on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // Enable edge to edge and immersive full screen
        enableEdgeToEdge()
        configureImmersiveMode()

        // Handle hardware and software back gestures with double-press confirmation to prevent accidental home exit
        var lastBackPressTime = 0L
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.preferences.value.isKioskLocked) {
                    // Strictly block back action when kiosk is locked
                    Log.d(TAG, "Back pressed ignored due to Kiosk lock")
                    configureImmersiveMode()
                    return
                }
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2000L) {
                    // Two presses within 2 seconds: minimize to background
                    com.example.data.CrashLogManager.logInfo("MainActivity", "User pressed back twice to minimize app")
                    moveTaskToBack(true)
                } else {
                    lastBackPressTime = now
                    android.widget.Toast.makeText(this@MainActivity, "もう一度「戻る」を押すと最小化します", android.widget.Toast.LENGTH_SHORT).show()
                    configureImmersiveMode()
                }
            }
        })

        // Observe kiosk lock state to engage/disengage screen pinning (startLockTask)
        lifecycleScope.launch {
            viewModel.preferences.collectLatest { prefs ->
                updateLockTaskMode(prefs.isKioskLocked)
            }
        }

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                DeskClockMainScreen(
                    viewModel = viewModel,
                    onUserInteraction = { configureImmersiveMode() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.example.data.CrashLogManager.setAppForeground(true)
        configureImmersiveMode()
        if (viewModel.preferences.value.isKioskLocked) {
            updateLockTaskMode(true)
        }
        viewModel.resumeIpCamera()
        viewModel.retryEspSensorConnection()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.retryEspSensorConnection()
    }

    override fun onPause() {
        super.onPause()
        com.example.data.CrashLogManager.setAppForeground(false)
        viewModel.pauseIpCamera()
    }

    override fun onDestroy() {
        if (isFinishing) {
            com.example.data.CrashLogManager.notifyCleanExit()
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            configureImmersiveMode()
            if (viewModel.preferences.value.isKioskLocked) {
                updateLockTaskMode(true)
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        // Reinforce immersive mode on every user interaction to suppress navigation bar popup on Android 7.1.2
        configureImmersiveMode()
        return super.dispatchTouchEvent(ev)
    }

    @SuppressLint("RestrictedApi", "GestureBackNavigation")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (viewModel.preferences.value.isKioskLocked) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_HOME,
                KeyEvent.KEYCODE_APP_SWITCH,
                KeyEvent.KEYCODE_MENU -> {
                    configureImmersiveMode()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @SuppressLint("RestrictedApi", "GestureBackNavigation")
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (viewModel.preferences.value.isKioskLocked) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_HOME,
                KeyEvent.KEYCODE_APP_SWITCH,
                KeyEvent.KEYCODE_MENU -> {
                    configureImmersiveMode()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun updateLockTaskMode(isLocked: Boolean) {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
            val isInLockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
            } else {
                @Suppress("DEPRECATION")
                activityManager.isInLockTaskMode
            }

            if (isLocked && !isInLockMode) {
                Log.i(TAG, "Entering Screen Pinning / Lock Task Mode")
                startLockTask()
            } else if (!isLocked && isInLockMode) {
                Log.i(TAG, "Exiting Screen Pinning / Lock Task Mode")
                stopLockTask()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to toggle lockTaskMode: ${e.message}")
        }
        configureImmersiveMode()
    }

    @Suppress("DEPRECATION")
    private fun configureImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        // Explicit low-level flags for Android 7.1.2 (API 25) and older Android devices
        val immersiveFlags = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
        if (window.decorView.systemUiVisibility != immersiveFlags) {
            window.decorView.systemUiVisibility = immersiveFlags
        }

        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if ((visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0 || (visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
                // If navigation or status bar becomes visible, automatically hide it immediately
                window.decorView.post {
                    if (!isFinishing && !isDestroyed) {
                        window.decorView.systemUiVisibility = immersiveFlags
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
        }
    }
}
