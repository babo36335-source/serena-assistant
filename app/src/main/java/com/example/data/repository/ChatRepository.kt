package com.example.data.repository

import com.example.data.api.GeminiApiClient
import com.example.data.local.ChatDao
import com.example.data.local.ChatMessage
import com.example.data.local.ChatSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ChatRepository(
    private val chatDao: ChatDao,
    private val apiClient: GeminiApiClient
) {
    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessions()

    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    suspend fun createNewSession(initialTitle: String = "새로운 대화"): Long {
        val session = ChatSession(
            title = initialTitle,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        return chatDao.insertSession(session)
    }

    suspend fun deleteSession(sessionId: Long) {
        chatDao.deleteMessagesBySession(sessionId)
        chatDao.deleteSession(sessionId)
    }

    suspend fun updateSessionTitle(sessionId: Long, newTitle: String) {
        val session = chatDao.getSessionById(sessionId) ?: return
        chatDao.updateSession(session.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
    }

    suspend fun clearAllData() {
        chatDao.clearAllMessages()
        chatDao.clearAllSessions()
    }

    suspend fun deleteMessage(messageId: Long) {
        chatDao.deleteMessage(messageId)
    }

    suspend fun sendUserMessage(
        sessionId: Long,
        userPrompt: String,
        onChunkReceived: ((String) -> Unit)? = null
    ): Result<ChatMessage> {
        // 1. Insert user message
        val userMsg = ChatMessage(
            sessionId = sessionId,
            role = "user",
            content = userPrompt.trim(),
            timestamp = System.currentTimeMillis()
        )
        chatDao.insertMessage(userMsg)

        // 2. Update session title if default
        val session = chatDao.getSessionById(sessionId)
        if (session != null && (session.title == "새로운 대화" || session.title.isBlank())) {
            val shortTitle = if (userPrompt.length > 18) {
                userPrompt.take(18) + "..."
            } else {
                userPrompt
            }
            chatDao.updateSession(session.copy(title = shortTitle, updatedAt = System.currentTimeMillis()))
        } else if (session != null) {
            chatDao.updateSession(session.copy(updatedAt = System.currentTimeMillis()))
        }

        // 3. Fetch past messages for context
        val history = chatDao.getMessagesListForSession(sessionId)

        // 4. Call Gemini API
        val responseResult = if (onChunkReceived != null) {
            apiClient.streamResponse(
                history = history.dropLast(1), // Exclude the just-inserted user message from history because it's passed as prompt
                latestUserPrompt = userPrompt,
                onChunk = onChunkReceived
            )
        } else {
            apiClient.generateResponse(
                history = history.dropLast(1),
                latestUserPrompt = userPrompt
            )
        }

        return if (responseResult.isSuccess) {
            val responseText = responseResult.getOrNull() ?: ""
            val modelMsg = ChatMessage(
                sessionId = sessionId,
                role = "model",
                content = responseText,
                timestamp = System.currentTimeMillis(),
                isError = false
            )
            val modelMsgId = chatDao.insertMessage(modelMsg)
            Result.success(modelMsg.copy(id = modelMsgId))
        } else {
            val error = responseResult.exceptionOrNull()
            val errorText = error?.message ?: "알 수 없는 오류가 발생했습니다."
            val errorMsg = ChatMessage(
                sessionId = sessionId,
                role = "model",
                content = errorText,
                timestamp = System.currentTimeMillis(),
                isError = true
            )
            val errorMsgId = chatDao.insertMessage(errorMsg)
            Result.failure(Exception(errorText))
        }
    }
}
