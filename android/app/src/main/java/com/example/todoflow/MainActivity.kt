package com.example.todoflow

import android.os.Bundle
import androidx.activity.ComponentActivity
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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as TodoFlowApplication
        val sessionManager = app.sessionManager
        val apiClient = app.apiClient
        val taskRepository = app.taskRepository

        setContent {
            val themeMode by sessionManager.themeMode.collectAsState()
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemDark
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
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isLoggedIn by sessionManager.isLoggedIn.collectAsState()

                    Crossfade(
                        targetState = isLoggedIn,
                        label = "AuthCrossfade"
                    ) { loggedIn ->
                        if (loggedIn) {
                            val tasksViewModel: TasksViewModel = viewModel(
                                factory = object : ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        return TasksViewModel(taskRepository, sessionManager) as T
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
                            LoginScreen(viewModel = authViewModel)
                        }
                    }
                }
            }
        }
    }
}
