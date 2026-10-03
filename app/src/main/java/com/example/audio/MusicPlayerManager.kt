package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import com.example.model.CustomAudioItem
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
import kotlin.random.Random

enum class MusicRepeatMode(val label: String, val iconDescription: String) {
    ALL("全曲リピート", "プレイリスト全体を繰り返し再生"),
    ONE("1曲リピート", "現在の曲を繰り返し再生"),
    OFF("リピートなし", "最後の曲で再生停止"),
    SHUFFLE("シャッフル", "曲順をランダムに再生")
}

data class MusicPlayerState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentTrack: CustomAudioItem? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 0.85f,
    val repeatMode: MusicRepeatMode = MusicRepeatMode.ALL,
    val playbackSpeed: Float = 1.0f,
    val playlist: List<CustomAudioItem> = emptyList(),
    val currentIndex: Int = -1
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
 * 本格的かつ高信頼な音楽プレイヤー管理マネージャー (MusicPlayerManager)
 * - 楽曲の再生・一時停止・再開・停止・曲送り・曲戻し
 * - 精密シークバー操作 (seekTo, +/-10秒スキップ)
 * - 音量個別調整 (0.0f〜1.0f)
 * - 再生速度調整 (0.5x〜2.0x)
 * - リピートモード (全曲リピート・1曲リピート・リピートOFF・シャッフル)
 * - 200ms 高精度リアルタイム進捗通知
 */
object MusicPlayerManager {
    private const val TAG = "MusicPlayerManager"

    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    private val _playerState = MutableStateFlow(MusicPlayerState())
    val playerState: StateFlow<MusicPlayerState> = _playerState.asStateFlow()

    // 外部通知用コールバック（メイン画面HUDやステータス同期用）
    var onTrackChanged: ((track: CustomAudioItem?) -> Unit)? = null
    var onPlaybackStateChanged: ((isPlaying: Boolean, track: CustomAudioItem?) -> Unit)? = null

    fun playTrack(
        track: CustomAudioItem,
        playlist: List<CustomAudioItem> = emptyList(),
        volume: Float = _playerState.value.volume,
        repeatMode: MusicRepeatMode = _playerState.value.repeatMode
    ) {
        scope.launch(Dispatchers.Main) {
            val activePlaylist = if (playlist.isNotEmpty()) playlist else listOf(track)
            val idx = activePlaylist.indexOfFirst { it.id == track.id }.let { if (it >= 0) it else 0 }

            startPlaybackInternal(
                track = track,
                playlist = activePlaylist,
                index = idx,
                volume = volume,
                repeatMode = repeatMode
            )
        }
    }

    fun playAtIndex(index: Int) {
        scope.launch(Dispatchers.Main) {
            val list = _playerState.value.playlist
            if (list.isEmpty()) return@launch
            val validIndex = index.coerceIn(0, list.lastIndex)
            val track = list[validIndex]
            startPlaybackInternal(
                track = track,
                playlist = list,
                index = validIndex,
                volume = _playerState.value.volume,
                repeatMode = _playerState.value.repeatMode
            )
        }
    }

    private fun startPlaybackInternal(
        track: CustomAudioItem,
        playlist: List<CustomAudioItem>,
        index: Int,
        volume: Float,
        repeatMode: MusicRepeatMode
    ) {
        // Prevent simultaneous playback / audio doubling with Video player
        VideoPlayerManager.stop()
        stopInternal(keepTrack = true)

        val file = File(track.filePath)
        if (!file.exists()) {
            Log.e(TAG, "Audio file does not exist: ${track.filePath}")
            _playerState.value = _playerState.value.copy(
                isPlaying = false,
                isPaused = false,
                currentTrack = null,
                currentPositionMs = 0L,
                durationMs = 0L
            )
            return
        }

        val fallbackDur = probeDuration(track.filePath)

        // Immediately update player state so the device HUD, Dialog, and Web Dashboard
        // broadcast reflect the playing state and current track instantly
        _playerState.value = _playerState.value.copy(
            isPlaying = true,
            isPaused = false,
            currentTrack = track,
            currentPositionMs = 0L,
            durationMs = fallbackDur,
            volume = volume,
            repeatMode = repeatMode,
            playlist = playlist,
            currentIndex = index
        )
        onTrackChanged?.invoke(track)
        onPlaybackStateChanged?.invoke(true, track)

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                FileInputStream(file).use { fis ->
                    setDataSource(fis.fd)
                }
                setVolume(volume, volume)

                val initialSessionId = audioSessionId
                if (initialSessionId > 0) {
                    AudioEqualizerManager.registerAudioSession(initialSessionId)
                }

                setOnPreparedListener { mp ->
                    try {
                        val duration = mp.duration.toLong().let { if (it > 0) it else fallbackDur }

                        // Start playback first so AudioFlinger track is established on Android 7
                        mp.start()

                        val sessionId = mp.audioSessionId
                        if (sessionId > 0) {
                            AudioEqualizerManager.registerAudioSession(sessionId)
                            AudioEqualizerManager.reapplyToSession(sessionId)
                        }

                        // Apply playback speed
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && _playerState.value.playbackSpeed != 1.0f) {
                            try {
                                mp.playbackParams = mp.playbackParams.setSpeed(_playerState.value.playbackSpeed)
                            } catch (_: Exception) {}
                        }

                        _playerState.value = _playerState.value.copy(
                            isPlaying = true,
                            isPaused = false,
                            currentTrack = track,
                            currentPositionMs = 0L,
                            durationMs = duration.coerceAtLeast(0L),
                            volume = volume,
                            repeatMode = repeatMode,
                            playlist = playlist,
                            currentIndex = index
                        )
                        startProgressTicker()
                        onTrackChanged?.invoke(track)
                        onPlaybackStateChanged?.invoke(true, track)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onPreparedListener", e)
                    }
                }

                setOnCompletionListener {
                    handleTrackCompletion()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    stopInternal(keepTrack = false)
                    true
                }

                setOnSeekCompleteListener { player ->
                    _playerState.value = _playerState.value.copy(
                        currentPositionMs = player.currentPosition.toLong().coerceAtLeast(0L)
                    )
                }

                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception initializing MediaPlayer for track ${track.name}", e)
            stopInternal(keepTrack = false)
        }
    }

    fun togglePlayPause() {
        scope.launch(Dispatchers.Main) {
            val mp = mediaPlayer
            if (mp != null) {
                if (mp.isPlaying) {
                    pause()
                } else {
                    resume()
                }
            } else {
                resume()
            }
        }
    }

    fun pause() {
        scope.launch(Dispatchers.Main) {
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
                    onPlaybackStateChanged?.invoke(false, _playerState.value.currentTrack)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing playback", e)
            }
        }
    }

    fun resume() {
        scope.launch(Dispatchers.Main) {
            VideoPlayerManager.stop()
            val mp = mediaPlayer
            if (mp != null && _playerState.value.isPaused) {
                try {
                    mp.start()
                    _playerState.value = _playerState.value.copy(
                        isPlaying = true,
                        isPaused = false
                    )
                    startProgressTicker()
                    onPlaybackStateChanged?.invoke(true, _playerState.value.currentTrack)
                } catch (e: Exception) {
                    Log.e(TAG, "Error resuming playback", e)
                    _playerState.value.currentTrack?.let { track ->
                        playTrack(track, _playerState.value.playlist)
                    }
                }
            } else if (_playerState.value.currentTrack != null) {
                _playerState.value.currentTrack?.let { track ->
                    playTrack(track, _playerState.value.playlist)
                }
            } else if (_playerState.value.playlist.isNotEmpty()) {
                playAtIndex(0)
            }
        }
    }

    fun stop() {
        scope.launch(Dispatchers.Main) {
            stopInternal(keepTrack = false)
        }
    }

    private fun stopInternal(keepTrack: Boolean) {
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

        val track = if (keepTrack) _playerState.value.currentTrack else null
        _playerState.value = _playerState.value.copy(
            isPlaying = false,
            isPaused = false,
            currentTrack = track,
            currentPositionMs = 0L,
            durationMs = if (keepTrack) _playerState.value.durationMs else 0L
        )
        onPlaybackStateChanged?.invoke(false, track)
        if (!keepTrack) {
            onTrackChanged?.invoke(null)
        }
    }

    fun next() {
        val list = _playerState.value.playlist
        if (list.isEmpty()) return

        val nextIndex = when (_playerState.value.repeatMode) {
            MusicRepeatMode.SHUFFLE -> {
                if (list.size > 1) {
                    var rand = Random.nextInt(list.size)
                    if (rand == _playerState.value.currentIndex) {
                        rand = (rand + 1) % list.size
                    }
                    rand
                } else 0
            }
            MusicRepeatMode.ONE -> _playerState.value.currentIndex
            MusicRepeatMode.ALL -> (_playerState.value.currentIndex + 1) % list.size
            MusicRepeatMode.OFF -> {
                val candidate = _playerState.value.currentIndex + 1
                if (candidate < list.size) candidate else -1
            }
        }

        if (nextIndex in list.indices) {
            playAtIndex(nextIndex)
        } else {
            stop()
        }
    }

    fun previous() {
        val list = _playerState.value.playlist
        if (list.isEmpty()) return

        // 3秒以上再生していたら曲の先頭に戻す
        if (_playerState.value.currentPositionMs > 3000L) {
            seekTo(0L)
            return
        }

        val prevIndex = when (_playerState.value.repeatMode) {
            MusicRepeatMode.SHUFFLE -> Random.nextInt(list.size)
            MusicRepeatMode.ONE -> _playerState.value.currentIndex
            MusicRepeatMode.ALL -> {
                if (_playerState.value.currentIndex - 1 < 0) list.lastIndex else _playerState.value.currentIndex - 1
            }
            MusicRepeatMode.OFF -> {
                val candidate = _playerState.value.currentIndex - 1
                if (candidate >= 0) candidate else 0
            }
        }

        if (prevIndex in list.indices) {
            playAtIndex(prevIndex)
        } else {
            seekTo(0L)
        }
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
            Log.e(TAG, "Error seeking to position $positionMs", e)
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
        try {
            mediaPlayer?.setVolume(clamped, clamped)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume $clamped", e)
        }
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
                Log.e(TAG, "Error setting playback speed", e)
            }
        }
    }

    fun setRepeatMode(mode: MusicRepeatMode) {
        _playerState.value = _playerState.value.copy(repeatMode = mode)
    }

    fun cycleRepeatMode(): MusicRepeatMode {
        val nextMode = when (_playerState.value.repeatMode) {
            MusicRepeatMode.ALL -> MusicRepeatMode.ONE
            MusicRepeatMode.ONE -> MusicRepeatMode.SHUFFLE
            MusicRepeatMode.SHUFFLE -> MusicRepeatMode.OFF
            MusicRepeatMode.OFF -> MusicRepeatMode.ALL
        }
        setRepeatMode(nextMode)
        return nextMode
    }

    private fun handleTrackCompletion() {
        when (_playerState.value.repeatMode) {
            MusicRepeatMode.ONE -> {
                seekTo(0L)
                mediaPlayer?.start()
                _playerState.value = _playerState.value.copy(isPlaying = true, isPaused = false, currentPositionMs = 0L)
                startProgressTicker()
            }
            MusicRepeatMode.ALL, MusicRepeatMode.SHUFFLE -> {
                next()
            }
            MusicRepeatMode.OFF -> {
                val list = _playerState.value.playlist
                val nextIdx = _playerState.value.currentIndex + 1
                if (nextIdx < list.size) {
                    playAtIndex(nextIdx)
                } else {
                    stop()
                }
            }
        }
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
