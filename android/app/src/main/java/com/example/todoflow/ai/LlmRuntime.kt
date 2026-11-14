package com.example.todoflow.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

sealed interface RuntimeState {
    data object Unloaded : RuntimeState
    data class Loading(val model: String) : RuntimeState
    data class Ready(val model: String, val backend: String) : RuntimeState
    data class Failed(val message: String) : RuntimeState
}

/** Runs a language model on the device. The app talks to this interface; [LiteRtRuntime] is the real one. */
interface LlmRuntime {
    val state: StateFlow<RuntimeState>

    /** Loads [modelFile] (replacing any loaded model). Throws if the model can't be started. */
    suspend fun load(modelFile: File, useGpu: Boolean)

    /** One question/answer: [system] sets the behaviour, [history] gives earlier turns, [user] is the new message. */
    suspend fun complete(system: String, history: List<Turn>, user: String): String

    /** Frees the model's memory. */
    suspend fun unload()
}

/**
 * [LlmRuntime] on top of LiteRT-LM. One thread owns the engine (the library requires it), the model is
 * loaded off the main thread, and it is released again after a few idle minutes to give the memory back.
 */
class LiteRtRuntime(private val context: Context) : LlmRuntime {

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "litertlm") }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private var engine: Engine? = null
    private var loadedKey: String? = null
    private var idleJob: Job? = null

    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.Unloaded)
    override val state: StateFlow<RuntimeState> = _state.asStateFlow()

    override suspend fun load(modelFile: File, useGpu: Boolean) = mutex.withLock {
        val key = modelFile.absolutePath + "|" + useGpu
        if (engine != null && loadedKey == key) return@withLock
        val name = ModelCatalog.displayName(modelFile.name)
        withContext(dispatcher) { closeEngine() }
        _state.value = RuntimeState.Loading(name)
        try {
            val (created, backendName) = withContext(dispatcher) { createEngine(modelFile, useGpu) }
            engine = created
            loadedKey = key
            _state.value = RuntimeState.Ready(name, backendName)
            scheduleIdleUnload()
        } catch (e: CancellationException) {
            _state.value = RuntimeState.Unloaded
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "Could not load the model", e)
            _state.value = RuntimeState.Failed(e.message ?: "Could not load the model")
            throw (e as? Exception ?: RuntimeException(e))
        }
    }

    private fun createEngine(modelFile: File, useGpu: Boolean): Pair<Engine, String> {
        fun start(backend: Backend): Engine {
            val e = Engine(EngineConfig(modelPath = modelFile.absolutePath, backend = backend, cacheDir = context.cacheDir.path))
            try {
                e.initialize()
            } catch (t: Throwable) {
                runCatching { e.close() }
                throw t
            }
            return e
        }
        if (useGpu) {
            try {
                return start(Backend.GPU()) to "GPU"
            } catch (t: Throwable) {
                Log.w(TAG, "GPU backend failed, falling back to the CPU", t)
            }
        }
        return start(Backend.CPU()) to "CPU"
    }

    override suspend fun complete(system: String, history: List<Turn>, user: String): String = mutex.withLock {
        val current = engine ?: error("No model is loaded")
        idleJob?.cancel()
        try {
            withContext(dispatcher) {
                val initial = history.flatMap { listOf(Message.user(it.user), Message.model(it.assistant)) }
                val config = ConversationConfig(
                    systemInstruction = Contents.of(system),
                    initialMessages = initial,
                    samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.2, seed = 0)
                )
                val conversation = current.createConversation(config)
                val out = StringBuilder()
                try {
                    conversation.sendMessageAsync(withModelHints(user)).collect { out.append(it.toString()) }
                } catch (e: CancellationException) {
                    runCatching { conversation.cancelProcess() }
                    throw e
                } finally {
                    runCatching { conversation.close() }
                }
                out.toString()
            }
        } finally {
            scheduleIdleUnload()
        }
    }

    /** Qwen3 "thinks" out loud by default, which is slow and useless here: its /no_think switch turns that off. */
    private fun withModelHints(user: String): String =
        if (loadedKey?.contains("qwen3", ignoreCase = true) == true) "$user /no_think" else user

    override suspend fun unload() = mutex.withLock {
        idleJob?.cancel()
        withContext(dispatcher) { closeEngine() }
        _state.value = RuntimeState.Unloaded
    }

    private fun closeEngine() {
        engine?.let { runCatching { it.close() } }
        engine = null
        loadedKey = null
    }

    private fun scheduleIdleUnload() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_UNLOAD_MS)
            unload()
        }
    }

    companion object {
        private const val TAG = "LiteRtRuntime"
        private const val IDLE_UNLOAD_MS = 5 * 60 * 1000L
    }
}
