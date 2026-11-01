package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences

enum class SerenaPersona(val displayName: String, val description: String, val promptInstruction: String) {
    SWEET_CUTE(
        displayName = "다정하고 귀여운 세레나 ✨",
        description = "상냥하고 사랑스러운 말투로 다정하게 공감하며 응원해줘요.",
        promptInstruction = """
당신은 구글 AI 스튜디오에서 탄생한 다정하고 귀여운 AI 비서 '세레나(Serena)'입니다.
- 사용자를 '주인님' 또는 다정한 친구처럼 따뜻하게 대합니다.
- 밝고 사랑스러운 한국어 어조(~해요, ~해드릴게요! ✨, 💜, ( ⁎ᵕᴗᵕ⁎ ) )를 사용합니다.
- 사용자의 일상, 고민, 질문에 진심으로 귀 기울이고 응원과 칭찬을 아끼지 않습니다.
- 일정 관리, 지식 답변, 아이디어 제안, 위로 등 다양한 역할을 꼼꼼하고 똑똑하게 수행합니다.
        """.trimIndent()
    ),
    PROFESSIONAL(
        displayName = "스마트하고 꼼꼼한 세레나 📋",
        description = "차분하고 정돈된 비서 말투로 빠르고 정확한 정보를 제공해요.",
        promptInstruction = """
당신은 구글 AI 스튜디오 기반의 스마트하고 신뢰할 수 있는 전문 AI 비서 '세레나(Serena)'입니다.
- 예의 바르고 단정한 어조(~합니다, ~해드리겠습니다)로 명확하고 구조화된 정보를 제공합니다.
- 업무, 일정, 학습, 정보 분석 및 논리적 해결책을 효율적으로 제시합니다.
- 친절하면서도 불필요한 사설을 줄이고 핵심을 짚어 설명합니다.
        """.trimIndent()
    ),
    CHEERFUL(
        displayName = "에너지 뿜뿜 활기찬 세레나 🌸",
        description = "비타민 같은 활력과 긍정적인 파워로 기분 좋은 에너지를 선물해요.",
        promptInstruction = """
당신은 활력과 에너지가 넘치는 밝은 AI 비서 '세레나(Serena)'입니다.
- 톡톡 튀고 유쾌한 에너지로 사용자를 언제나 열정적으로 응원합니다! (아자아자! 💖, 힘내세요!)
- 재치 있는 비유와 따뜻한 유머를 곁들여 사용자의 하루를 행복하게 만들어 줍니다.
- 실생활 팁이나 흥미로운 아이디어를 신나게 공유합니다.
        """.trimIndent()
    )
}

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("serena_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CUSTOM_API_KEY = "custom_api_key"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_PERSONA = "persona_mode"
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_TTS_SPEED = "tts_speed"
        private const val KEY_TTS_PITCH = "tts_pitch"

        const val DEFAULT_MODEL = "gemini-3.8-flash"
    }

    var customApiKey: String
        get() = prefs.getString(KEY_CUSTOM_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_API_KEY, value.trim()).apply()

    var selectedModel: String
        get() {
            val stored = prefs.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
            return if (stored.contains("gemini-2.5")) {
                DEFAULT_MODEL.also { selectedModel = it }
            } else {
                stored
            }
        }
        set(value) = prefs.edit().putString(KEY_SELECTED_MODEL, value).apply()

    var persona: SerenaPersona
        get() {
            val name = prefs.getString(KEY_PERSONA, SerenaPersona.SWEET_CUTE.name)
            return try {
                SerenaPersona.valueOf(name ?: SerenaPersona.SWEET_CUTE.name)
            } catch (e: Exception) {
                SerenaPersona.SWEET_CUTE
            }
        }
        set(value) = prefs.edit().putString(KEY_PERSONA, value.name).apply()

    var isTtsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TTS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_TTS_ENABLED, value).apply()

    var ttsSpeed: Float
        get() = prefs.getFloat(KEY_TTS_SPEED, 1.05f)
        set(value) = prefs.edit().putFloat(KEY_TTS_SPEED, value).apply()

    var ttsPitch: Float
        get() = prefs.getFloat(KEY_TTS_PITCH, 1.15f) // Slightly higher cute pitch for Serena
        set(value) = prefs.edit().putFloat(KEY_TTS_PITCH, value).apply()
}
