package com.example.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "PmVoiceEngine"

class VoiceAssistantEngine(context: Context) {

    private val appContext = context.applicationContext
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = tts?.setLanguage(Locale("en", "IN"))
                ttsReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
                if (!ttsReady) {
                    tts?.language = Locale.US
                    ttsReady = true
                }
            }
        }
    }

    fun isSpeechRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(appContext)
    }

    fun startListening(
        languageTag: String = "en-IN",
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isSpeechRecognitionAvailable()) {
            onError("On-device speech recognition service is unavailable on this device.")
            return
        }
        stopSpeaking()
        stopListening()

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                    _partialTranscript.value = "Listening..."
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _isListening.value = false
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    _partialTranscript.value = ""
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Tap mic and try again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                        else -> "Voice input error ($error)."
                    }
                    onError(msg)
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    _partialTranscript.value = ""
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull()?.trim().orEmpty()
                    if (best.isNotEmpty()) {
                        onResult(best)
                    } else {
                        onError("Could not recognize speech.")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        _partialTranscript.value = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognizer", e)
            _isListening.value = false
            onError("Could not start microphone: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        _isListening.value = false
        _partialTranscript.value = ""
        runCatching {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }

    fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        val clean = text.replace("**", "").replace("`", "").take(600)
        _isSpeaking.value = true
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "pm_ai_tts_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        _isSpeaking.value = false
        runCatching { tts?.stop() }
    }

    fun release() {
        stopListening()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }
}
