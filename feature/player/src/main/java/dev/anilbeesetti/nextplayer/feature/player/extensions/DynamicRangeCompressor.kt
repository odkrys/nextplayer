package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.model.DrcPreset


@OptIn(UnstableApi::class)
class DynamicRangeCompressor(
    private val audioSessionId: Int,
    val channelCount: Int = 2,
) {

    private var dynamicsProcessing: DynamicsProcessing? = null

    var enabled: Boolean = false
        set(value) {
            field = value
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dynamicsProcessing?.enabled = value
            }
        }

    var attackTime: Float = 10f
    var releaseTime: Float = 250f
    var ratio: Float = 4f
    var threshold: Float = -20f
    var kneeWidth: Float = 6f
    var postGain: Float = 8f

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setup()
        }
    }

    fun applyPreset(preset: DrcPreset) {
        when (preset) {
            DrcPreset.LIGHT -> {
                threshold = -20f
                ratio = 4f
                postGain = 6f
                attackTime = 10f
                releaseTime = 250f
            }
            DrcPreset.STRONG -> {
                threshold = -28f
                ratio = 10f
                postGain = 12f
                attackTime = 5f
                releaseTime = 200f
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            updateBands()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun updateBands() {
        val dp = dynamicsProcessing ?: run {
            return
        }
        for (ch in 0 until channelCount) {
            try {
                val band = dp.getMbcBandByChannelIndex(ch, 0).apply {
                    this.attackTime = this@DynamicRangeCompressor.attackTime
                    this.releaseTime = this@DynamicRangeCompressor.releaseTime
                    this.ratio = this@DynamicRangeCompressor.ratio
                    this.threshold = this@DynamicRangeCompressor.threshold
                    this.kneeWidth = this@DynamicRangeCompressor.kneeWidth
                    this.postGain = this@DynamicRangeCompressor.postGain
                }
                dp.setMbcBandByChannelIndex(ch, 0, band)
            } catch (e: Exception) {
                // ignore
            }
        }

        try {
            dp.enabled = false
            dp.enabled = enabled
        } catch (e: Exception) {}
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun setup() {
        try {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                channelCount,      // channelCount
                false,  // preEqInUse
                0,      // preEqBandCount
                true,   // mbcInUse
                1,      // mbcBandCount
                false,  // postEqInUse
                0,      // postEqBandCount
                false,  // limiterInUse
            ).build()

            dynamicsProcessing = DynamicsProcessing(0, audioSessionId, config).apply {
                for (ch in 0 until channelCount) {
                    try {
                        val band = getMbcBandByChannelIndex(ch, 0).apply {
                            this.attackTime = this@DynamicRangeCompressor.attackTime
                            this.releaseTime = this@DynamicRangeCompressor.releaseTime
                            this.ratio = this@DynamicRangeCompressor.ratio
                            this.threshold = this@DynamicRangeCompressor.threshold
                            this.kneeWidth = this@DynamicRangeCompressor.kneeWidth
                            this.noiseGateThreshold = -80f
                            this.expanderRatio = 1f
                            this.preGain = 0f
                            this.postGain = this@DynamicRangeCompressor.postGain
                        }
                        setMbcBandByChannelIndex(ch, 0, band)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            dynamicsProcessing = null
        }
    }

    fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dynamicsProcessing?.release()
        }
        dynamicsProcessing = null
    }

    val isAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dynamicsProcessing != null
}

