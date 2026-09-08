package nirmal.auric.music.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

@UnstableApi
class StereoWidenerAudioProcessor : AudioProcessor {

    @Volatile
    var width: Float = 1f
        set(value) {
            field = value.coerceIn(1f, 2f)
        }

    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isActive = false

    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        if (encoding != C.ENCODING_PCM_16BIT || channelCount > 2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        isActive = true
        return inputAudioFormat
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inputSize = inputBuffer.remaining()
        if (inputSize == 0) return

        if (outputBuffer.capacity() < inputSize) {
            outputBuffer = ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        val widthNow = width
        val sampleCount = inputSize / 2
        when (channelCount) {
            2 -> repeat(sampleCount / 2) {
                val left = inputBuffer.getShort().toInt()
                val right = inputBuffer.getShort().toInt()

                val mid = (left + right) * 0.5
                val side = (left - right) * 0.5 * widthNow

                var outLeft = mid + side
                var outRight = mid - side

                val peak = maxOf(abs(outLeft), abs(outRight))
                if (peak > 32767.0) {
                    val scale = 32767.0 / peak
                    outLeft *= scale
                    outRight *= scale
                }

                outputBuffer.putShort(outLeft.toInt().toShort())
                outputBuffer.putShort(outRight.toInt().toShort())
            }
            else -> repeat(sampleCount) {
                outputBuffer.putShort(inputBuffer.getShort())
            }
        }

        outputBuffer.flip()
    }

    override fun getOutput(): ByteBuffer {
        val buffer = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return buffer
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer.remaining() == 0

    @Deprecated("Deprecated in Java")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        channelCount = 0
        encoding = C.ENCODING_INVALID
        isActive = false
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }
}
