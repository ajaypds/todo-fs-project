package com.example.todoflow.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.todoflow.ai.AssistantViewModel
import com.example.todoflow.ai.LlmRuntime
import com.example.todoflow.ai.ModelDownloader
import com.example.todoflow.ai.ModelStore
import com.example.todoflow.ai.RuntimeState
import com.example.todoflow.ai.Turn
import com.example.todoflow.data.local.SessionManager
import com.example.todoflow.data.local.TaskFilterType
import com.example.todoflow.data.local.TodoDbHelper
import com.example.todoflow.data.local.TodoLocalDataSource
import com.example.todoflow.data.remote.ApiProvider
import com.example.todoflow.data.repository.FakeTodoFlowApi
import com.example.todoflow.data.repository.TaskRepository
import com.example.todoflow.ui.assistant.AssistantDialog
import com.example.todoflow.ui.assistant.ModelManagerDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

private class ScriptedRuntime(var answer: String = """{"reply":"ok","actions":[]}""") : LlmRuntime {
    override val state: StateFlow<RuntimeState> = MutableStateFlow(RuntimeState.Unloaded)
    override suspend fun load(modelFile: File, useGpu: Boolean) = Unit
    override suspend fun complete(system: String, history: List<Turn>, user: String) = answer
    override suspend fun unload() = Unit
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AssistantUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: TodoDbHelper
    private lateinit var local: TodoLocalDataSource
    private lateinit var store: ModelStore
    private lateinit var runtime: ScriptedRuntime
    private lateinit var vm: AssistantViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = TodoDbHelper(context, "assistant-ui-${java.util.UUID.randomUUID()}.db")
        local = TodoLocalDataSource(db)
        val repo = TaskRepository(local, ApiProvider { FakeTodoFlowApi() }, CoroutineScope(Job().apply { cancel() }))
        store = ModelStore(tmp.newFolder("models"))
        runtime = ScriptedRuntime()
        val session = SessionManager(context).apply { setAiActiveModel(null); huggingFaceToken = "" }
        vm = AssistantViewModel(repo, session, store, ModelDownloader(OkHttpClient(), store, CoroutineScope(Job()), { "" }), runtime, deviceRamGb = 16)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun modelManagerOffersTheCatalogueAndRecommendsGemmaOnA16GbPhone() {
        compose.setContent { MaterialTheme { ModelManagerDialog(vm, onDismiss = {}) } }
        compose.waitForIdle()

        compose.onNodeWithText("On-device AI models").assertIsDisplayed()
        compose.onNodeWithText("No model installed yet.").assertExists()
        compose.onNodeWithText("Qwen3 0.6B").assertExists()
        compose.onNodeWithText("Gemma 4 E2B").assertExists()
        compose.onNodeWithText("Recommended").assertExists()
        compose.onNodeWithText("Import a .litertlm file").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun downloadingAGatedModelWithoutATokenExplainsWhy() {
        compose.setContent { MaterialTheme { ModelManagerDialog(vm, onDismiss = {}) } }
        compose.waitForIdle()
        // Gemma 4 E2B is the second card: its Download button is the second one
        compose.onAllNodesWithText("Download")[1].performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("needs a Hugging Face token", substring = true).assertExists()
    }

    @Test
    fun installedModelsCanBeSelectedAndDeleted() {
        store.import(ByteArrayInputStream(ByteArray(1_500_000)), "Qwen3-0.6B.litertlm")
        vm.refreshInstalled()
        compose.setContent { MaterialTheme { ModelManagerDialog(vm, onDismiss = {}) } }
        compose.waitForIdle()

        compose.onNodeWithText("Qwen3 0.6B").assertExists()
        compose.onNodeWithContentDescription("Delete Qwen3 0.6B").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Delete Qwen3 0.6B?").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(5_000) { store.list().isEmpty() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("No model installed yet.").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun chatWithoutAModelOffersToSetOneUp() {
        var opened = 0
        compose.setContent { MaterialTheme { AssistantDialog(vm, onDismiss = {}, onOpenModels = { opened++ }) } }
        compose.waitForIdle()
        compose.onNodeWithText("No model installed").assertIsDisplayed()
        compose.onNodeWithText("Set up a model").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun chatShowsTheProposedChangesAndAppliesThemOnRequest() {
        store.import(ByteArrayInputStream(ByteArray(10)), "Qwen3-0.6B.litertlm")
        vm.refreshInstalled()
        runtime.answer = """{"reply":"Sure","actions":[{"op":"create_task","title":"Water plants","due":"2026-11-15"}]}"""
        compose.setContent { MaterialTheme { AssistantDialog(vm, onDismiss = {}, onOpenModels = {}) } }
        compose.waitForIdle()

        compose.onNodeWithText("Tell me what to do…").performTextInput("water the plants tomorrow")
        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Apply").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("water the plants tomorrow").assertExists() // may be scrolled off in the small test window
        compose.onNodeWithText("Sure").assertExists()
        compose.onNodeWithText("Create task “Water plants”", substring = true).assertExists()

        compose.onNodeWithText("Apply").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("1 change applied").fetchSemanticsNodes().isNotEmpty() }
        val titles = runBlocking { local.getTasks(TaskFilterType.ALL) }.map { it.title }
        assertEquals(listOf("Water plants"), titles)

        // The result is explained and there is a shortcut to the list; the model's raw answer can be inspected
        compose.onNodeWithText("Show my tasks").assertExists()
        compose.onNodeWithText("Show model output").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("""{"reply":"Sure"""", substring = true).assertExists()
    }
}
