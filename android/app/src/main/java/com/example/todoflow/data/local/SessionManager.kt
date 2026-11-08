package com.example.todoflow.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Holds the login session and app settings.
 *
 * Secrets (access token, refresh token, email) live in [EncryptedSharedPreferences], encrypted with
 * a key kept in the Android Keystore. Non-sensitive settings (server URL, theme, toggles) stay in
 * ordinary preferences.
 */
class SessionManager(context: Context) {

    companion object {
        private const val TAG = "SessionManager"
        private const val PREF_NAME = "todoflow_session"
        private const val SECURE_PREF_NAME = "todoflow_secure_session"

        // Secrets (encrypted)
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_LAST_EMAIL = "last_user_email"

        // Plain settings
        private const val KEY_SESSION_EXPIRED = "session_expired"
        private const val KEY_BASE_URL = "backend_url"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_REMINDERS_ENABLED = "reminders_enabled"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        const val DEFAULT_BASE_URL = "https://todoflow.ddns.net/"

        private fun createSecurePrefs(context: Context): SharedPreferences {
            fun build(): SharedPreferences {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                return EncryptedSharedPreferences.create(
                    context,
                    SECURE_PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }
            return try {
                build()
            } catch (e: Exception) {
                // Keystore keys can disappear (device restore, OS bug). The encrypted file is then
                // unreadable: discard it and start clean; the user simply logs in again.
                Log.w(TAG, "Secure storage unreadable, resetting it", e)
                context.deleteSharedPreferences(SECURE_PREF_NAME)
                try {
                    build()
                } catch (e2: Exception) {
                    // Last resort so the app stays usable on devices with a broken Keystore.
                    Log.e(TAG, "Secure storage unavailable, falling back to private preferences", e2)
                    context.getSharedPreferences(SECURE_PREF_NAME + "_fallback", Context.MODE_PRIVATE)
                }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val securePrefs: SharedPreferences = createSecurePrefs(context)

    init {
        migrateLegacySecrets()
    }

    /** Older versions stored the tokens in plain preferences: move them into encrypted storage. */
    private fun migrateLegacySecrets() {
        if (!prefs.contains(KEY_TOKEN) && !prefs.contains(KEY_REFRESH_TOKEN) && !prefs.contains(KEY_EMAIL)) return
        val editor = securePrefs.edit()
        prefs.getString(KEY_TOKEN, null)?.let { editor.putString(KEY_TOKEN, it) }
        prefs.getString(KEY_REFRESH_TOKEN, null)?.let { editor.putString(KEY_REFRESH_TOKEN, it) }
        prefs.getString(KEY_EMAIL, null)?.let { editor.putString(KEY_EMAIL, it) }
        prefs.getString(KEY_LAST_EMAIL, null)?.let { editor.putString(KEY_LAST_EMAIL, it) }
        if (editor.commit()) {
            prefs.edit().remove(KEY_TOKEN).remove(KEY_REFRESH_TOKEN).remove(KEY_EMAIL).remove(KEY_LAST_EMAIL).apply()
        }
    }

    private val _isLoggedIn = MutableStateFlow(!securePrefs.getString(KEY_TOKEN, null).isNullOrBlank())
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

    private val _remindersEnabled = MutableStateFlow(prefs.getBoolean(KEY_REMINDERS_ENABLED, false))

    /** Whether due-date reminder notifications are on. */
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled.asStateFlow()

    fun setRemindersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDERS_ENABLED, enabled).apply()
        _remindersEnabled.value = enabled
    }

    private val _appLockEnabled = MutableStateFlow(prefs.getBoolean(KEY_APP_LOCK_ENABLED, false))

    /** Whether the app asks for fingerprint / face / device credentials when opened. */
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled.asStateFlow()

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
        _appLockEnabled.value = enabled
    }

    var token: String?
        get() = securePrefs.getString(KEY_TOKEN, null)
        private set(value) {
            securePrefs.edit().putString(KEY_TOKEN, value).apply()
            _isLoggedIn.value = !value.isNullOrBlank()
        }

    var refreshToken: String?
        get() = securePrefs.getString(KEY_REFRESH_TOKEN, null)
        private set(value) {
            securePrefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()
        }

    var userEmail: String?
        get() = securePrefs.getString(KEY_EMAIL, null)
        private set(value) {
            securePrefs.edit().putString(KEY_EMAIL, value).apply()
        }

    /** Email of the last user who logged in; survives logout/expiry so a different user's cache can be reset. */
    val lastLoggedInEmail: String?
        get() = securePrefs.getString(KEY_LAST_EMAIL, null)

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
            securePrefs.edit().putString(KEY_LAST_EMAIL, email).apply()
        }
    }

    /** Replaces only the access token (used after a silent token refresh); keeps email/refresh token. */
    fun updateAccessToken(newToken: String, newRefreshToken: String? = null) {
        this.token = newToken
        if (!newRefreshToken.isNullOrBlank()) {
            this.refreshToken = newRefreshToken
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

    fun clearSession() {
        securePrefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EMAIL)
            .apply()
        _isLoggedIn.value = false
    }
}
