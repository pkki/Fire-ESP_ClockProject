package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import android.view.Surface
import com.example.model.CustomVideoItem
import com.example.model.VideoDisplayLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

enum class VideoAspectRatio(val label: String) {
    FIT("全体表示 (Fit)"),
    FILL_CROP("全画面 (Fill)"),
    ORIGINAL("元比率")
}

data class VideoPlayerState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentVideo: CustomVideoItem? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 0.85f,
    val isMuted: Boolean = false,
    val isLooping: Boolean = true,
    val playbackSpeed: Float = 1.0f,
    val displayLayer: VideoDisplayLayer = VideoDisplayLayer.BACKGROUND,
    val aspectRatio: VideoAspectRatio = VideoAspectRatio.FIT,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0
) {
    val progressFraction: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val positionFormatted: String
        get() = formatTimeMs(currentPositionMs)

    val durationFormatted: String
        get() = formatTimeMs(durationMs)

    companion object {
        fun formatTimeMs(ms: Long): String {
            if (ms <= 0) return "00:00"
            val totalSec = ms / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            val hrs = min / 60
            return if (hrs > 0) {
                String.format("%02d:%02d:%02d", hrs, min % 60, sec)
            } else {
                String.format("%02d:%02d", min, sec)
            }
        }
    }
}

/**
 * 完璧な動画再生管理マネージャー (VideoPlayerManager)
 * - MP4/WebM等の動画再生・一時停止・再開・停止
 * - 精密シーク (seekTo, +/-10秒スキップ)
 * - 音量調整 (0.0f〜1.0f), ミュート切替
 * - ループ再生 / 単曲再生切替
 * - 再生速度調整 (0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x)
 * - 画面アスペクト比切替 (Fit / Fill)
 * - 前面 (FOREGROUND) / 背景 (BACKGROUND) レイヤー切替
 * - 200ms 高速プログレス・ティッカーによる正確な再生位置同期
 */
object VideoPlayerManager {
    private const val TAG = "VideoPlayerManager"

    private var mediaPlayer: MediaPlayer? = null
    private var attachedSurface: Surface? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    private val _playerState = MutableStateFlow(VideoPlayerState())
    val playerState: StateFlow<VideoPlayerState> = _playerState.asStateFlow()

    var onPlaybackStateChanged: ((isPlaying: Boolean, video: CustomVideoItem?) -> Unit)? = null
    var onVideoCompletion: (() -> Unit)? = null

    fun playVideo(
        video: CustomVideoItem,
        displayLayer: VideoDisplayLayer = _playerState.value.displayLayer,
        playAudio: Boolean = true,
        volume: Float = _playerState.value.volume,
        isLooping: Boolean = true
    ) {
        val file = File(video.filePath)
        if (!file.exists()) {
            Log.e(TAG, "Video file does not exist: ${video.filePath}")
            return
        }

        // Prevent simultaneous playback / audio doubling with Music player
        MusicPlayerManager.stop()

        // Accurate duration & dimension probe beforehand
        val fallbackDur = probeDuration(video.filePath)
        val (initW, initH) = probeVideoDimensions(video.filePath)

        stopInternal(keepVideo = true)

        _playerState.value = _playerState.value.copy(
            currentVideo = video,
            durationMs = fallbackDur,
            videoWidth = if (initW > 0) initW else _playerState.value.videoWidth,
            videoHeight = if (initH > 0) initH else _playerState.value.videoHeight
        )

        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                FileInputStream(file).use { fis ->
                    setDataSource(fis.fd)
                }
                attachedSurface?.let { setSurface(it) }

                this.isLooping = isLooping
                val vol = if (playAudio && !_playerState.value.isMuted) volume.coerceIn(0f, 1f) else 0f
                setVolume(vol, vol)

                val initialSessionId = audioSessionId
                if (initialSessionId > 0 && playAudio) {
                    AudioEqualizerManager.registerAudioSession(initialSessionId)
                }

                setOnVideoSizeChangedListener { _, width, height ->
                    if (width > 0 && height > 0) {
                        _playerState.value = _playerState.value.copy(videoWidth = width, videoHeight = height)
                    }
                }

                setOnPreparedListener { player ->
                    try {
                        val dur = player.duration.toLong().let { if (it > 0) it else fallbackDur }
                        val pw = player.videoWidth
                        val ph = player.videoHeight
                        val finalW = if (pw > 0) pw else initW
                        val finalH = if (ph > 0) ph else initH
                        val sessionId = player.audioSessionId
                        if (sessionId > 0 && playAudio) {
                            AudioEqualizerManager.registerAudioSession(sessionId)
                        }

                        // Apply playback speed
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && _playerState.value.playbackSpeed != 1.0f) {
                            try {
                                player.playbackParams = player.playbackParams.setSpeed(_playerState.value.playbackSpeed)
                            } catch (_: Exception) {}
                        }

                        player.start()
                        _playerState.value = _playerState.value.copy(
                            isPlaying = true,
                            isPaused = false,
                            currentVideo = video,
                            currentPositionMs = 0L,
                            durationMs = dur,
                            volume = volume,
                            isLooping = isLooping,
                            displayLayer = displayLayer,
                            videoWidth = if (finalW > 0) finalW else _playerState.value.videoWidth,
                            videoHeight = if (finalH > 0) finalH else _playerState.value.videoHeight
                        )
                        startProgressTicker()
                        onPlaybackStateChanged?.invoke(true, video)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onPreparedListener", e)
                    }
                }

                setOnCompletionListener {
                    if (_playerState.value.isLooping) {
                        try {
                            mediaPlayer?.seekTo(0)
                            mediaPlayer?.start()
                            startProgressTicker()
                        } catch (_: Exception) {}
                    } else {
                        stopProgressTicker()
                        _playerState.value = _playerState.value.copy(
                            isPlaying = false,
                            isPaused = false,
                            currentPositionMs = 0L
                        )
                        onPlaybackStateChanged?.invoke(false, video)
                        onVideoCompletion?.invoke()
                    }
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    stopInternal(keepVideo = false)
                    true
                }

                setOnSeekCompleteListener { player ->
                    _playerState.value = _playerState.value.copy(
                        currentPositionMs = player.currentPosition.toLong().coerceAtLeast(0L)
                    )
                }

                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MediaPlayer for video: ${video.name}", e)
            stopInternal(keepVideo = false)
        }
    }

    fun attachSurface(surface: Surface) {
        attachedSurface = surface
        try {
            mediaPlayer?.setSurface(surface)
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching surface", e)
        }
    }

    fun detachSurface(surface: Surface? = null) {
        if (surface == null || attachedSurface == surface) {
            attachedSurface = null
            try {
                mediaPlayer?.setSurface(null)
            } catch (_: Exception) {}
        }
    }

    fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                val pos = mediaPlayer?.currentPosition?.toLong() ?: _playerState.value.currentPositionMs
                stopProgressTicker()
                _playerState.value = _playerState.value.copy(
                    isPlaying = false,
                    isPaused = true,
                    currentPositionMs = pos
                )
                onPlaybackStateChanged?.invoke(false, _playerState.value.currentVideo)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing video", e)
        }
    }

    fun resume() {
        MusicPlayerManager.stop()
        val mp = mediaPlayer
        if (mp != null && _playerState.value.isPaused) {
            try {
                mp.start()
                _playerState.value = _playerState.value.copy(
                    isPlaying = true,
                    isPaused = false
                )
                startProgressTicker()
                onPlaybackStateChanged?.invoke(true, _playerState.value.currentVideo)
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming video", e)
                _playerState.value.currentVideo?.let { playVideo(it) }
            }
        } else if (_playerState.value.currentVideo != null) {
            playVideo(_playerState.value.currentVideo!!)
        }
    }

    fun stop() {
        stopInternal(keepVideo = false)
    }

    private fun stopInternal(keepVideo: Boolean) {
        stopProgressTicker()
        try {
            mediaPlayer?.let { mp ->
                val sessionId = try { mp.audioSessionId } catch (_: Exception) { 0 }
                if (sessionId > 0) {
                    AudioEqualizerManager.unregisterAudioSession(sessionId)
                }
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null

        val video = if (keepVideo) _playerState.value.currentVideo else null
        _playerState.value = _playerState.value.copy(
            isPlaying = false,
            isPaused = false,
            currentVideo = video,
            currentPositionMs = 0L,
            durationMs = if (keepVideo) _playerState.value.durationMs else 0L
        )
        onPlaybackStateChanged?.invoke(false, video)
    }

    fun seekTo(positionMs: Long) {
        try {
            val maxDur = _playerState.value.durationMs.coerceAtLeast(1L)
            val validPos = positionMs.coerceIn(0L, maxDur)
            _playerState.value = _playerState.value.copy(currentPositionMs = validPos)

            mediaPlayer?.let { mp ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mp.seekTo(validPos, MediaPlayer.SEEK_CLOSEST)
                } else {
                    mp.seekTo(validPos.toInt())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking video to $positionMs", e)
        }
    }

    fun seekRelative(offsetMs: Long) {
        val current = _playerState.value.currentPositionMs
        val target = current + offsetMs
        seekTo(target)
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _playerState.value = _playerState.value.copy(volume = clamped)
        applyVolumeInternal()
    }

    fun toggleMute() {
        _playerState.value = _playerState.value.copy(isMuted = !_playerState.value.isMuted)
        applyVolumeInternal()
    }

    fun setMuted(muted: Boolean) {
        _playerState.value = _playerState.value.copy(isMuted = muted)
        applyVolumeInternal()
    }

    private fun applyVolumeInternal() {
        try {
            val vol = if (_playerState.value.isMuted) 0f else _playerState.value.volume
            mediaPlayer?.setVolume(vol, vol)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting video volume", e)
        }
    }

    fun setLooping(loop: Boolean) {
        _playerState.value = _playerState.value.copy(isLooping = loop)
        try {
            mediaPlayer?.isLooping = loop
        } catch (_: Exception) {}
    }

    fun setPlaybackSpeed(speed: Float) {
        val validSpeed = speed.coerceIn(0.25f, 3.0f)
        _playerState.value = _playerState.value.copy(playbackSpeed = validSpeed)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.let { mp ->
                    mp.playbackParams = mp.playbackParams.setSpeed(validSpeed)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting video playback speed", e)
            }
        }
    }

    fun setAspectRatio(ratio: VideoAspectRatio) {
        _playerState.value = _playerState.value.copy(aspectRatio = ratio)
    }

    fun cycleAspectRatio(): VideoAspectRatio {
        val next = when (_playerState.value.aspectRatio) {
            VideoAspectRatio.FIT -> VideoAspectRatio.FILL_CROP
            VideoAspectRatio.FILL_CROP -> VideoAspectRatio.FIT
            else -> VideoAspectRatio.FIT
        }
        _playerState.value = _playerState.value.copy(aspectRatio = next)
        return next
    }

    fun setDisplayLayer(layer: VideoDisplayLayer) {
        _playerState.value = _playerState.value.copy(displayLayer = layer)
    }

    fun toggleDisplayLayer(): VideoDisplayLayer {
        val newLayer = if (_playerState.value.displayLayer == VideoDisplayLayer.BACKGROUND) {
            VideoDisplayLayer.FOREGROUND
        } else {
            VideoDisplayLayer.BACKGROUND
        }
        setDisplayLayer(newLayer)
        return newLayer
    }

    private fun startProgressTicker() {
        stopProgressTicker()
        progressJob = scope.launch {
            while (isActive && mediaPlayer?.isPlaying == true) {
                try {
                    val pos = mediaPlayer?.currentPosition?.toLong() ?: 0L
                    val dur = mediaPlayer?.duration?.toLong()?.let { if (it > 0) it else _playerState.value.durationMs } ?: _playerState.value.durationMs
                    _playerState.value = _playerState.value.copy(
                        currentPositionMs = pos,
                        durationMs = dur.coerceAtLeast(0L)
                    )
                } catch (_: Exception) {}
                delay(200)
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun probeVideoDimensions(filePath: String): Pair<Int, Int> {
        return try {
            val file = File(filePath)
            if (!file.exists()) return Pair(0, 0)
            val retriever = MediaMetadataRetriever()
            FileInputStream(file).use { fis ->
                retriever.setDataSource(fis.fd)
            }
            val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            retriever.release()
            val w = wStr?.toIntOrNull() ?: 0
            val h = hStr?.toIntOrNull() ?: 0
            val rot = rotStr?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) {
                Pair(h, w)
            } else {
                Pair(w, h)
            }
        } catch (_: Exception) {
            Pair(0, 0)
        }
    }

    fun probeDuration(filePath: String): Long {
        return try {
            val file = File(filePath)
            if (!file.exists()) return 0L
            val retriever = MediaMetadataRetriever()
            FileInputStream(file).use { fis ->
                retriever.setDataSource(fis.fd)
            }
            val timeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            timeStr?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
