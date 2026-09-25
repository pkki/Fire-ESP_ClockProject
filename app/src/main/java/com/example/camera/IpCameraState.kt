package com.example.camera

data class IpCameraConfig(
    val isEnabled: Boolean = false,
    val port: Int = 8080,
    val useFrontCamera: Boolean = true,
    val targetFps: Int = 10,
    val resolutionWidth: Int = 640,
    val resolutionHeight: Int = 480,
    val jpegQuality: Int = 75,
    val enableAudio: Boolean = true,
    val audioGain: Float = 1.0f,
    val showMiniPreviewOnClock: Boolean = false
)

data class IpCameraStatus(
    val isRunning: Boolean = false,
    val serverUrl: String = "",
    val localIpAddress: String = "",
    val port: Int = 8080,
    val clientCount: Int = 0,
    val actualFps: Float = 0f,
    val useFrontCamera: Boolean = true,
    val isAudioEnabled: Boolean = true,
    val isAudioStreaming: Boolean = false,
    val audioClientCount: Int = 0,
    val audioLevelPercent: Int = 0,
    val errorMessage: String? = null
)
