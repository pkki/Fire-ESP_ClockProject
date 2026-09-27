package com.example.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import com.example.model.BassCutMode
import com.example.model.EqualizerPreset
import com.example.model.EqualizerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized Audio Equalizer and Speaker Bass Protection Manager
 * - Applies 5-band graphic equalizer across all audio players in the application
 * - Specifically addresses low-frequency weakness / rattling / distortion on small device speakers via dedicated Bass Cut Filter
 * - Supports hardware AudioFx Equalizer and BassBoost on Android audio sessions
 */
object AudioEqualizerManager {
    private const val TAG = "AudioEqualizerManager"

    private val _equalizerState = MutableStateFlow(EqualizerState())
    val equalizerState: StateFlow<EqualizerState> = _equalizerState.asStateFlow()

    // Map of active audio session IDs to their hardware Equalizer & BassBoost instances
    private val activeEqualizers = ConcurrentHashMap<Int, Equalizer>()
    private val activeBassBoosts = ConcurrentHashMap<Int, BassBoost>()

    // Callback when state changes to persist to preferences
    var onStateChanged: ((EqualizerState) -> Unit)? = null

    /**
     * Initialize equalizer state from saved preferences
     */
    fun initFromPreferences(
        enabled: Boolean,
        presetName: String,
        bands: List<Int>,
        bassCutName: String
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
            bassCutMode = bassCut
        )
        _equalizerState.value = newState
        applyStateToAllEqualizers(newState)
    }

    /**
     * Register a newly opened audio session ID (from MediaPlayer or AudioTrack)
     */
    fun registerAudioSession(sessionId: Int) {
        if (sessionId <= 0) return
        try {
            // Check if already registered
            if (activeEqualizers.containsKey(sessionId)) return

            val eq = Equalizer(0, sessionId).apply {
                enabled = _equalizerState.value.isEnabled
            }

            // Read supported center frequencies if available
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

            // Try binding BassBoost
            try {
                val bb = BassBoost(0, sessionId)
                bb.enabled = _equalizerState.value.isEnabled && _equalizerState.value.bassCutMode == BassCutMode.OFF
                activeBassBoosts[sessionId] = bb
            } catch (e: Exception) {
                Log.d(TAG, "BassBoost not supported on this session: ${e.message}")
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
     * Adjust Bass Cut Filter mode
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
                bb.enabled = state.isEnabled && state.bassCutMode == BassCutMode.OFF && state.currentPreset == EqualizerPreset.BASS_BOOST
                if (bb.strengthSupported && bb.enabled) {
                    bb.setStrength(600.toShort())
                }
            } catch (_: Exception) {}
        }
    }

    private fun applyStateToEqualizer(eq: Equalizer, state: EqualizerState) {
        try {
            eq.enabled = state.isEnabled
            if (!state.isEnabled) return

            val effectiveGains = state.getEffectiveBandGains()
            val numBands = eq.numberOfBands.toInt()
            val minLevel = eq.bandLevelRange?.get(0)?.toInt() ?: -1500
            val maxLevel = eq.bandLevelRange?.get(1)?.toInt() ?: 1500

            for (i in 0 until minOf(effectiveGains.size, numBands)) {
                val gainDb = effectiveGains[i]
                // Convert dB to millibels (1 dB = 100 mB)
                val targetMilliBels = (gainDb * 100).coerceIn(minLevel, maxLevel).toShort()
                eq.setBandLevel(i.toShort(), targetMilliBels)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying band levels to Equalizer: ${e.message}")
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
    }
}
