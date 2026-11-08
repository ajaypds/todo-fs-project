package com.example.todoflow.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

class SessionManager(context: Context) {

    companion object {
        private const val PREF_NAME = "todoflow_session"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_LAST_EMAIL = "last_user_email"
        private const val KEY_SESSION_EXPIRED = "session_expired"
        private const val KEY_BASE_URL = "backend_url"
        private const val KEY_THEME_MODE = "theme_mode"
        const val DEFAULT_BASE_URL = "https://todoflow.ddns.net/"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _isLoggedIn = MutableStateFlow(!prefs.getString(KEY_TOKEN, null).isNullOrBlank())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _themeMode = MutableStateFlow(
        try {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        private set(value) {
            prefs.edit().putString(KEY_TOKEN, value).apply()
            _isLoggedIn.value = !value.isNullOrBlank()
        }

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        private set(value) {
            prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()
        }

    var userEmail: String?
        get() = prefs.getString(KEY_EMAIL, null)
        private set(value) {
            prefs.edit().putString(KEY_EMAIL, value).apply()
        }

    /** Email of the last user who logged in; survives logout/expiry so a different user's cache can be reset. */
    val lastLoggedInEmail: String?
        get() = prefs.getString(KEY_LAST_EMAIL, null)

    var baseUrl: String
        get() {
            val url = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
            return if (url.endsWith("/")) url else "$url/"
        }
        set(value) {
            val normalized = if (value.endsWith("/")) value else "$value/"
            prefs.edit().putString(KEY_BASE_URL, normalized).apply()
        }

    fun saveSession(token: String, refreshToken: String? = null, email: String? = null) {
        this.token = token
        this.refreshToken = refreshToken
        this.userEmail = email
        prefs.edit().remove(KEY_SESSION_EXPIRED).apply()
        if (!email.isNullOrBlank()) {
            prefs.edit().putString(KEY_LAST_EMAIL, email).apply()
        }
    }

    /** Ends the session because the server rejected the refresh token; the login screen explains why. */
    fun expireSession() {
        prefs.edit().putBoolean(KEY_SESSION_EXPIRED, true).apply()
        clearSession()
    }

    /** Returns true once if the last logout was caused by an expired session. */
    fun consumeSessionExpired(): Boolean {
        val expired = prefs.getBoolean(KEY_SESSION_EXPIRED, false)
        if (expired) prefs.edit().remove(KEY_SESSION_EXPIRED).apply()
        return expired
    }

    /** Replaces only the access token (used after a silent token refresh); keeps email/refresh token. */
    fun updateAccessToken(newToken: String, newRefreshToken: String? = null) {
        this.token = newToken
        if (!newRefreshToken.isNullOrBlank()) {
            this.refreshToken = newRefreshToken
        }
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EMAIL)
            .apply()
        _isLoggedIn.value = false
    }
}
