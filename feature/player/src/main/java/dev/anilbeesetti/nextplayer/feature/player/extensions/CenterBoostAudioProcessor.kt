package dev.anilbeesetti.nextplayer.feature.player.extensions

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max

@OptIn(UnstableApi::class)
class CenterBoostAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var centerBoostGain: Float = 1.0f
    var centerChannelIndex: Int = 2

    @Volatile
    var decoderOutputChannels: Int = -1
        private set

    private var currentLimiterGain = 1.0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        decoderOutputChannels = inputAudioFormat.channelCount

        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount < 3) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val buffer = replaceOutputBuffer(remaining)

        if (centerBoostGain <= 1.001f) {
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val channels = inputAudioFormat.channelCount
        val duckingFactor = 1.0f / centerBoostGain

        while (inputBuffer.hasRemaining()) {
            val frame = IntArray(channels)
            for (i in 0 until channels) {
                frame[i] = inputBuffer.short.toInt()
            }

            val originalCenter = frame[centerChannelIndex]
            val originalFl = frame[0]
            val originalFr = frame[1]

            val boostedCenter = (originalCenter * centerBoostGain).toInt()
            val rawFl = (originalFl * duckingFactor).toInt() + boostedCenter
            val rawFr = (originalFr * duckingFactor).toInt() + boostedCenter

            val peak = max(abs(rawFl), abs(rawFr)).toFloat()
            val maxAllowed = 30000f

            val targetGain = if (peak > maxAllowed) maxAllowed / peak else 1.0f

            if (targetGain < currentLimiterGain) {
                currentLimiterGain = targetGain
            } else {
                currentLimiterGain += 0.00002f
                if (currentLimiterGain > 1.0f) currentLimiterGain = 1.0f
            }

            var newFl = (rawFl * currentLimiterGain).toInt()
            var newFr = (rawFr * currentLimiterGain).toInt()

            frame[centerChannelIndex] = 0

            if (newFl > Short.MAX_VALUE) newFl = Short.MAX_VALUE.toInt()
            if (newFl < Short.MIN_VALUE) newFl = Short.MIN_VALUE.toInt()
            if (newFr > Short.MAX_VALUE) newFr = Short.MAX_VALUE.toInt()
            if (newFr < Short.MIN_VALUE) newFr = Short.MIN_VALUE.toInt()

            frame[0] = newFl
            frame[1] = newFr

            for (i in 0 until channels) {
                var sample = frame[i]
                if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE.toInt()
                if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE.toInt()
                buffer.putShort(sample.toShort())
            }
        }

        buffer.flip()
    }
}
