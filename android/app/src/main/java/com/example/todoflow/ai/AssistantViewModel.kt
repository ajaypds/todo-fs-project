package com.example.todoflow.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todoflow.data.local.SessionManager
import com.example.todoflow.data.local.TaskFilterType
import com.example.todoflow.data.repository.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.time.LocalDate

enum class ProposalStatus { NONE, PENDING, APPLYING, APPLIED, DISCARDED }

sealed interface ChatMessage {
    val id: Long
}

data class UserMessage(override val id: Long, val text: String) : ChatMessage

data class AssistantMessage(
    override val id: Long,
    val text: String,
    val proposals: List<Proposal> = emptyList(),
    val problems: List<String> = emptyList(),
    val status: ProposalStatus = ProposalStatus.NONE,
    /** What the model actually wrote; replayed to it on the next turn so follow-ups make sense. */
    val raw: String = "",
    val resultNote: String? = null
) : ChatMessage

/**
 * Drives the AI assistant: the chat (prompt → model → reviewed changes → backend) and the management
 * of the model files (download, import, select, delete).
 */
class AssistantViewModel(
    private val repository: TaskRepository,
    private val session: SessionManager,
    val store: ModelStore,
    val downloader: ModelDownloader,
    private val runtime: LlmRuntime,
    private val executor: ActionExecutor = ActionExecutor(repository),
    private val today: () -> LocalDate = { LocalDate.now() },
    val deviceRamGb: Int = 0
) : ViewModel() {

    // ---- models -------------------------------------------------------------------------------------

    private val _installed = MutableStateFlow(store.list())
    val installed: StateFlow<List<InstalledModel>> = _installed.asStateFlow()

    val activeModel: StateFlow<String?> = session.aiActiveModel
    val useGpu: StateFlow<Boolean> = session.aiUseGpu
    val downloads: StateFlow<Map<String, DownloadState>> = downloader.states
    val runtimeState: StateFlow<RuntimeState> = runtime.state

    private val _modelNotice = MutableStateFlow<String?>(null)
    val modelNotice: StateFlow<String?> = _modelNotice.asStateFlow()

    private val _importing = MutableStateFlow<String?>(null)
    val importing: StateFlow<String?> = _importing.asStateFlow()

    var huggingFaceToken: String
        get() = session.huggingFaceToken
        set(value) { session.huggingFaceToken = value }

    val recommended: ModelSpec get() = ModelCatalog.recommended(deviceRamGb)

    init {
        // A finished download shows up as a new file: refresh the list and use it if nothing is selected yet
        viewModelScope.launch {
            var previous = downloader.states.value.keys
            downloader.states.collect { now ->
                if (previous.any { it !in now.keys }) refreshInstalled()
                previous = now.keys
            }
        }
        autoSelectIfOnlyOne()
    }

    fun refreshInstalled() {
        _installed.value = store.list()
        // The selected model was removed behind our back
        val active = session.aiActiveModel.value
        if (active != null && !store.isInstalled(active)) session.setAiActiveModel(null)
        autoSelectIfOnlyOne()
    }

    private fun autoSelectIfOnlyOne() {
        val list = _installed.value
        if (session.aiActiveModel.value == null && list.size == 1) session.setAiActiveModel(list.first().fileName)
    }

    fun selectModel(fileName: String) {
        if (!store.isInstalled(fileName)) return
        session.setAiActiveModel(fileName)
        viewModelScope.launch { runCatching { runtime.unload() } }
    }

    fun deleteModel(fileName: String) {
        viewModelScope.launch {
            if (session.aiActiveModel.value == fileName) {
                session.setAiActiveModel(null)
                runCatching { runtime.unload() }
            }
            withContext(Dispatchers.IO) { store.delete(fileName) }
            refreshInstalled()
        }
    }

    fun setUseGpu(enabled: Boolean) {
        session.setAiUseGpu(enabled)
        viewModelScope.launch { runCatching { runtime.unload() } }
    }

    fun download(spec: ModelSpec) {
        if (spec.gated && huggingFaceToken.isBlank()) {
            _modelNotice.value = "${spec.name} needs a Hugging Face token. Paste one below (and accept the model's licence on huggingface.co first)."
            return
        }
        _modelNotice.value = null
        downloader.start(spec.fileName, spec.url, spec.approxBytes)
    }

    /** Download a `.litertlm` file from any address, e.g. another model from litert-community. */
    fun downloadFromUrl(url: String) {
        val clean = url.trim()
        val fileName = clean.substringBefore('?').substringAfterLast('/')
        if (!clean.startsWith("https://") || !fileName.endsWith(ModelStore.EXTENSION)) {
            _modelNotice.value = "Enter an https:// link that ends in .litertlm"
            return
        }
        _modelNotice.value = null
        downloader.start(fileName, clean)
    }

    fun cancelDownload(fileName: String) = downloader.cancel(fileName)

    fun clearNotice() { _modelNotice.value = null }

    /** Copies a model file picked by the user into the app. [open] provides the file's contents. */
    fun importModel(fileName: String, open: () -> InputStream?) {
        viewModelScope.launch {
            _modelNotice.value = null
            _importing.value = "Importing ${fileName}…"
            try {
                val model = withContext(Dispatchers.IO) {
                    val input = open() ?: throw java.io.IOException("Could not open the file")
                    input.use { store.import(it, fileName) { bytes -> _importing.value = "Importing… ${bytes / 1_000_000} MB" } }
                }
                refreshInstalled()
                if (session.aiActiveModel.value == null) session.setAiActiveModel(model.fileName)
            } catch (e: Exception) {
                _modelNotice.value = e.message ?: "Import failed"
            } finally {
                _importing.value = null
            }
        }
    }

    // ---- chat ---------------------------------------------------------------------------------------

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private var nextId = 1L

    val hasUsableModel: Boolean
        get() = session.aiActiveModel.value?.let { store.isInstalled(it) } == true

    fun newChat() {
        if (_busy.value) return
        _messages.value = emptyList()
    }

    fun send(text: String) {
        val input = text.trim()
        if (input.isEmpty() || _busy.value) return
        val modelName = session.aiActiveModel.value?.takeIf { store.isInstalled(it) }
        add(UserMessage(nextId++, input))
        if (modelName == null) {
            add(AssistantMessage(nextId++, "No model is selected yet. Open the model manager (the chip icon) to download or import one."))
            return
        }
        viewModelScope.launch {
            _busy.value = true
            try {
                runtime.load(store.fileFor(modelName), session.aiUseGpu.value)
                val tasks = repository.observeTasks(TaskFilterType.ALL).first()
                val projects = repository.observeProjects().first()
                val system = PromptBuilder.system(today(), tasks, projects)
                val raw = runtime.complete(system, recentTurns(), input)
                val parsed = ActionParser.parse(raw)
                val resolution = ActionResolver.resolve(parsed, PromptBuilder.contextTasks(tasks), projects)
                val reply = parsed.reply.ifBlank {
                    when {
                        resolution.proposals.isNotEmpty() -> "Here is what I would do:"
                        resolution.problems.isNotEmpty() -> "I couldn't work that out."
                        else -> "I'm not sure how to help with that."
                    }
                }
                add(
                    AssistantMessage(
                        id = nextId++,
                        text = reply,
                        proposals = resolution.proposals,
                        problems = resolution.problems,
                        status = if (resolution.proposals.isEmpty()) ProposalStatus.NONE else ProposalStatus.PENDING,
                        raw = raw
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                add(AssistantMessage(nextId++, "Something went wrong: ${e.message ?: "the model could not answer"}"))
            } finally {
                _busy.value = false
            }
        }
    }

    /** Last few exchanges, replayed to the model for follow-up requests. */
    private fun recentTurns(): List<Turn> {
        val turns = mutableListOf<Turn>()
        var pendingUser: String? = null
        // The user message just added is the new request, so leave it out of the history
        for (m in _messages.value.dropLast(1)) {
            when (m) {
                is UserMessage -> pendingUser = m.text
                is AssistantMessage -> {
                    val user = pendingUser
                    if (user != null && m.raw.isNotBlank()) turns += Turn(user, m.raw)
                    pendingUser = null
                }
            }
        }
        return turns.takeLast(MAX_HISTORY_TURNS)
    }

    fun apply(messageId: Long) {
        val message = _messages.value.firstOrNull { it.id == messageId } as? AssistantMessage ?: return
        if (message.status != ProposalStatus.PENDING) return
        replace(messageId) { it.copy(status = ProposalStatus.APPLYING) }
        viewModelScope.launch {
            val result = try {
                executor.execute(message.proposals)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExecutionResult(0, message.proposals.map { it.summary })
            }
            val note = buildString {
                append("${result.applied} change${if (result.applied == 1) "" else "s"} applied")
                if (result.failures.isNotEmpty()) append(". Could not apply: ${result.failures.joinToString("; ")}")
            }
            replace(messageId) { it.copy(status = ProposalStatus.APPLIED, resultNote = note) }
        }
    }

    fun discard(messageId: Long) {
        replace(messageId) { if (it.status == ProposalStatus.PENDING) it.copy(status = ProposalStatus.DISCARDED) else it }
    }

    private fun add(m: ChatMessage) = _messages.update { it + m }

    private fun replace(id: Long, f: (AssistantMessage) -> AssistantMessage) = _messages.update { list ->
        list.map { if (it.id == id && it is AssistantMessage) f(it) else it }
    }

    companion object {
        private const val MAX_HISTORY_TURNS = 4
    }
}
