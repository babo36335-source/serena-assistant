package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import com.example.data.local.ChatMessage
import com.example.data.preferences.SerenaPersona
import com.example.data.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class GeminiApiClient(private val preferences: UserPreferences) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Model fallback sequence in case of 503 (overloaded), 429 (rate limit), or 404
    private val fallbackModels = listOf("gemini-3.8-flash", "gemini-3.5-flash", "gemini-3.1-pro-preview")

    private fun getEffectiveApiKey(): String {
        val customKey = preferences.customApiKey.trim()
        if (customKey.isNotEmpty()) {
            return customKey
        }
        val buildConfigKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
        return if (buildConfigKey.isNotEmpty() && buildConfigKey != "MY_GEMINI_API_KEY") {
            buildConfigKey
        } else {
            customKey
        }
    }

    private fun getModelsToTry(requestedModel: String): List<String> {
        val clean = requestedModel.trim().removePrefix("models/")
        val list = mutableListOf(clean)
        for (m in fallbackModels) {
            if (m != clean && !list.contains(m)) {
                list.add(m)
            }
        }
        return list
    }

    private fun parseErrorMessage(code: Int, responseBody: String): String {
        var apiMsg = ""
        try {
            val errJson = JSONObject(responseBody)
            val errorObj = errJson.optJSONObject("error")
            apiMsg = errorObj?.optString("message") ?: ""
        } catch (e: Exception) {
            // Ignore
        }

        return when (code) {
            503 -> "세레나 AI 서버가 현재 일시적인 트래픽 폭주 상태입니다(503). 잠시 후 다시 전송해 주세요 💜"
            429 -> "요청 횟수 제한(429)에 도달했습니다. 잠시 숨을 고르고 다시 말을 걸어주세요 ✨"
            404 -> "AI 모델을 찾을 수 없습니다(404). 다른 모델로 자동 전환을 시도합니다."
            401, 403 -> "API 키 인증에 실패했습니다(코드: $code). 설정(⚙️)에서 AI Studio API 키를 확인해 주세요."
            else -> if (apiMsg.isNotBlank()) "세레나 응답 오류: $apiMsg" else "API 오류 (코드: $code)"
        }
    }

    suspend fun generateResponse(
        history: List<ChatMessage>,
        latestUserPrompt: String,
        persona: SerenaPersona = preferences.persona,
        modelName: String = preferences.selectedModel
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini API 키가 설정되지 않았습니다. 우측 상단 설정(⚙️) 아이콘을 눌러 구글 AI 스튜디오 API 키를 입력해 주세요!")
            )
        }

        val modelsToTry = getModelsToTry(modelName)
        var lastException: Exception? = null

        for (currModel in modelsToTry) {
            // Retry loop up to 3 times for transient 503/429 errors with exponential backoff
            val maxRetries = 3
            var retryDelay = 1200L

            for (attempt in 1..maxRetries) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$currModel:generateContent?key=$apiKey"
                    val rootJson = buildRequestBody(history, latestUserPrompt, persona)

                    val requestBody = rootJson.toString().toRequestBody(jsonMediaType)
                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        val jsonResponse = JSONObject(responseBody)
                        val candidates = jsonResponse.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val candidate = candidates.getJSONObject(0)
                            val content = candidate.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                val text = parts.getJSONObject(0).optString("text")
                                if (text.isNotEmpty()) {
                                    return@withContext Result.success(text)
                                }
                            }
                        }
                        lastException = Exception("세레나가 답변을 생성하지 못했습니다. 다시 시도해 주세요.")
                        break // Non-retryable empty response for this model
                    } else {
                        val friendlyMsg = parseErrorMessage(response.code, responseBody)
                        lastException = Exception(friendlyMsg)
                        Log.w("GeminiApiClient", "Attempt $attempt on $currModel returned ${response.code}: $responseBody")

                        // Retry if 503 (Service Unavailable) or 429 (Too Many Requests) or 500
                        if ((response.code == 503 || response.code == 429 || response.code == 500) && attempt < maxRetries) {
                            delay(retryDelay)
                            retryDelay *= 2
                            continue
                        } else {
                            // Move to next fallback model
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GeminiApiClient", "Network Error on $currModel attempt $attempt", e)
                    lastException = Exception("네트워크 연결을 확인해 주세요: ${e.localizedMessage}")
                    if (attempt < maxRetries) {
                        delay(retryDelay)
                        retryDelay *= 2
                    }
                }
            }
        }

        Result.failure(lastException ?: Exception("서버에 일시적인 장애가 발생했습니다(503). 잠시 후 다시 시도해 주세요."))
    }

    suspend fun streamResponse(
        history: List<ChatMessage>,
        latestUserPrompt: String,
        persona: SerenaPersona = preferences.persona,
        modelName: String = preferences.selectedModel,
        onChunk: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini API 키가 설정되지 않았습니다. 우측 상단 설정(⚙️) 아이콘을 눌러 구글 AI 스튜디오 API 키를 입력해 주세요!")
            )
        }

        val modelsToTry = getModelsToTry(modelName)
        var lastException: Exception? = null

        for (currModel in modelsToTry) {
            val maxRetries = 2
            var retryDelay = 1000L

            for (attempt in 1..maxRetries) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$currModel:streamGenerateContent?alt=sse&key=$apiKey"
                    val rootJson = buildRequestBody(history, latestUserPrompt, persona)

                    val requestBody = rootJson.toString().toRequestBody(jsonMediaType)
                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .build()

                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string() ?: ""
                        val friendlyMsg = parseErrorMessage(response.code, errBody)
                        lastException = Exception(friendlyMsg)
                        Log.w("GeminiApiClient", "Stream attempt $attempt on $currModel failed with ${response.code}: $errBody")

                        if ((response.code == 503 || response.code == 429 || response.code == 500) && attempt < maxRetries) {
                            delay(retryDelay)
                            retryDelay *= 2
                            continue
                        } else {
                            break
                        }
                    }

                    val fullText = StringBuilder()
                    val inputStream = response.body?.byteStream() ?: break
                    val reader = BufferedReader(InputStreamReader(inputStream))

                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val rawLine = line?.trim() ?: continue
                        if (!rawLine.startsWith("data:")) continue
                        val jsonPayload = rawLine.removePrefix("data:").trim()
                        if (jsonPayload.isEmpty() || jsonPayload == "[DONE]") continue

                        try {
                            val chunkJson = JSONObject(jsonPayload)
                            val candidates = chunkJson.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val content = candidates.getJSONObject(0).optJSONObject("content")
                                val parts = content?.optJSONArray("parts")
                                if (parts != null && parts.length() > 0) {
                                    val chunkText = parts.getJSONObject(0).optString("text")
                                    if (chunkText.isNotEmpty()) {
                                        fullText.append(chunkText)
                                        withContext(Dispatchers.Main) {
                                            onChunk(chunkText)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore malformed chunk
                        }
                    }

                    if (fullText.isNotEmpty()) {
                        return@withContext Result.success(fullText.toString())
                    } else {
                        // If empty stream, fallback to generateResponse (non-streaming)
                        val fallbackNonStream = generateResponse(history, latestUserPrompt, persona, currModel)
                        if (fallbackNonStream.isSuccess) {
                            val txt = fallbackNonStream.getOrNull().orEmpty()
                            withContext(Dispatchers.Main) { onChunk(txt) }
                            return@withContext Result.success(txt)
                        }
                    }
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < maxRetries) {
                        delay(retryDelay)
                        retryDelay *= 2
                    }
                }
            }
        }

        // If streaming failed with 503/errors on all tries, do one last fallback attempt via generateResponse
        val finalSyncAttempt = generateResponse(history, latestUserPrompt, persona, modelName)
        if (finalSyncAttempt.isSuccess) {
            val syncTxt = finalSyncAttempt.getOrNull().orEmpty()
            withContext(Dispatchers.Main) { onChunk(syncTxt) }
            return@withContext Result.success(syncTxt)
        }

        Result.failure(lastException ?: finalSyncAttempt.exceptionOrNull() ?: Exception("서버가 일시적으로 응답하지 못했습니다(503). 잠시 후 다시 시도해 주세요."))
    }

    private fun buildRequestBody(
        history: List<ChatMessage>,
        latestUserPrompt: String,
        persona: SerenaPersona
    ): JSONObject {
        val rootJson = JSONObject()

        // System instruction
        val systemInstructionObj = JSONObject()
        val sysPartsArray = JSONArray()
        sysPartsArray.put(JSONObject().put("text", persona.promptInstruction))
        systemInstructionObj.put("parts", sysPartsArray)
        rootJson.put("systemInstruction", systemInstructionObj)

        // Generation config
        val generationConfig = JSONObject()
        generationConfig.put("temperature", 0.75)
        generationConfig.put("topP", 0.95)
        rootJson.put("generationConfig", generationConfig)

        // Conversation history (limit to last 14 turns to reduce payload size & latency)
        val contentsArray = JSONArray()
        val recentHistory = history.takeLast(14)
        for (msg in recentHistory) {
            if (msg.content.isBlank()) continue
            val turnObj = JSONObject()
            turnObj.put("role", if (msg.role == "user") "user" else "model")
            val parts = JSONArray()
            parts.put(JSONObject().put("text", msg.content))
            turnObj.put("parts", parts)
            contentsArray.put(turnObj)
        }

        // Current prompt
        val currentTurn = JSONObject()
        currentTurn.put("role", "user")
        val currentParts = JSONArray()
        currentParts.put(JSONObject().put("text", latestUserPrompt))
        currentTurn.put("parts", currentParts)
        contentsArray.put(currentTurn)

        rootJson.put("contents", contentsArray)
        return rootJson
    }
}
