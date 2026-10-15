package com.example.todoflow.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todoflow.data.local.SessionManager
import com.example.todoflow.data.model.LoginRequest
import com.example.todoflow.data.remote.ApiClient
import com.example.todoflow.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val serverUrl: String = SessionManager.DEFAULT_BASE_URL,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class AuthViewModel(
    private val sessionManager: SessionManager,
    private val apiClient: ApiClient,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(
            email = sessionManager.userEmail ?: "",
            serverUrl = sessionManager.baseUrl
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEmailChanged(email: String) {
        _uiState.value = _uiState.value.copy(email = email, errorMessage = null)
    }

    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(password = password, errorMessage = null)
    }

    fun onServerUrlChanged(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url, errorMessage = null)
    }

    fun login() {
        val state = _uiState.value
        val email = state.email.trim()
        val password = state.password.trim()

        if (email.isBlank() || password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Email and password are required")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            try {
                // Update server base url if user changed it
                sessionManager.baseUrl = state.serverUrl.trim()

                val api = apiClient.getApi()
                val response = api.login(LoginRequest(email = email, password = password))

                val token = response.token
                if (!token.isNullOrBlank()) {
                    sessionManager.saveSession(
                        token = token,
                        refreshToken = response.refreshToken,
                        email = email
                    )
                    // Pre-fetch tasks and projects into local SQLite immediately
                    taskRepository.refresh()
                    _uiState.value = _uiState.value.copy(isLoading = false)
                } else if (response.emailVerificationRequired) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Please verify your email before logging in"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = response.message ?: "Authentication failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Failed to connect to server"
                )
            }
        }
    }
}
