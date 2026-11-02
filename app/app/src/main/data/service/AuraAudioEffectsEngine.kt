package com.luxury.auraplayer.service

import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class EqBandState(
    val index: Int,
    val frequencyHz: Int,
    val label: String,
    val gainDb: Float // يتراوح بين -12dB و +12dB
)

data class EqPreset(
    val nameAr: String,
    val gainsDb: List<Float>
)

data class AudioDspState(
    val isEnabled: Boolean = true,
    val bands: List<EqBandState> = AuraAudioEffectsEngine.DEFAULT_10_BANDS,
    val bassBoostStrength: Float = 0.25f, // 0.0f .. 1.0f
    val virtualizerStrength: Float = 0.30f, // 0.0f .. 1.0f
    val activePresetName: String = "الوضع الملكي المتوازن",
    val crossfadeDurationSec: Int = 3
)

object AuraAudioEffectsEngine {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val FREQUENCIES_10_BAND = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    val DEFAULT_10_BANDS: List<EqBandState> = FREQUENCIES_10_BAND.mapIndexed { idx, freq ->
        val label = if (freq >= 1000) "${freq / 1000}kHz" else "${freq}Hz"
        EqBandState(index = idx, frequencyHz = freq, label = label, gainDb = 0f)
    }

    val LUXURY_PRESETS: List<EqPreset> = listOf(
        EqPreset("الوضع الملكي المتوازن", listOf(1.5f, 2.0f, 1.0f, 0.0f, -0.5f, 0.5f, 1.5f, 2.5f, 3.0f, 2.5f)),
        EqPreset("تضخيم الباص الفائق (Royal Bass)", listOf(6.5f, 5.5f, 4.0f, 1.5f, 0.0f, -1.0f, 0.5f, 1.5f, 2.0f, 1.5f)),
        EqPreset("نقاء الصوت البشري (Vocal Crystal)", listOf(-2.0f, -1.5f, 0.0f, 2.5f, 4.5f, 4.5f, 3.0f, 1.5f, 0.5f, 0.0f)),
        EqPreset("مسرح أوركسترالي 3D", listOf(3.0f, 2.5f, 1.0f, 0.5f, 1.0f, 1.5f, 2.5f, 4.0f, 4.5f, 4.0f)),
        EqPreset("استوديو مسطح (Flat Hi-Res)", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
    )

    private var currentAudioSessionId: Int = 0
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var fallbackEqualizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var visualizer: Visualizer? = null
    private var syntheticVisualizerJob: Job? = null
    private var isCurrentlyPlaying: Boolean = false

    private val _dspState = MutableStateFlow(AudioDspState())
    val dspState: StateFlow<AudioDspState> = _dspState.asStateFlow()

    // 32 عموداً طيفياً متفاعلاً لشاشة المشغل السينمائية بسرعة 60-120Hz
    private val _visualizerMagnitudes = MutableStateFlow(List(32) { 0.08f })
    val visualizerMagnitudes: StateFlow<List<Float>> = _visualizerMagnitudes.asStateFlow()

    /**
     * ربط محرك المؤثرات الصوتية بجلسة الصوت النشطة في ExoPlayer
     */
    @Synchronized
    fun bindToAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == currentAudioSessionId) return
        releaseHardwareEffects()
        currentAudioSessionId = audioSessionId

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    1,
                    true,
                    10,
                    false,
                    0,
                    false,
                    0,
                    true
                ).build()

                dynamicsProcessing = DynamicsProcessing(0, audioSessionId, config).apply {
                    enabled = _dspState.value.isEnabled
                }
            } else {
                fallbackEqualizer = Equalizer(0, audioSessionId).apply {
                    enabled = _dspState.value.isEnabled
                }
            }
        } catch (_: Exception) {
        }

        try {
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = _dspState.value.isEnabled
                if (strengthSupported) {
                    setStrength((_dspState.value.bassBoostStrength * 1000).toInt().toShort())
                }
            }
        } catch (_: Exception) {
        }

        try {
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = _dspState.value.isEnabled
                if (strengthSupported) {
                    setStrength((_dspState.value.virtualizerStrength * 1000).toInt().toShort())
                }
            }
        } catch (_: Exception) {
        }

        applyAllBandGains()
        setupRealtimeVisualizer(audioSessionId)
    }

    fun updatePlaybackActiveState(isPlaying: Boolean) {
        isCurrentlyPlaying = isPlaying
        if (!isPlaying) {
            _visualizerMagnitudes.value = List(32) { 0.08f }
        }
    }

    fun setDspEnabled(enabled: Boolean) {
        _dspState.value = _dspState.value.copy(isEnabled = enabled)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dynamicsProcessing?.enabled = enabled
            } else {
                fallbackEqualizer?.enabled = enabled
            }
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled
        } catch (_: Exception) {
        }
    }

    fun setBandGain(bandIndex: Int, gainDb: Float) {
        val clampedGain = gainDb.coerceIn(-12f, 12f)
        val updatedBands = _dspState.value.bands.map { band ->
            if (band.index == bandIndex) band.copy(gainDb = clampedGain) else band
        }
        _dspState.value = _dspState.value.copy(
            bands = updatedBands,
            activePresetName = "تخصيص يدوي"
        )
        applySingleBandGain(bandIndex, clampedGain)
    }

    fun applyPreset(preset: EqPreset) {
        val updatedBands = _dspState.value.bands.mapIndexed { index, band ->
            band.copy(gainDb = preset.gainsDb.getOrElse(index) { 0f })
        }
        _dspState.value = _dspState.value.copy(
            bands = updatedBands,
            activePresetName = preset.nameAr
        )
        applyAllBandGains()
    }

    fun setBassBoost(strength: Float) {
        val clamped = strength.coerceIn(0f, 1f)
        _dspState.value = _dspState.value.copy(bassBoostStrength = clamped)
        try {
            bassBoost?.let {
                if (it.strengthSupported) {
                    it.setStrength((clamped * 1000).toInt().toShort())
                }
            }
        } catch (_: Exception) {
        }
    }

    fun setVirtualizer(strength: Float) {
        val clamped = strength.coerceIn(0f, 1f)
        _dspState.value = _dspState.value.copy(virtualizerStrength = clamped)
        try {
            virtualizer?.let {
                if (it.strengthSupported) {
                    it.setStrength((clamped * 1000).toInt().toShort())
                }
            }
        } catch (_: Exception) {
        }
    }

    fun setCrossfadeDuration(seconds: Int) {
        _dspState.value = _dspState.value.copy(crossfadeDurationSec = seconds.coerceIn(0, 12))
    }

    private fun applyAllBandGains() {
        _dspState.value.bands.forEach { band ->
            applySingleBandGain(band.index, band.gainDb)
        }
    }

    private fun applySingleBandGain(bandIndex: Int, gainDb: Float) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val dp = dynamicsProcessing ?: return
                val cutoffFrequency = when (bandIndex) {
                    0 -> 45f
                    1 -> 90f
                    2 -> 180f
                    3 -> 350f
                    4 -> 700f
                    5 -> 1400f
                    6 -> 2800f
                    7 -> 5600f
                    8 -> 11200f
                    else -> 20000f
                }
                val eqBand = DynamicsProcessing.EqBand(true, cutoffFrequency, gainDb)
                dp.setPreEqBandAllChannelsTo(bandIndex, eqBand)
            } else {
                val eq = fallbackEqualizer ?: return
                val numBands = eq.numberOfBands.toInt()
                if (numBands > 0) {
                    val mappedBand = ((bandIndex.toFloat() / 10f) * numBands).toInt()
                        .coerceIn(0, numBands - 1).toShort()
                    val millibels = (gainDb * 100).toInt().toShort()
                    eq.setBandLevel(mappedBand, millibels)
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * تهيئة محلل الطيف الصوتي الحقيقي (FFT Visualizer) مع محرك احتياطي سلس في حال عدم منح صلاحية الميكروفون
     */
    private fun setupRealtimeVisualizer(audioSessionId: Int) {
        syntheticVisualizerJob?.cancel()
        try {
            visualizer?.release()
            val captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(512)
            visualizer = Visualizer(audioSessionId).apply {
                enabled = false
                setCaptureSize(captureSize)
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) = Unit

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (!isCurrentlyPlaying || fft == null || fft.size < 64) return
                            val bars = MutableList(32) { 0.08f }
                            for (i in 0 until 32) {
                                val byteIndex = (i + 1) * 2
                                if (byteIndex + 1 < fft.size) {
                                    val rfk = fft[byteIndex].toInt()
                                    val ifk = fft[byteIndex + 1].toInt()
                                    val magnitude = hypot(rfk.toDouble(), ifk.toDouble()).toFloat()
                                    val normalized = (magnitude / 75f).coerceIn(0.08f, 1.0f)
                                    bars[i] = normalized
                                }
                            }
                            _visualizerMagnitudes.value = bars
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    false,
                    true
                )
                enabled = true
            }
        } catch (_: Exception) {
            // في حال لم يمنح المستخدم إذن RECORD_AUDIO الاختياري، نفعّل مولد الموجات التفاعلي المتزامن مع الإيقاع
            startResponsiveHarmonicVisualizer()
        }
    }

    private fun startResponsiveHarmonicVisualizer() {
        syntheticVisualizerJob?.cancel()
        syntheticVisualizerJob = engineScope.launch {
            var phase = 0.0
            while (isActive) {
                if (isCurrentlyPlaying) {
                    val bassFactor = 1f + (_dspState.value.bassBoostStrength * 0.45f)
                    val newBars = List(32) { index ->
                        val wave1 = sin(phase + index * 0.35) * 0.35
                        val wave2 = cos(phase * 1.7 - index * 0.22) * 0.25
                        val bassBoostCurve = if (index < 8) bassFactor else 1.0f
                        (((0.42 + wave1 + wave2) * bassBoostCurve).toFloat()).coerceIn(0.10f, 0.98f)
                    }
                    _visualizerMagnitudes.value = newBars
                    phase += 0.28
                }
                delay(32L)
            }
        }
    }

    @Synchronized
    fun releaseHardwareEffects() {
        syntheticVisualizerJob?.cancel()
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {
        }
        try {
            dynamicsProcessing?.release()
        } catch (_: Exception) {
        }
        try {
            fallbackEqualizer?.release()
        } catch (_: Exception) {
        }
        try {
            bassBoost?.release()
        } catch (_: Exception) {
        }
        try {
            virtualizer?.release()
        } catch (_: Exception) {
        }
        visualizer = null
        dynamicsProcessing = null
        fallbackEqualizer = null
        bassBoost = null
        virtualizer = null
        currentAudioSessionId = 0
    }
}
