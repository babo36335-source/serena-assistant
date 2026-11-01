package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiApiClient
import com.example.data.local.AppDatabase
import com.example.data.local.ChatMessage
import com.example.data.local.ChatSession
import com.example.data.preferences.UserPreferences
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    val preferences = UserPreferences(application)
    private val database = AppDatabase.getInstance(application)
    private val apiClient = GeminiApiClient(preferences)
    private val repository = ChatRepository(database.chatDao(), apiClient)

    val sessions: StateFlow<List<ChatSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

    val messages: StateFlow<List<ChatMessage>> = _currentSessionId.flatMapLatest { sessionId ->
        if (sessionId == null) flowOf(emptyList())
        else repository.getMessagesForSession(sessionId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _speakingMessageId = MutableStateFlow<Long?>(null)
    val speakingMessageId: StateFlow<Long?> = _speakingMessageId.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        // Automatically open or create first session
        viewModelScope.launch {
            repository.allSessions.collect { list ->
                if (_currentSessionId.value == null) {
                    if (list.isNotEmpty()) {
                        _currentSessionId.value = list.first().id
                    } else {
                        val newId = repository.createNewSession("세레나와의 첫 대화 ✨")
                        _currentSessionId.value = newId
                    }
                }
            }
        }
    }

    fun selectSession(sessionId: Long) {
        _currentSessionId.value = sessionId
    }

    fun createNewSession() {
        viewModelScope.launch {
            val newId = repository.createNewSession("새로운 대화")
            _currentSessionId.value = newId
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                _currentSessionId.value = null
            }
        }
    }

    fun renameSession(sessionId: Long, newTitle: String) {
        viewModelScope.launch {
            repository.updateSessionTitle(sessionId, newTitle)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            val newId = repository.createNewSession("새로운 대화 ✨")
            _currentSessionId.value = newId
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    fun sendMessage(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || _isGenerating.value) return

        val sessionId = _currentSessionId.value ?: return

        viewModelScope.launch {
            _isGenerating.value = true
            _streamingText.value = ""

            val result = repository.sendUserMessage(
                sessionId = sessionId,
                userPrompt = trimmed,
                onChunkReceived = { chunk ->
                    _streamingText.value += chunk
                }
            )

            _isGenerating.value = false
            _streamingText.value = ""

            if (result.isFailure) {
                _snackbarMessage.value = result.exceptionOrNull()?.message ?: "응답을 생성하는 중 문제가 발생했습니다."
            }
        }
    }

    fun setSpeakingMessageId(id: Long?) {
        _speakingMessageId.value = id
    }

    fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }
}
