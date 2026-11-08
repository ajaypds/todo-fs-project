package com.example.todoflow

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import com.example.todoflow.security.AppLock
import com.example.todoflow.ui.auth.LockScreen
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.todoflow.data.local.ThemeMode
import com.example.todoflow.theme.TodoFlowTheme
import com.example.todoflow.ui.auth.AuthViewModel
import com.example.todoflow.ui.auth.LoginScreen
import com.example.todoflow.ui.tasks.TasksScreen
import com.example.todoflow.ui.tasks.TasksViewModel

class MainActivity : FragmentActivity() {

    companion object {
        /** The app locks again after being in the background for this long. */
        private const val RELOCK_AFTER_MS = 30_000L
    }

    private val isLocked = MutableStateFlow(false)
    private var isPromptShowing = false
    private var lastStoppedAt = 0L

    private val sessionManager get() = (application as TodoFlowApplication).sessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as TodoFlowApplication
        val sessionManager = app.sessionManager
        val apiClient = app.apiClient
        val taskRepository = app.taskRepository

        // Cold start: locked if the app lock is on and someone is logged in
        isLocked.value = sessionManager.appLockEnabled.value && sessionManager.isLoggedIn.value

        setContent {
            val themeMode by sessionManager.themeMode.collectAsState()
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemDark
            }

            val lockEnabled by sessionManager.appLockEnabled.collectAsState()
            val locked by isLocked.collectAsState()

            // With the lock on, hide the app in the recents screen and block screenshots
            DisposableEffect(lockEnabled) {
                if (lockEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    isLocked.value = false
                }
                onDispose {}
            }

            // Fix for status bar and navigation bar icon contrast in dark & light modes
            DisposableEffect(isDark) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                // When in dark mode: isAppearanceLightStatusBars = false makes status bar icons WHITE
                // When in light mode: isAppearanceLightStatusBars = true makes status bar icons DARK
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
                onDispose {}
            }

            TodoFlowTheme(darkTheme = isDark) {
                // Edge-to-edge: the Surface background is drawn behind the status/navigation bars.
                // Each screen applies the insets itself (Scaffold/TopAppBar on tasks, padding on login).
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isLoggedIn by sessionManager.isLoggedIn.collectAsState()

                    if (locked && lockEnabled && isLoggedIn) {
                        LockScreen(
                            onUnlock = { promptUnlock() },
                            modifier = Modifier.safeDrawingPadding()
                        )
                        return@Surface
                    }

                    Crossfade(
                        targetState = isLoggedIn,
                        label = "AuthCrossfade"
                    ) { loggedIn ->
                        if (loggedIn) {
                            val tasksViewModel: TasksViewModel = viewModel(
                                factory = object : ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        return TasksViewModel(taskRepository, sessionManager, app.realtimeClient) as T
                                    }
                                }
                            )
                            TasksScreen(viewModel = tasksViewModel)
                        } else {
                            val authViewModel: AuthViewModel = viewModel(
                                factory = object : ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        return AuthViewModel(sessionManager, apiClient, taskRepository) as T
                                    }
                                }
                            )
                            LoginScreen(
                                viewModel = authViewModel,
                                modifier = Modifier.safeDrawingPadding()
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val enabled = sessionManager.appLockEnabled.value && sessionManager.isLoggedIn.value
        if (enabled && !isLocked.value && lastStoppedAt > 0 &&
            SystemClock.elapsedRealtime() - lastStoppedAt > RELOCK_AFTER_MS
        ) {
            isLocked.value = true
        }
        if (enabled && isLocked.value) promptUnlock()
    }

    override fun onStop() {
        super.onStop()
        lastStoppedAt = SystemClock.elapsedRealtime()
    }

    private fun promptUnlock() {
        if (isPromptShowing) return
        val authenticators = AppLock.authenticators(this)
        if (authenticators == null) {
            // The device's screen lock was removed: don't lock the user out of their tasks
            sessionManager.setAppLockEnabled(false)
            isLocked.value = false
            return
        }
        isPromptShowing = true
        AppLock.prompt(this, authenticators) { success ->
            isPromptShowing = false
            if (success) isLocked.value = false
        }
    }
}
