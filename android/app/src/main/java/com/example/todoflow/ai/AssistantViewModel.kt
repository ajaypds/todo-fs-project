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

/** EXPIRED: a change that was never applied and whose chat was closed (can't be applied any more). */
enum class ProposalStatus { NONE, PENDING, APPLYING, APPLIED, DISCARDED, EXPIRED }

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
    val resultNote: String? = null,
    /** Changes of a chat restored from the history (shown as text only). */
    val changes: List<StoredChange> = emptyList(),
    /** What the app looked up in the user's data for this answer: real data, shown instead of the model's words. */
    val lookups: String? = null
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
    val deviceRamGb: Int = 0,
    /** Where past chats are kept; null disables the history. */
    private val chatHistory: ChatHistoryStore? = null
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
    private var currentChatId = System.currentTimeMillis()

    // ---- history ---------------------------------------------------------------------------------

    private val _chats = MutableStateFlow<List<StoredChat>>(emptyList())
    val chats: StateFlow<List<StoredChat>> = _chats.asStateFlow()

    /** Asks for the current chat to be saved; bursts of changes are merged into one write. */
    private val saveRequests = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

    init {
        if (chatHistory != null) {
            viewModelScope.launch(Dispatchers.IO) {
                for (ignored in saveRequests) {
                    val snapshot = snapshotChat()
                    if (snapshot != null) {
                        try {
                            chatHistory.save(snapshot)
                        } catch (e: Exception) {
                            android.util.Log.w("AssistantViewModel", "Could not save the chat", e)
                        }
                    }
                }
            }
        }
    }

    private fun snapshotChat(): StoredChat? {
        val msgs = _messages.value
        val first = msgs.firstOrNull { it is UserMessage } as? UserMessage ?: return null
        val stored = msgs.map { m ->
            when (m) {
                is UserMessage -> StoredMessage("user", m.text)
                is AssistantMessage -> StoredMessage(
                    role = "assistant",
                    text = m.text,
                    changes = if (m.proposals.isNotEmpty()) m.proposals.map { StoredChange(it.summary, it.destructive) } else m.changes,
                    problems = m.problems,
                    status = m.status.name,
                    raw = m.raw,
                    note = m.resultNote,
                    lookups = m.lookups
                )
            }
        }
        return StoredChat(currentChatId, first.text.replace('\n', ' ').take(48), System.currentTimeMillis(), stored)
    }

    private fun save() {
        if (chatHistory != null) saveRequests.trySend(Unit)
    }

    /** Reloads the list of past chats (call when the history screen opens). */
    fun refreshChats() {
        val store = chatHistory ?: return
        viewModelScope.launch {
            _chats.value = withContext(Dispatchers.IO) { runCatching { store.load() }.getOrDefault(emptyList()) }
        }
    }

    /** Continue a past chat. Changes it had proposed but not applied can no longer be applied. */
    fun openChat(id: Long) {
        if (_busy.value) return
        val chat = _chats.value.firstOrNull { it.id == id } ?: return
        currentChatId = chat.id
        _messages.value = chat.messages.map { m ->
            if (m.role == "user") UserMessage(nextId++, m.text)
            else AssistantMessage(
                id = nextId++,
                text = m.text,
                problems = m.problems,
                status = when (val st = runCatching { ProposalStatus.valueOf(m.status) }.getOrDefault(ProposalStatus.NONE)) {
                    ProposalStatus.PENDING, ProposalStatus.APPLYING -> ProposalStatus.EXPIRED
                    else -> st
                },
                raw = m.raw,
                resultNote = m.note,
                changes = m.changes,
                lookups = m.lookups
            )
        }
    }

    fun deleteChat(id: Long) {
        val store = chatHistory ?: return
        if (id == currentChatId) {
            _messages.value = emptyList()
            currentChatId = System.currentTimeMillis()
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { store.delete(id) } }
            refreshChats()
        }
    }

    val hasUsableModel: Boolean
        get() = session.aiActiveModel.value?.let { store.isInstalled(it) } == true

    fun newChat() {
        if (_busy.value) return
        _messages.value = emptyList()
        currentChatId = System.currentTimeMillis()
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
                val history = PromptBuilder.examples(today()) + recentTurns()
                val queries = QueryRunner(repository, today)

                // One model call; if it ignores the JSON format, ask once more insisting on it
                suspend fun ask(turns: List<Turn>, message: String): Pair<String, ParsedReply> {
                    var out = runtime.complete(system, turns, message)
                    var p = ActionParser.parse(out)
                    if (!p.hadJson) {
                        out = runtime.complete(system, turns, message + RETRY_HINT)
                        p = ActionParser.parse(out)
                    }
                    return out to p
                }

                var (raw, parsed) = ask(history, input)

                // A question about the user's data must be answered from the app's data. If the model tries
                // to answer from memory instead, send it back once to use a look-up tool.
                var unchecked = false
                if (parsed.hadJson && parsed.actions.isEmpty() && DataQuestions.looksLikeDataQuestion(input)) {
                    val again = ask(history, input + DATA_HINT)
                    raw = again.first
                    parsed = again.second
                    unchecked = parsed.hadJson && parsed.actions.isEmpty()
                }
                val lookupResults = mutableListOf<String>()

                // Look-ups: run them in the app, give the model the real result and let it continue
                val conversation = history.toMutableList()
                var rounds = 0
                while (parsed.hadJson && rounds < MAX_LOOKUP_ROUNDS) {
                    val lookups = parsed.actions.filterIsInstance<AssistantQuery>()
                    if (lookups.isEmpty()) break
                    val results = queries.run(lookups)
                    lookupResults += results
                    if (DataQuestions.looksLikeDataQuestion(input)) {
                        // A plain question: the app's data is the answer, no need to ask the model to retell it
                        parsed = parsed.copy(actions = parsed.actions.filter { it !is AssistantQuery })
                        break
                    }
                    conversation += Turn(if (rounds == 0) input else LOOKUP_FOLLOW_UP, raw)
                    val follow = "Results from the app (real data):\n$results\n\nNow answer my request: $input"
                    val next = ask(conversation, follow)
                    raw = next.first
                    parsed = next.second
                    rounds++
                }
                if (parsed.actions.any { it is AssistantQuery }) {
                    parsed = parsed.copy(
                        actions = parsed.actions.filter { it !is AssistantQuery },
                        problems = parsed.problems + "I couldn't finish looking that up"
                    )
                }
                val resolution = ActionResolver.resolve(parsed, projects, tasks)
                val reply = if (!parsed.hadJson) {
                    "The model didn't answer in a form I can use, so nothing was changed. " +
                        "Try rephrasing, or pick a larger model in the model manager.\n\nIt said: " + parsed.reply.take(300)
                } else if (lookupResults.isNotEmpty() && resolution.proposals.isEmpty()) {
                    // The answer is the app's own data, not the model's wording of it
                    "Here's what I found in your data:"
                } else if (unchecked) {
                    parsed.reply.ifBlank { "I'm not sure." } +
                        "\n\n(I couldn't check this against your data, so it may be wrong. Try asking again, for example “list my projects”.)"
                } else parsed.reply.ifBlank {
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
                        // A prose answer must not be replayed to the model as if it were the right format
                        raw = if (parsed.hadJson) raw else "",
                        lookups = lookupResults.takeIf { it.isNotEmpty() }?.let { QueryRunner.forDisplay(it.joinToString("\n\n")) }
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
                    if (user != null && m.raw.isNotBlank()) turns += Turn(user, replayedAnswer(m))
                    pendingUser = null
                }
            }
        }
        return turns.takeLast(MAX_HISTORY_TURNS)
    }

    /**
     * What the model is shown for one of its earlier answers. Its words ("I've created the project") are only
     * true if the user applied the change, so an answer that was discarded, ignored or only partly applied is
     * replaced by a note saying so. Otherwise the model believes it did things that never happened.
     */
    private fun replayedAnswer(m: AssistantMessage): String {
        val hadChanges = m.proposals.isNotEmpty() || m.changes.isNotEmpty()
        return when {
            hadChanges && m.status != ProposalStatus.APPLIED ->
                """{"reply":"[The user did not apply this, so nothing was changed.]","actions":[]}"""
            hadChanges && m.resultNote?.contains("Could not apply") == true ->
                """{"reply":${kotlinx.serialization.json.JsonPrimitive("[Only partly applied: " + m.resultNote + "]")},"actions":[]}"""
            else -> m.raw
        }
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

    private fun add(m: ChatMessage) {
        _messages.update { it + m }
        save()
    }

    private fun replace(id: Long, f: (AssistantMessage) -> AssistantMessage) {
        _messages.update { list -> list.map { if (it.id == id && it is AssistantMessage) f(it) else it } }
        save()
    }

    companion object {
        private const val MAX_HISTORY_TURNS = 4
        private const val MAX_LOOKUP_ROUNDS = 3
        private const val DATA_HINT = "\n\nThis is a question about my own data. Use a look-up tool (list_projects, list_tasks, ...) and do not answer from memory. Answer with ONLY the JSON object, starting with {"
        private const val LOOKUP_FOLLOW_UP = "(looking things up)"
        private const val RETRY_HINT = "\n\nAnswer with ONLY the JSON object, starting with {"
    }
}
