package com.example.todoflow.ai

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.todoflow.data.local.SessionManager
import com.example.todoflow.data.local.TaskEntity
import com.example.todoflow.data.local.TaskFilterType
import com.example.todoflow.data.local.TodoDbHelper
import com.example.todoflow.data.local.TodoLocalDataSource
import com.example.todoflow.data.remote.ApiProvider
import com.example.todoflow.data.repository.FakeTodoFlowApi
import com.example.todoflow.data.repository.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDate

/** Replays canned model answers and records what the app asked. */
private class FakeRuntime : LlmRuntime {
    val answers = ArrayDeque<String>()
    val calls = mutableListOf<Triple<String, List<Turn>, String>>()
    var loaded: File? = null
    var failWith: String? = null
    override val state: StateFlow<RuntimeState> = MutableStateFlow(RuntimeState.Unloaded)
    override suspend fun load(modelFile: File, useGpu: Boolean) { loaded = modelFile }
    override suspend fun complete(system: String, history: List<Turn>, user: String): String {
        calls += Triple(system, history, user)
        failWith?.let { throw IllegalStateException(it) }
        return answers.removeFirst()
    }
    override suspend fun unload() { loaded = null }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AssistantViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: TodoDbHelper
    private lateinit var local: TodoLocalDataSource
    private lateinit var repo: TaskRepository
    private lateinit var runtime: FakeRuntime
    private lateinit var store: ModelStore
    private lateinit var vm: AssistantViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = TodoDbHelper(context, "assistant-${java.util.UUID.randomUUID()}.db")
        local = TodoLocalDataSource(db)
        repo = TaskRepository(local, ApiProvider { FakeTodoFlowApi() }, CoroutineScope(Job().apply { cancel() }))
        runtime = FakeRuntime()
        store = ModelStore(tmp.newFolder("models"))
        val session = SessionManager(context).apply { setAiActiveModel(null) }
        val downloader = ModelDownloader(OkHttpClient(), store, CoroutineScope(Job()), { "" })
        vm = AssistantViewModel(repo, session, store, downloader, runtime, today = { LocalDate.of(2026, 11, 14) }, deviceRamGb = 16)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun installModel() {
        store.import(ByteArrayInputStream(ByteArray(10)), "Qwen3-0.6B.litertlm")
        vm.refreshInstalled()
    }

    private fun send(text: String) {
        vm.send(text)
        runBlocking { withTimeout(10_000) { vm.busy.first { !it } } }
    }

    private fun tasks() = runBlocking { local.getTasks(TaskFilterType.ALL) }
    private fun last() = vm.messages.value.last() as AssistantMessage

    @Test
    fun withoutAModelTheChatPointsToTheModelManager() {
        send("add milk")
        assertTrue(last().text.contains("model manager"))
        assertTrue(runtime.calls.isEmpty())
    }

    @Test
    fun theOnlyInstalledModelIsSelectedAutomatically() {
        installModel()
        assertEquals("Qwen3-0.6B.litertlm", vm.activeModel.value)
        assertTrue(vm.hasUsableModel)
    }

    @Test
    fun requestBecomesReviewableChangesAndApplyingThemCreatesTheTask() {
        installModel()
        runtime.answers += """{"reply":"On it","actions":[
            {"op":"create_project","name":"Home"},
            {"op":"create_task","title":"Buy milk","due":"2026-11-15","priority":"high","project":"Home","subtasks":["Oat","Whole"]}]}"""

        send("add buy milk tomorrow, high priority, in a new project Home")
        val reply = last()
        assertEquals("On it", reply.text)
        assertEquals(ProposalStatus.PENDING, reply.status)
        assertEquals(2, reply.proposals.size)
        assertTrue("nothing is changed before the user applies", tasks().isEmpty())
        // The model saw today's date and the chosen model was loaded
        assertTrue(runtime.calls.single().first.contains("Today is Saturday, 2026-11-14"))
        assertEquals("Qwen3-0.6B.litertlm", runtime.loaded?.name)

        vm.apply(reply.id)
        runBlocking { withTimeout(10_000) { while (last().status != ProposalStatus.APPLIED) kotlinx.coroutines.delay(20) } }
        assertEquals("2 changes applied", last().resultNote)

        val task = tasks().single()
        assertEquals("Buy milk", task.title)
        assertEquals("2026-11-15T23:59:59Z", task.dueDate)
        assertEquals(1, task.priority)
        assertEquals("Home", task.projectName)
        assertEquals(listOf("Oat", "Whole"), task.subtasks.map { it.title })
    }

    @Test
    fun followUpsGetTheEarlierExchangeAndEditTheSameTask() {
        installModel()
        runBlocking { local.upsertTasks(listOf(TaskEntity("t1", "Pay rent"))) }
        runtime.answers += """{"reply":"Found it","actions":[]}"""
        runtime.answers += """{"reply":"Done","actions":[{"op":"update_task","task":1,"priority":"high","due":"2026-11-20"}]}"""

        send("what is my first task?")
        send("make it high priority and due on the 20th")

        val second = runtime.calls[1]
        // The worked examples come first, then the real earlier exchange
        val examples = PromptBuilder.examples(LocalDate.of(2026, 11, 14))
        assertEquals(examples + Turn("what is my first task?", """{"reply":"Found it","actions":[]}"""), second.second)
        assertEquals("make it high priority and due on the 20th", second.third)

        vm.apply(last().id)
        runBlocking { withTimeout(10_000) { while (last().status != ProposalStatus.APPLIED) kotlinx.coroutines.delay(20) } }
        val task = tasks().single { it.id == "t1" }
        assertEquals(1, task.priority)
        assertEquals("2026-11-20T23:59:59Z", task.dueDate)
        assertEquals("Pay rent", task.title) // untouched fields stay
    }

    @Test
    fun deletesNeedApplyAndDiscardChangesNothing() {
        installModel()
        runBlocking { local.upsertTasks(listOf(TaskEntity("t1", "Old"), TaskEntity("t2", "Keep"))) }
        runtime.answers += """{"reply":"Deleting","actions":[{"op":"delete_task","task":1}]}"""
        send("delete old")
        val reply = last()
        assertTrue(reply.proposals.single().destructive)

        vm.discard(reply.id)
        assertEquals(ProposalStatus.DISCARDED, last().status)
        assertEquals(2, tasks().size)

        // Applying a discarded message does nothing
        vm.apply(reply.id)
        assertEquals(ProposalStatus.DISCARDED, last().status)
        assertEquals(2, tasks().size)
    }

    @Test
    fun aMistakeByTheModelIsExplainedNotApplied() {
        installModel()
        runtime.answers += """{"reply":"Hmm","actions":[{"op":"delete_task","task":42}]}"""
        send("delete task 42")
        assertTrue(last().proposals.isEmpty())
        assertTrue(last().problems.single().contains("#42"))
        assertEquals(ProposalStatus.NONE, last().status)
    }

    @Test
    fun runtimeFailuresBecomeAChatMessage() {
        installModel()
        runtime.failWith = "out of memory"
        send("add milk")
        assertTrue(last().text.contains("out of memory"))
        assertFalse(vm.busy.value)
        assertNotNull(vm.messages.value.firstOrNull { it is UserMessage })
    }

    @Test
    fun aProseAnswerIsRetriedOnceAndTheSecondTryIsUsed() {
        installModel()
        runtime.answers += "I'll help you with that task."
        runtime.answers += """{"reply":"Added","actions":[{"op":"create_task","title":"Buy milk","due":"2026-11-15"}]}"""

        send("Add buy milk for tomorrow")

        assertEquals(2, runtime.calls.size)
        assertTrue(runtime.calls[1].third.endsWith("Answer with ONLY the JSON object, starting with {"))
        assertEquals(1, last().proposals.size)
        assertEquals("Added", last().text)
    }

    @Test
    fun ifTheModelNeverUsesTheFormatTheUserIsToldAndItsProseIsNotReplayed() {
        installModel()
        runtime.answers += "I'll help you with that task."
        runtime.answers += "Sure, happy to help!"
        send("Add buy milk for tomorrow")

        val reply = last()
        assertTrue(reply.proposals.isEmpty())
        assertTrue(reply.text.contains("didn't answer in a form I can use"))
        assertTrue(reply.text.contains("Sure, happy to help!"))
        assertEquals("", reply.raw)

        runtime.answers += """{"reply":"ok","actions":[]}"""
        send("hello")
        // The earlier prose answer is not part of the history the model sees
        assertEquals(PromptBuilder.examples(LocalDate.of(2026, 11, 14)), runtime.calls.last().second)
    }

    @Test
    fun deletingTheActiveModelClearsTheSelection() {
        installModel()
        vm.deleteModel("Qwen3-0.6B.litertlm")
        runBlocking { withTimeout(5_000) { vm.installed.first { it.isEmpty() } } }
        assertEquals(null, vm.activeModel.value)
        assertFalse(vm.hasUsableModel)
    }

    @Test
    fun importingFromAPickerAddsAndSelectsTheModel() {
        vm.importModel("Picked.litertlm") { ByteArrayInputStream(ByteArray(2048)) }
        runBlocking { withTimeout(5_000) { vm.activeModel.first { it != null } } }
        assertEquals("Picked.litertlm", vm.activeModel.value)
        vm.importModel("readme.txt") { ByteArrayInputStream(ByteArray(2)) }
        runBlocking { withTimeout(5_000) { vm.modelNotice.first { it != null } } }
        assertTrue(vm.modelNotice.value!!.contains(".litertlm"))
    }
}
