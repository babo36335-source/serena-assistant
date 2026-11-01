package com.example

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.ChatScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.SerenaTheme
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private val viewModel: MainViewModel by viewModels()
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        textToSpeech = TextToSpeech(this, this)

        setContent {
            SerenaTheme {
                ChatScreen(
                    viewModel = viewModel,
                    onSpeakText = { text, msgId, speed, pitch ->
                        speakOut(text, msgId, speed, pitch)
                    },
                    onStopSpeech = {
                        stopSpeech()
                    }
                )
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to default
                textToSpeech?.setLanguage(Locale.getDefault())
            }
            isTtsReady = true

            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        viewModel.setSpeakingMessageId(null)
                    }
                }

                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        viewModel.setSpeakingMessageId(null)
                    }
                }
            })
        } else {
            isTtsReady = false
        }
    }

    private fun speakOut(text: String, msgId: Long, speed: Float, pitch: Float) {
        if (!isTtsReady || textToSpeech == null) {
            Toast.makeText(this, "음성 엔진(TTS)을 초기화하는 중입니다.", Toast.LENGTH_SHORT).show()
            viewModel.setSpeakingMessageId(null)
            return
        }

        textToSpeech?.setPitch(pitch)
        textToSpeech?.setSpeechRate(speed)

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "serena_utt_$msgId")
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "serena_utt_$msgId")
    }

    private fun stopSpeech() {
        textToSpeech?.stop()
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}
