package com.example.audio

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
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
 * Centralized Audio Equalizer, Hardware BassBoost, and Virtualizer Sound Effects Manager
 * - Robust Android 7 (Nougat, API 24/25) and modern Android compatibility
 * - Applies 5-band graphic equalizer across all audio and video players in the application
 * - Broadcasts ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION to trigger Android OS audio DSP engine
 * - Direct band mapping (0 to numBands - 1) avoiding HAL getBand frequency aliasing
 * - Hardware BassBoost (0% to 100%) and Virtualizer (3D Surround Sound)
 */
object AudioEqualizerManager {
    private const val TAG = "AudioEqualizerManager"

    private var appContext: Context? = null

    private val _equalizerState = MutableStateFlow(EqualizerState())
    val equalizerState: StateFlow<EqualizerState> = _equalizerState.asStateFlow()

    // Map of active audio session IDs to their hardware Equalizer, BassBoost, and Virtualizer instances
    private val activeEqualizers = ConcurrentHashMap<Int, Equalizer>()
    private val activeBassBoosts = ConcurrentHashMap<Int, BassBoost>()
    private val activeVirtualizers = ConcurrentHashMap<Int, Virtualizer>()

    // Callback when state changes to persist to preferences
    var onStateChanged: ((EqualizerState) -> Unit)? = null

    fun initContext(context: Context) {
        appContext = context.applicationContext
        // Android 7 (Nougat, API 24/25) & legacy Android: Register global session 0 for system-wide DSP pipeline
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
            try {
                registerAudioSessionInternal(0)
            } catch (e: Exception) {
                Log.d(TAG, "Global session 0 init: ${e.message}")
            }
        }
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
        bassCutName: String = "OFF"
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
            bassCutMode = bassCut
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
                } catch (e: Exception) {
                    Log.d(TAG, "AudioEffect broadcast open failed: ${e.message}")
                }
            }

            // If already registered, update settings and return
            activeEqualizers[sessionId]?.let { eq ->
                applyStateToEqualizer(eq, _equalizerState.value)
                return
            }

            // Create Equalizer with priority 1000 (preempts low-priority defaults across Android 7+ HALs)
            val eq = Equalizer(1000, sessionId).apply {
                enabled = _equalizerState.value.isEnabled
            }

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
            } catch (e: Exception) {
                Log.w(TAG, "Could not query center frequencies: ${e.message}")
            }

            activeEqualizers[sessionId] = eq
            applyStateToEqualizer(eq, _equalizerState.value)

            // Setup hardware BassBoost (priority 1000)
            try {
                val bb = BassBoost(1000, sessionId)
                val curState = _equalizerState.value
                val bbEnabled = curState.isEnabled && curState.bassBoostStrength > 0
                bb.enabled = bbEnabled
                if (bb.strengthSupported && bbEnabled) {
                    bb.setStrength(curState.bassBoostStrength.toShort())
                }
                activeBassBoosts[sessionId] = bb
            } catch (e: Exception) {
                Log.d(TAG, "BassBoost not supported on session $sessionId: ${e.message}")
            }

            // Setup hardware Virtualizer (priority 1000)
            try {
                val virt = Virtualizer(1000, sessionId)
                val curState = _equalizerState.value
                val virtEnabled = curState.isEnabled && curState.virtualizerStrength > 0
                virt.enabled = virtEnabled
                if (virt.strengthSupported && virtEnabled) {
                    virt.setStrength(curState.virtualizerStrength.toShort())
                }
                activeVirtualizers[sessionId] = virt
            } catch (e: Exception) {
                Log.d(TAG, "Virtualizer not supported on session $sessionId: ${e.message}")
            }

            Log.d(TAG, "Audio session $sessionId registered to Equalizer (active count: ${activeEqualizers.size})")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create Equalizer for session $sessionId: ${e.message}")
        }
    }

    /**
     * Unregister and release an audio session when playback ends
     */
    fun unregisterAudioSession(sessionId: Int) {
        if (sessionId <= 0) return
        try {
            appContext?.let { ctx ->
                try {
                    val intent = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, ctx.packageName)
                    }
                    ctx.sendBroadcast(intent)
                } catch (_: Exception) {}
            }

            activeEqualizers.remove(sessionId)?.let { eq ->
                try {
                    eq.enabled = false
                    eq.release()
                } catch (_: Exception) {}
            }
            activeBassBoosts.remove(sessionId)?.let { bb ->
                try {
                    bb.enabled = false
                    bb.release()
                } catch (_: Exception) {}
            }
            activeVirtualizers.remove(sessionId)?.let { virt ->
                try {
                    virt.enabled = false
                    virt.release()
                } catch (_: Exception) {}
            }
            Log.d(TAG, "Audio session $sessionId unregistered from Equalizer")
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing Equalizer for session $sessionId: ${e.message}")
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
     * Reset Equalizer to Flat
     */
    fun resetToFlat() {
        setPreset(EqualizerPreset.FLAT)
    }

    private fun applyStateToAllEqualizers(state: EqualizerState) {
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
            } catch (_: Exception) {}
        }
        activeVirtualizers.values.forEach { virt ->
            try {
                val shouldEnable = state.isEnabled && state.virtualizerStrength > 0
                virt.enabled = shouldEnable
                if (virt.strengthSupported && shouldEnable) {
                    virt.setStrength(state.virtualizerStrength.toShort())
                }
            } catch (_: Exception) {}
        }
    }

    private fun applyStateToEqualizer(eq: Equalizer, state: EqualizerState) {
        try {
            val numBands = try { eq.numberOfBands.toInt() } catch (_: Exception) { 0 }
            if (numBands <= 0) return

            val minLevel = try { eq.bandLevelRange?.get(0)?.toInt() ?: -1500 } catch (_: Exception) { -1500 }
            val maxLevel = try { eq.bandLevelRange?.get(1)?.toInt() ?: 1500 } catch (_: Exception) { 1500 }

            val effectiveGains = state.getEffectiveBandGains()

            // Ensure equalizer effect is enabled
            eq.enabled = state.isEnabled

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
                } catch (e: Exception) {
                    Log.w(TAG, "Error setting band $i to $targetMilliBels: ${e.message}")
                }
            }

            // Re-confirm enabled state
            eq.enabled = state.isEnabled
        } catch (e: Exception) {
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
        activeEqualizers.forEach { (sessionId, eq) ->
            try {
                eq.enabled = false
                eq.release()
            } catch (_: Exception) {}
        }
        activeEqualizers.clear()

        activeBassBoosts.forEach { (_, bb) ->
            try {
                bb.enabled = false
                bb.release()
            } catch (_: Exception) {}
        }
        activeBassBoosts.clear()

        activeVirtualizers.forEach { (_, virt) ->
            try {
                virt.enabled = false
                virt.release()
            } catch (_: Exception) {}
        }
        activeVirtualizers.clear()
    }
}
