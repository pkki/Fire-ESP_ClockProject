package com.example.audio

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import com.example.model.BassCutMode
import com.example.model.EqualizerPreset
import com.example.model.EqualizerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized Audio Equalizer, Hardware BassBoost, Virtualizer & LoudnessEnhancer Sound Effects Manager
 * - Robust Android 7 (Nougat, API 24/25) and modern Android compatibility
 * - Applies 5-band graphic equalizer across all audio and video players in the application
 * - Broadcasts ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION to trigger Android OS audio DSP engine
 * - Direct band mapping (0 to numBands - 1) avoiding HAL getBand frequency aliasing
 * - Hardware BassBoost (0% to 100%) and Virtualizer (3D Surround Sound)
 * - Automatic Volume Normalization & Loudness Maximizer (Hardware LoudnessEnhancer)
 */
object AudioEqualizerManager {
    private const val TAG = "AudioEqualizerManager"

    private var appContext: Context? = null

    private val _equalizerState = MutableStateFlow(EqualizerState())
    val equalizerState: StateFlow<EqualizerState> = _equalizerState.asStateFlow()

    private val effectLock = Any()

    // Map of active audio session IDs to their hardware Equalizer, BassBoost, Virtualizer, and LoudnessEnhancer instances
    private val activeEqualizers = ConcurrentHashMap<Int, Equalizer>()
    private val activeBassBoosts = ConcurrentHashMap<Int, BassBoost>()
    private val activeVirtualizers = ConcurrentHashMap<Int, Virtualizer>()
    private val activeLoudnessEnhancers = ConcurrentHashMap<Int, LoudnessEnhancer>()

    // Track-specific loudness boost overrides
    private val sessionLoudnessOverrides = ConcurrentHashMap<Int, Int>()

    // Callback when state changes to persist to preferences
    var onStateChanged: ((EqualizerState) -> Unit)? = null

    fun initContext(context: Context) {
        appContext = context.applicationContext
        AudioVolumeNormalizer.init(context)
        // グローバル(session 0)EQは常時音声パイプラインに居座るため、ここでは作らない。
        // EQが有効かつFLATでない時だけ syncGlobalSession0() が作成/解放する。
    }

    /**
     * Initialize equalizer state from saved preferences
     */
    fun initFromPreferences(
        enabled: Boolean,
        presetName: String,
        bands: List<Int>,
        bassBoostStrength: Int = 0,
        virtualizerStrength: Int = 0,
        bassCutName: String = "OFF",
        autoVolumeNormalization: Boolean = true,
        loudnessBoostGainMb: Int = 400
    ) {
        val preset = try {
            EqualizerPreset.valueOf(presetName)
        } catch (_: Exception) {
            EqualizerPreset.FLAT
        }

        val bassCut = try {
            BassCutMode.valueOf(bassCutName)
        } catch (_: Exception) {
            preset.bassCutMode
        }

        val actualBands = if (bands.size == 5) bands else preset.bandGainsDb

        val newState = EqualizerState(
            isEnabled = enabled,
            currentPreset = preset,
            bandGainsDb = actualBands,
            bassBoostStrength = bassBoostStrength.coerceIn(0, 1000),
            virtualizerStrength = virtualizerStrength.coerceIn(0, 1000),
            bassCutMode = bassCut,
            autoVolumeNormalization = autoVolumeNormalization,
            loudnessBoostGainMb = loudnessBoostGainMb.coerceIn(0, 1200)
        )
        _equalizerState.value = newState
        applyStateToAllEqualizers(newState)
    }

    /**
     * Register a newly opened audio session ID (from MediaPlayer or AudioTrack)
     */
    fun registerAudioSession(sessionId: Int) {
        if (sessionId < 0) return
        registerAudioSessionInternal(sessionId)
    }

    private fun registerAudioSessionInternal(sessionId: Int) {
        synchronized(effectLock) {
            try {
                // Notify system audio effect control engine (Crucial on Android 7)
                appContext?.let { ctx ->
                    try {
                        val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, ctx.packageName)
                            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                        }
                        ctx.sendBroadcast(intent)
                    } catch (e: Throwable) {
                        Log.d(TAG, "AudioEffect broadcast open failed: ${e.message}")
                    }
                }

                // If already registered, update settings and return
                activeEqualizers[sessionId]?.let { eq ->
                    applyStateToEqualizer(eq, _equalizerState.value)
                    reapplyToSessionLocked(sessionId)
                    return
                }

                // Create Equalizer with standard application priority 0 (fallback to 1000 if 0 fails)
                val eq = try {
                    Equalizer(0, sessionId).apply {
                        enabled = _equalizerState.value.isEnabled
                    }
                } catch (e0: Throwable) {
                    try {
                        Equalizer(1000, sessionId).apply {
                            enabled = _equalizerState.value.isEnabled
                        }
                    } catch (e1: Throwable) {
                        Log.w(TAG, "Equalizer creation failed for session $sessionId: ${e1.message}")
                        null
                    }
                }

                if (eq != null) {
                    // Read supported center frequencies
                    try {
                        val numBands = eq.numberOfBands.toInt()
                        if (numBands > 0) {
                            val freqs = mutableListOf<Int>()
                            for (i in 0 until minOf(5, numBands)) {
                                val centerFreqMilliHz = eq.getCenterFreq(i.toShort())
                                freqs.add(centerFreqMilliHz / 1000)
                            }
                            if (freqs.isNotEmpty()) {
                                _equalizerState.value = _equalizerState.value.copy(
                                    centerFrequenciesHz = freqs,
                                    isSupportedOnDevice = true
                                )
                            }
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "Could not query center frequencies: ${e.message}")
                    }

                    activeEqualizers[sessionId] = eq
                    applyStateToEqualizer(eq, _equalizerState.value)
                }

                // BassBoost & Virtualizer are only created on active positive session IDs (sessionId > 0)
                // Attaching BassBoost/Virtualizer to global session 0 is unsafe on older Android HALs
                if (sessionId > 0) {
                    // Setup hardware BassBoost (priority 0, fallback 1000)
                    try {
                        val bb = try {
                            BassBoost(0, sessionId)
                        } catch (_: Throwable) {
                            BassBoost(1000, sessionId)
                        }
                        val curState = _equalizerState.value
                        val bbEnabled = curState.isEnabled && curState.bassBoostStrength > 0
                        bb.enabled = bbEnabled
                        if (bb.strengthSupported && bbEnabled) {
                            bb.setStrength(curState.bassBoostStrength.toShort())
                        }
                        activeBassBoosts[sessionId] = bb
                    } catch (e: Throwable) {
                        Log.d(TAG, "BassBoost not supported on session $sessionId: ${e.message}")
                    }

                    // Setup hardware Virtualizer (priority 0, fallback 1000)
                    try {
                        val virt = try {
                            Virtualizer(0, sessionId)
                        } catch (_: Throwable) {
                            Virtualizer(1000, sessionId)
                        }
                        val curState = _equalizerState.value
                        val virtEnabled = curState.isEnabled && curState.virtualizerStrength > 0
                        virt.enabled = virtEnabled
                        if (virt.strengthSupported && virtEnabled) {
                            virt.setStrength(curState.virtualizerStrength.toShort())
                        }
                        activeVirtualizers[sessionId] = virt
                    } catch (e: Throwable) {
                        Log.d(TAG, "Virtualizer not supported on session $sessionId: ${e.message}")
                    }

                    // Setup hardware LoudnessEnhancer for dynamic auto-volume normalization & maximum level boost
                    try {
                        val le = LoudnessEnhancer(sessionId)
                        val curState = _equalizerState.value
                        val leEnabled = curState.isEnabled && curState.autoVolumeNormalization
                        le.enabled = leEnabled
                        val targetGainMb = sessionLoudnessOverrides[sessionId] ?: curState.loudnessBoostGainMb
                        if (leEnabled) {
                            le.setTargetGain(targetGainMb)
                        }
                        activeLoudnessEnhancers[sessionId] = le
                    } catch (e: Throwable) {
                        Log.d(TAG, "LoudnessEnhancer not supported on session $sessionId: ${e.message}")
                    }
                }

                Log.i(TAG, "Audio session $sessionId registered to Equalizer & LoudnessEnhancer (active count: ${activeEqualizers.size})")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to register session $sessionId: ${e.message}")
            }
        }
    }

    /**
     * Set track-specific loudness boost override (e.g. from AudioVolumeNormalizer peak analysis)
     */
    fun applyTrackLoudnessBoost(sessionId: Int, boostMb: Int) {
        if (sessionId <= 0) return
        sessionLoudnessOverrides[sessionId] = boostMb
        synchronized(effectLock) {
            activeLoudnessEnhancers[sessionId]?.let { le ->
                try {
                    val curState = _equalizerState.value
                    val enabled = curState.isEnabled && curState.autoVolumeNormalization
                    le.enabled = enabled
                    if (enabled) {
                        le.setTargetGain(boostMb)
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Unregister and release an audio session when playback ends
     */
    fun unregisterAudioSession(sessionId: Int) {
        if (sessionId <= 0) return
        sessionLoudnessOverrides.remove(sessionId)
        synchronized(effectLock) {
            try {
                appContext?.let { ctx ->
                    try {
                        val intent = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, ctx.packageName)
                        }
                        ctx.sendBroadcast(intent)
                    } catch (_: Throwable) {}
                }

                activeEqualizers.remove(sessionId)?.let { eq ->
                    try {
                        eq.enabled = false
                        eq.release()
                    } catch (_: Throwable) {}
                }
                activeBassBoosts.remove(sessionId)?.let { bb ->
                    try {
                        bb.enabled = false
                        bb.release()
                    } catch (_: Throwable) {}
                }
                activeVirtualizers.remove(sessionId)?.let { virt ->
                    try {
                        virt.enabled = false
                        virt.release()
                    } catch (_: Throwable) {}
                }
                activeLoudnessEnhancers.remove(sessionId)?.let { le ->
                    try {
                        le.enabled = false
                        le.release()
                    } catch (_: Throwable) {}
                }
                Log.d(TAG, "Audio session $sessionId unregistered from Equalizer")
            } catch (e: Throwable) {
                Log.w(TAG, "Error releasing Equalizer for session $sessionId: ${e.message}")
            }
        }
    }

    /**
     * Enable or disable the entire Equalizer
     */
    fun setEqualizerEnabled(enabled: Boolean) {
        val updated = _equalizerState.value.copy(isEnabled = enabled)
        _equalizerState.value = updated
        applyStateToAllEqualizers(updated)
        onStateChanged?.invoke(updated)
    }

    /**
     * Select a preset
     */
    fun setPreset(preset: EqualizerPreset) {
        val updated = if (preset == EqualizerPreset.CUSTOM) {
            _equalizerState.value.copy(currentPreset = EqualizerPreset.CUSTOM)
        } else {
            _equalizerState.value.copy(
                currentPreset = preset,
                bandGainsDb = preset.bandGainsDb,
                bassBoostStrength = preset.bassBoostStrength,
                virtualizerStrength = preset.virtualizerStrength,
                bassCutMode = preset.bassCutMode
            )
        }
        _equalizerState.value = updated
        applyStateToAllEqualizers(updated)
        onStateChanged?.invoke(updated)
    }

    /**
     * Adjust a single band's gain in dB (-15dB to +15dB)
     */
    fun setBandGain(bandIndex: Int, gainDb: Int) {
        val clamped = gainDb.coerceIn(-15, 15)
        val currentBands = _equalizerState.value.bandGainsDb.toMutableList()
        while (currentBands.size < 5) currentBands.add(0)
        if (bandIndex in 0..4) {
            currentBands[bandIndex] = clamped
        }
        val updated = _equalizerState.value.copy(
            bandGainsDb = currentBands,
            currentPreset = EqualizerPreset.CUSTOM
        )
        _equalizerState.value = updated
        applyStateToAllEqualizers(updated)
        onStateChanged?.invoke(updated)
    }

    /**
     * Set Bass Boost slider strength (0 to 1000)
     */
    fun setBassBoostStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        val updated = _equalizerState.value.copy(
            bassBoostStrength = clamped,
            currentPreset = EqualizerPreset.CUSTOM
        )
        _equalizerState.value = updated
        applyStateToAllEqualizers(updated)
        onStateChanged?.invoke(updated)
    }

    /**
     * Set Virtualizer / 3D Surround sound slider strength (0 to 1000)
     */
    fun setVirtualizerStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        val updated = _equalizerState.value.copy(
            virtualizerStrength = clamped,
            currentPreset = EqualizerPreset.CUSTOM
        )
        _equalizerState.value = updated
        applyStateToAllEqualizers(updated)
        onStateChanged?.invoke(updated)
    }

    /**
     * Adjust Bass Cut Filter mode (optional protection)
     */
    fun setBassCutMode(mode: BassCutMode) {
        val updated = _equalizerState.value.copy(bassCutMode = mode)
        _equalizerState.value = updated
        applyStateToAllEqualizers(updated)
        onStateChanged?.invoke(updated)
    }

    /**
     * Enable or disable automatic volume normalization (loudness maximizer)
     */
    fun setAutoVolumeNormalization(enabled: Boolean) {
        val updated = _equalizerState.value.copy(autoVolumeNormalization = enabled)
        _equalizerState.value = updated
        applyStateToAllEqualizers(updated)
        onStateChanged?.invoke(updated)
    }

    /**
     * Set hardware LoudnessEnhancer target boost gain (0 .. 1200 mB)
     */
    fun setLoudnessBoostGainMb(gainMb: Int) {
        val clamped = gainMb.coerceIn(0, 1200)
        val updated = _equalizerState.value.copy(loudnessBoostGainMb = clamped)
        _equalizerState.value = updated
        applyStateToAllEqualizers(updated)
        onStateChanged?.invoke(updated)
    }

    /**
     * Reset Equalizer to Flat
     */
    fun resetToFlat() {
        setPreset(EqualizerPreset.FLAT)
    }

    /** Android 7以下: 実際に補正が必要な間だけグローバルEQ(session 0)を保持する */
    private fun syncGlobalSession0(state: EqualizerState) {
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.P) return
        val needed = state.isEnabled && state.getEffectiveBandGains().any { it != 0 }
        try {
            if (needed) {
                if (!activeEqualizers.containsKey(0)) registerAudioSessionInternal(0)
            } else {
                activeEqualizers.remove(0)?.let { eq ->
                    try { eq.enabled = false; eq.release() } catch (_: Throwable) {}
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Global session 0 sync failed: ${e.message}")
        }
    }

    private fun applyStateToAllEqualizers(state: EqualizerState) {
        synchronized(effectLock) {
            syncGlobalSession0(state)
            activeEqualizers.values.forEach { eq ->
                applyStateToEqualizer(eq, state)
            }
            activeBassBoosts.values.forEach { bb ->
                try {
                    val shouldEnable = state.isEnabled && state.bassBoostStrength > 0
                    bb.enabled = shouldEnable
                    if (bb.strengthSupported && shouldEnable) {
                        bb.setStrength(state.bassBoostStrength.toShort())
                    }
                } catch (_: Throwable) {}
            }
            activeVirtualizers.values.forEach { virt ->
                try {
                    val shouldEnable = state.isEnabled && state.virtualizerStrength > 0
                    virt.enabled = shouldEnable
                    if (virt.strengthSupported && shouldEnable) {
                        virt.setStrength(state.virtualizerStrength.toShort())
                    }
                } catch (_: Throwable) {}
            }
            activeLoudnessEnhancers.forEach { (sessionId, le) ->
                try {
                    val shouldEnable = state.isEnabled && state.autoVolumeNormalization
                    le.enabled = shouldEnable
                    if (shouldEnable) {
                        val gainMb = sessionLoudnessOverrides[sessionId] ?: state.loudnessBoostGainMb
                        le.setTargetGain(gainMb)
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    fun reapplyToSession(sessionId: Int) {
        synchronized(effectLock) {
            reapplyToSessionLocked(sessionId)
        }
    }

    private fun reapplyToSessionLocked(sessionId: Int) {
        activeEqualizers[sessionId]?.let { eq ->
            applyStateToEqualizer(eq, _equalizerState.value)
        }
        activeBassBoosts[sessionId]?.let { bb ->
            try {
                val state = _equalizerState.value
                val bbEnabled = state.isEnabled && state.bassBoostStrength > 0
                bb.enabled = false
                if (bb.strengthSupported && bbEnabled) {
                    bb.setStrength(state.bassBoostStrength.toShort())
                }
                bb.enabled = bbEnabled
            } catch (_: Throwable) {}
        }
        activeVirtualizers[sessionId]?.let { virt ->
            try {
                val state = _equalizerState.value
                val virtEnabled = state.isEnabled && state.virtualizerStrength > 0
                virt.enabled = false
                if (virt.strengthSupported && virtEnabled) {
                    virt.setStrength(state.virtualizerStrength.toShort())
                }
                virt.enabled = virtEnabled
            } catch (_: Throwable) {}
        }
        activeLoudnessEnhancers[sessionId]?.let { le ->
            try {
                val state = _equalizerState.value
                val leEnabled = state.isEnabled && state.autoVolumeNormalization
                le.enabled = leEnabled
                if (leEnabled) {
                    val gainMb = sessionLoudnessOverrides[sessionId] ?: state.loudnessBoostGainMb
                    le.setTargetGain(gainMb)
                }
            } catch (_: Throwable) {}
        }
    }

    private fun applyStateToEqualizer(eq: Equalizer, state: EqualizerState) {
        try {
            val numBands = try { eq.numberOfBands.toInt() } catch (_: Throwable) { 0 }
            if (numBands <= 0) return

            val minLevel = try { eq.bandLevelRange?.get(0)?.toInt() ?: -1500 } catch (_: Throwable) { -1500 }
            val maxLevel = try { eq.bandLevelRange?.get(1)?.toInt() ?: 1500 } catch (_: Throwable) { 1500 }

            val effectiveGains = state.getEffectiveBandGains()

            // Direct 1:1 band index mapping to guarantee every hardware band receives its exact level
            for (i in 0 until numBands) {
                val gainDb = if (numBands == 5) {
                    effectiveGains.getOrElse(i) { 0 }
                } else {
                    val mappedIdx = ((i.toFloat() / (numBands - 1).coerceAtLeast(1)) * 4f).toInt().coerceIn(0, 4)
                    effectiveGains.getOrElse(mappedIdx) { 0 }
                }

                // 1 dB = 100 milliBels
                val targetMilliBels = (gainDb * 100).coerceIn(minLevel, maxLevel).toShort()

                try {
                    eq.setBandLevel(i.toShort(), targetMilliBels)
                } catch (e: Throwable) {
                    Log.w(TAG, "Error setting band $i to $targetMilliBels: ${e.message}")
                }
            }

            // Confirm enabled state on hardware effect
            eq.enabled = state.isEnabled
            val hasControl = try { eq.hasControl() } catch (_: Throwable) { true }
            Log.d(TAG, "Equalizer applied: enabled=${state.isEnabled}, hasControl=$hasControl, bands=$numBands")
        } catch (e: Throwable) {
            Log.w(TAG, "Error applying state to Equalizer: ${e.message}")
        }
    }

    fun getBandFrequencyLabel(bandIndex: Int): String {
        val freqs = _equalizerState.value.centerFrequenciesHz
        val hz = freqs.getOrElse(bandIndex) {
            when (bandIndex) {
                0 -> 60
                1 -> 230
                2 -> 910
                3 -> 3600
                4 -> 14000
                else -> 1000
            }
        }
        return if (hz >= 1000) {
            val k = hz / 1000.0
            if (k % 1.0 == 0.0) "${k.toInt()} kHz" else String.format("%.1f kHz", k)
        } else {
            "$hz Hz"
        }
    }

    fun releaseAll() {
        synchronized(effectLock) {
            activeEqualizers.forEach { (sessionId, eq) ->
                try {
                    eq.enabled = false
                    eq.release()
                } catch (_: Throwable) {}
            }
            activeEqualizers.clear()

            activeBassBoosts.forEach { (_, bb) ->
                try {
                    bb.enabled = false
                    bb.release()
                } catch (_: Throwable) {}
            }
            activeBassBoosts.clear()

            activeVirtualizers.forEach { (_, virt) ->
                try {
                    virt.enabled = false
                    virt.release()
                } catch (_: Throwable) {}
            }
            activeVirtualizers.clear()

            activeLoudnessEnhancers.forEach { (_, le) ->
                try {
                    le.enabled = false
                    le.release()
                } catch (_: Throwable) {}
            }
            activeLoudnessEnhancers.clear()
            sessionLoudnessOverrides.clear()
        }
    }
}
