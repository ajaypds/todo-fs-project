package com.example.todoflow.data.remote

import com.example.todoflow.data.local.SessionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Live updates: connects to the backend's STOMP endpoint (`/ws`) and subscribes to the user's task
 * events, so changes made on the web or another device show up right away. Reconnects with backoff
 * while started; callers start it when the app is visible and stop it when it goes to the background.
 */
class RealtimeClient(
    private val sessionManager: SessionManager,
    private val apiClient: ApiClient
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // long-lived connection
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val _connected = MutableStateFlow(false)

    /** True while the live connection is up. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Raw JSON of each task event (TASK_CREATED / TASK_UPDATED / TASK_DELETED). */
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var job: Job? = null

    @Synchronized
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            var backoffMs = INITIAL_BACKOFF_MS
            while (isActive) {
                val wasConnected = runSession()
                backoffMs = if (wasConnected) INITIAL_BACKOFF_MS else minOf(backoffMs * 2, MAX_BACKOFF_MS)
                delay(backoffMs)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        _connected.value = false
    }

    /** Runs one connection until it closes. Returns true if the STOMP session was established. */
    private suspend fun runSession(): Boolean {
        val token = withContext(Dispatchers.IO) { apiClient.validAccessToken() } ?: return false
        val userId = apiClient.currentUserId() ?: return false
        val request = try {
            Request.Builder().url(webSocketUrl(sessionManager.baseUrl)).build()
        } catch (_: IllegalArgumentException) {
            return false
        }

        val closed = CompletableDeferred<Unit>()
        var established = false
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(StompFrame.connect(token))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = StompFrame.parse(text) ?: return
                when (frame.command) {
                    "CONNECTED" -> {
                        established = true
                        _connected.value = true
                        webSocket.send(StompFrame.subscribe("tasks", "/topic/users/$userId/tasks"))
                    }
                    "MESSAGE" -> _events.tryEmit(frame.body)
                    "ERROR" -> webSocket.close(1000, "stomp error")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closed.complete(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                closed.complete(Unit)
            }
        })

        try {
            closed.await()
        } finally {
            _connected.value = false
            socket.cancel()
        }
        return established
    }

    companion object {
        private const val INITIAL_BACKOFF_MS = 2_000L
        private const val MAX_BACKOFF_MS = 60_000L

        /** `https://host/` -> `wss://host/ws/websocket` (SockJS' raw WebSocket transport). */
        fun webSocketUrl(baseUrl: String): String {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            return base.replaceFirst("http", "ws") + "ws/websocket"
        }
    }
}
