package nirmal.auric.music.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TTSManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingSpeak: String? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                isReady = true
                pendingSpeak?.let { speak(it) }
                pendingSpeak = null
            } else {
                Log.e("TTSManager", "TTS initialization failed with status: $status")
            }
        }
    }

    fun speak(text: String) {
        if (!isReady) {
            pendingSpeak = text
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "echo_tts_${System.currentTimeMillis()}")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
