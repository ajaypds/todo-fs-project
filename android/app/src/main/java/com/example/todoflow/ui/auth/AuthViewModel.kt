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
    val errorMessage: String? = null,
    val isConnectionError: Boolean = false
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
        _uiState.value = _uiState.value.copy(email = email, errorMessage = null, isConnectionError = false)
    }

    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(password = password, errorMessage = null, isConnectionError = false)
    }

    fun onServerUrlChanged(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url, errorMessage = null, isConnectionError = false)
    }

    fun login() {
        val state = _uiState.value
        val email = state.email.trim()
        val password = state.password.trim()

        if (email.isBlank() || password.isBlank()) {
            _uiState.value = state.copy(
                errorMessage = "Email and password are required",
                isConnectionError = false
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null, isConnectionError = false)
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
                        errorMessage = "Please verify your email before logging in",
                        isConnectionError = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = response.message ?: "Invalid email or password",
                        isConnectionError = false
                    )
                }
            } catch (e: Exception) {
                val isConn = isConnectionException(e)
                val friendlyMessage = formatNetworkErrorMessage(e, state.serverUrl.trim())

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = friendlyMessage,
                    isConnectionError = isConn
                )
            }
        }
    }

    private fun isConnectionException(e: Throwable): Boolean {
        if (e is java.net.ConnectException ||
            e is java.net.SocketTimeoutException ||
            e is java.net.UnknownHostException
        ) {
            return true
        }
        val msg = e.localizedMessage ?: ""
        return msg.contains("failed to connect", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true) ||
            msg.contains("ECONNREFUSED", ignoreCase = true)
    }

    private fun formatNetworkErrorMessage(e: Throwable, serverUrl: String): String {
        return when (e) {
            is java.net.ConnectException -> {
                "Server is unreachable or offline ($serverUrl).\n\nPlease verify that your TodoFlow backend is running and that your phone/emulator has internet access."
            }
            is java.net.SocketTimeoutException -> {
                "Connection timed out while contacting server ($serverUrl).\n\nThe server took too long to respond. Please check your network and try again."
            }
            is java.net.UnknownHostException -> {
                "Cannot resolve server host ($serverUrl).\n\nPlease check your internet connection or verify the server address in Server Settings below."
            }
            is retrofit2.HttpException -> {
                when (e.code()) {
                    401 -> "Invalid email or password. Please verify your credentials."
                    403 -> "Account verification required or access restricted."
                    404 -> "TodoFlow API endpoint was not found on this server."
                    500, 502, 503, 504 -> "The server is currently unavailable or experiencing an issue (${e.code()}). Please try again shortly."
                    else -> "Server returned error ${e.code()}. Please try again."
                }
            }
            else -> {
                val msg = e.localizedMessage ?: ""
                when {
                    msg.contains("failed to connect", ignoreCase = true) || msg.contains("ECONNREFUSED", ignoreCase = true) ->
                        "Server is unreachable ($serverUrl).\n\nPlease verify that the server is online or check Server Settings below."
                    msg.contains("timeout", ignoreCase = true) ->
                        "Connection timed out. The server took too long to respond."
                    else ->
                        "Unable to connect to server ($serverUrl). Please check your internet connection and try again."
                }
            }
        }
    }
}
