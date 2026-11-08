package com.example.todoflow.data.remote

import com.example.todoflow.data.local.SessionManager
import com.example.todoflow.data.model.AuthResponse
import com.example.todoflow.data.model.RefreshRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class ApiClient(private val sessionManager: SessionManager) {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private sealed interface RefreshOutcome {
        data class Success(val token: String, val refreshToken: String?) : RefreshOutcome
        /** Refresh token is invalid/expired/revoked: the user must log in again. */
        data object Rejected : RefreshOutcome
        /** Network/server problem: keep the session and try again later. */
        data object Unavailable : RefreshOutcome
    }

    // Plain client (no authenticator) used only to call the refresh endpoint.
    private val refreshClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun requestTokenRefresh(): RefreshOutcome {
        val refreshToken = sessionManager.refreshToken
        if (refreshToken.isNullOrBlank()) return RefreshOutcome.Rejected
        return try {
            val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(sessionManager.baseUrl + "api/v1/auth/refresh")
                .header("Accept", "application/json")
                .post(body)
                .build()
            refreshClient.newCall(request).execute().use { resp ->
                when {
                    resp.isSuccessful -> {
                        val parsed = json.decodeFromString(
                            AuthResponse.serializer(),
                            resp.body?.string().orEmpty()
                        )
                        val newToken = parsed.token
                        if (newToken.isNullOrBlank()) RefreshOutcome.Rejected
                        else RefreshOutcome.Success(newToken, parsed.refreshToken)
                    }
                    resp.code == 401 || resp.code == 403 -> RefreshOutcome.Rejected
                    else -> RefreshOutcome.Unavailable
                }
            }
        } catch (_: Exception) {
            RefreshOutcome.Unavailable
        }
    }

    private val refreshLock = Any()

    /** Reads the `exp` claim (epoch seconds) from a JWT without verifying it. */
    private fun tokenExpiryEpochSec(token: String): Long? = try {
        val payload = token.split(".").getOrNull(1) ?: return null
        val decoded = android.util.Base64.decode(
            payload,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
        json.parseToJsonElement(String(decoded)).jsonObject["exp"]?.jsonPrimitive?.longOrNull
    } catch (_: Exception) {
        null
    }

    /** True if the token is expired or will expire within [skewSeconds]. Unparseable counts as expired. */
    private fun isExpiredOrExpiring(token: String, skewSeconds: Long = 60): Boolean {
        val exp = tokenExpiryEpochSec(token) ?: return true
        return exp - skewSeconds <= System.currentTimeMillis() / 1000
    }

    /**
     * Returns a usable access token different from [usedToken], refreshing it if nobody else
     * already did. Returns null if refreshing failed (session cleared if the server rejected it).
     */
    private fun refreshAccessToken(usedToken: String): String? = synchronized(refreshLock) {
        val current = sessionManager.token
        // Another request already refreshed the token while we were waiting for the lock.
        if (!current.isNullOrBlank() && current != usedToken) return current
        when (val outcome = requestTokenRefresh()) {
            is RefreshOutcome.Success -> {
                sessionManager.updateAccessToken(outcome.token, outcome.refreshToken)
                outcome.token
            }
            RefreshOutcome.Rejected -> {
                sessionManager.expireSession()
                null
            }
            RefreshOutcome.Unavailable -> null
        }
    }

    private fun Request.withToken(token: String?): Request {
        val builder = newBuilder().header("Accept", "application/json")
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        return builder.build()
    }

    /**
     * Adds the bearer token and keeps it fresh:
     *  - refreshes proactively when the (15 minute) access token is expired/expiring, and
     *  - retries once after a refresh when the server rejects the token. The backend answers an
     *    expired JWT with 403 (not 401), so both are handled; a 403 only triggers a refresh when
     *    the token is actually expired, so genuine "forbidden" responses don't cause refresh loops.
     */
    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        if (original.url.encodedPath.contains("/api/v1/auth/")) {
            return@Interceptor chain.proceed(original.withToken(null))
        }

        var token = sessionManager.token
        if (!token.isNullOrBlank() && isExpiredOrExpiring(token)) {
            token = refreshAccessToken(token) ?: token
        }

        val response = chain.proceed(original.withToken(token))
        val rejected = response.code == 401 ||
            (response.code == 403 && !token.isNullOrBlank() && isExpiredOrExpiring(token, skewSeconds = 0))
        if (rejected && !token.isNullOrBlank()) {
            val refreshed = refreshAccessToken(token)
            if (refreshed != null && refreshed != token) {
                response.close()
                return@Interceptor chain.proceed(original.withToken(refreshed))
            }
        }
        response
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var cachedBaseUrl: String? = null
    private var cachedApi: TodoFlowApi? = null

    fun getApi(): TodoFlowApi {
        val currentBaseUrl = sessionManager.baseUrl
        val existing = cachedApi
        if (existing != null && cachedBaseUrl == currentBaseUrl) {
            return existing
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(currentBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val api = retrofit.create(TodoFlowApi::class.java)
        cachedBaseUrl = currentBaseUrl
        cachedApi = api
        return api
    }
}
