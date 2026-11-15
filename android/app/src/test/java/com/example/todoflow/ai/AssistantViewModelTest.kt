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
    private lateinit var history: ChatHistoryStore

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
        history = ChatHistoryStore(java.io.File(tmp.newFolder("hist"), "chats.json"))
        vm = AssistantViewModel(repo, session, store, downloader, runtime, today = { LocalDate.of(2026, 11, 14) }, deviceRamGb = 16, chatHistory = history)
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
        runtime.answers += """{"reply":"Done","actions":[{"op":"update_task","task":"A","priority":"high","due":"2026-11-20"}]}"""

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
        runtime.answers += """{"reply":"Deleting","actions":[{"op":"delete_task","task":"A"}]}"""
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
        runtime.answers += """{"reply":"Hmm","actions":[{"op":"delete_task","task":"ZZ"}]}"""
        send("delete task 42")
        assertTrue(last().proposals.isEmpty())
        assertTrue(last().problems.single().contains("code ZZ"))
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
    fun deletingAProjectWithItsTasksDeletesTheTasksToo() {
        installModel()
        runBlocking {
            local.upsertProjects(listOf(com.example.todoflow.data.local.ProjectEntity("p1", "Test")))
            local.upsertTasks(listOf(
                TaskEntity("t1", "In test", projectId = "p1", projectName = "Test"),
                TaskEntity("t2", "Also in test", projectId = "p1", projectName = "Test"),
                TaskEntity("t3", "Elsewhere")
            ))
        }
        runtime.answers += """{"reply":"Deleting","actions":[{"op":"delete_project","project":"Test","with_tasks":true}]}"""
        send("delete test project with its tasks")
        assertEquals("Delete project “Test” and its 2 tasks", last().proposals.single().summary)

        vm.apply(last().id)
        runBlocking { withTimeout(10_000) { while (last().status != ProposalStatus.APPLIED) kotlinx.coroutines.delay(20) } }
        assertEquals(listOf("t3"), tasks().map { it.id })
        assertTrue(runBlocking { repo.observeProjects().first() }.none { it.name == "Test" })
    }

    @Test
    fun withoutTheOptionTheProjectsTasksMoveToTheInbox() {
        installModel()
        runBlocking {
            local.upsertProjects(listOf(com.example.todoflow.data.local.ProjectEntity("p1", "Test")))
            local.upsertTasks(listOf(TaskEntity("t1", "In test", projectId = "p1", projectName = "Test")))
        }
        runtime.answers += """{"reply":"ok","actions":[{"op":"delete_project","project":"Test"}]}"""
        send("delete test project")
        vm.apply(last().id)
        runBlocking { withTimeout(10_000) { while (last().status != ProposalStatus.APPLIED) kotlinx.coroutines.delay(20) } }
        val t = tasks().single()
        assertEquals(null, t.projectId)
    }

    @Test
    fun chatsAreSavedAndCanBeReopenedFromTheHistory() {
        installModel()
        runtime.answers += """{"reply":"Added","actions":[{"op":"create_task","title":"Buy milk"}]}"""
        send("add buy milk")
        runBlocking { withTimeout(10_000) { while (history.load().isEmpty()) kotlinx.coroutines.delay(20) } }
        runBlocking {
            withTimeout(10_000) { while (history.load().single().messages.size < 2) kotlinx.coroutines.delay(20) }
        }

        vm.newChat()
        assertTrue(vm.messages.value.isEmpty())
        vm.refreshChats()
        runBlocking { withTimeout(10_000) { vm.chats.first { it.isNotEmpty() } } }
        val saved = vm.chats.value.single()
        assertEquals("add buy milk", saved.title)

        vm.openChat(saved.id)
        val restored = vm.messages.value
        assertEquals("add buy milk", (restored[0] as UserMessage).text)
        val reply = restored[1] as AssistantMessage
        assertEquals("Added", reply.text)
        // What was only proposed can't be applied from a restored chat
        assertEquals(ProposalStatus.EXPIRED, reply.status)
        assertEquals(listOf("Create task “Buy milk”"), reply.changes.map { it.summary })

        // A restored chat continues: the model gets its earlier turn
        runtime.answers += """{"reply":"ok","actions":[]}"""
        send("thanks")
        // ...but its answer was never applied, so the model is told that instead of its own claim
        assertEquals(
            Turn("add buy milk", """{"reply":"[The user did not apply this, so nothing was changed.]","actions":[]}"""),
            runtime.calls.last().second.last()
        )

        vm.deleteChat(saved.id)
        runBlocking { withTimeout(10_000) { vm.chats.first { it.isEmpty() } } }
        assertTrue(vm.messages.value.isEmpty())
    }

    @Test
    fun aDiscardedAnswerIsNotReplayedAsIfItHadHappened() {
        installModel()
        runtime.answers += """{"reply":"I've created the Test project.","actions":[{"op":"create_project","name":"Test"}]}"""
        send("create a Test project")
        vm.discard(last().id)

        runtime.answers += """{"reply":"I'll create it.","actions":[{"op":"create_project","name":"Test"}]}"""
        send("create a Test project")

        val replayed = runtime.calls.last().second.last()
        assertEquals("create a Test project", replayed.user)
        assertTrue(replayed.assistant.contains("did not apply"))
        assertFalse(replayed.assistant.contains("I've created"))
        assertEquals("Create project “Test”", last().proposals.single().summary)
    }

    @Test
    fun theModelCanLookThingsUpAndThenAnswerWithTheRealData() {
        installModel()
        runBlocking {
            local.upsertProjects(listOf(com.example.todoflow.data.local.ProjectEntity("p1", "Garden")))
            local.upsertTasks(listOf(TaskEntity("t1", "Mow lawn", projectId = "p1", projectName = "Garden")))
        }
        runtime.answers += """{"reply":"Let me check.","actions":[{"op":"list_projects"}]}"""
        runtime.answers += """{"reply":"You have one project: Garden (1 task).","actions":[]}"""

        send("which projects do I have?")

        assertEquals(2, runtime.calls.size)
        val second = runtime.calls[1]
        assertTrue(second.third.contains("Results from the app"))
        assertTrue(second.third.contains("Garden (1 task)"))
        assertTrue(second.third.endsWith("Now answer my request: which projects do I have?"))
        // the model sees its own look-up request as the previous turn
        assertEquals("which projects do I have?", second.second.last().user)
        assertTrue(second.second.last().assistant.contains("list_projects"))
        assertEquals("You have one project: Garden (1 task).", last().text)
        assertTrue(last().proposals.isEmpty())
    }

    @Test
    fun aModelThatKeepsLookingThingsUpIsStopped() {
        installModel()
        repeat(6) { runtime.answers += """{"reply":"checking","actions":[{"op":"list_labels"}]}""" }
        send("loop forever")
        assertEquals(4, runtime.calls.size) // the first answer + 3 look-up rounds
        assertTrue(last().problems.any { it.contains("couldn't finish looking") })
        assertTrue(last().proposals.isEmpty())
    }

    @Test
    fun tasksNamedLikeCodesAreFoundByTitleThroughTheirLetters() {
        installModel()
        // "Buy milk" is due first so it gets code A; "Task 1" is therefore B, not 1
        runBlocking {
            local.upsertTasks(listOf(
                TaskEntity("m", "Buy milk", dueDate = "2026-11-15T23:59:59Z"),
                TaskEntity("t1", "Task 1", dueDate = "2026-11-16T23:59:59Z"),
                TaskEntity("t2", "Task 2", dueDate = "2026-11-16T23:59:59Z"),
                TaskEntity("t3", "Task 3", dueDate = "2026-11-16T23:59:59Z")
            ))
        }
        runtime.answers += """{"reply":"I'll move them.","actions":[{"op":"create_project","name":"Test"},
            {"op":"update_task","task":"B","project":"Test"},{"op":"update_task","task":"C","project":"Test"},{"op":"update_task","task":"D","project":"Test"}]}"""
        send("Create a Test project and move Task 1, Task 2 and Task 3 to it")

        val system = runtime.calls.single().first
        assertTrue(system.contains("[A] Buy milk"))
        assertTrue(system.contains("[B] Task 1"))
        assertEquals(
            listOf("Create project “Test”", "Update “Task 1”: move to Test", "Update “Task 2”: move to Test", "Update “Task 3”: move to Test"),
            last().proposals.map { it.summary }
        )
        // And the untouched Buy milk really isn't among them
        assertTrue(last().proposals.none { it.summary.contains("Buy milk") })
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
