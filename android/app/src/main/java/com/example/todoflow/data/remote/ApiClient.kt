package com.example.todoflow.data.remote

import com.example.todoflow.data.local.SessionManager
import com.example.todoflow.data.model.AuthResponse
import com.example.todoflow.data.model.RefreshRequest
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
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

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()

        val token = sessionManager.token
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        builder.header("Accept", "application/json")

        chain.proceed(builder.build())
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

    /**
     * On HTTP 401 the (15 minute) access token has expired: silently exchange the refresh token
     * for a new one and retry the request once.
     */
    private val tokenAuthenticator = object : Authenticator {
        private val lock = Any()

        override fun authenticate(route: Route?, response: Response): Request? {
            val path = response.request.url.encodedPath
            if (path.contains("/api/v1/auth/")) return null
            val usedHeader = response.request.header("Authorization") ?: return null
            if (response.priorResponse != null) return null // already retried once

            synchronized(lock) {
                val current = sessionManager.token
                // Another request already refreshed the token while we were waiting.
                if (!current.isNullOrBlank() && "Bearer $current" != usedHeader) {
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer $current")
                        .build()
                }
                return when (val outcome = requestTokenRefresh()) {
                    is RefreshOutcome.Success -> {
                        sessionManager.updateAccessToken(outcome.token, outcome.refreshToken)
                        response.request.newBuilder()
                            .header("Authorization", "Bearer ${outcome.token}")
                            .build()
                    }
                    RefreshOutcome.Rejected -> {
                        sessionManager.clearSession()
                        null
                    }
                    RefreshOutcome.Unavailable -> null
                }
            }
        }
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
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
