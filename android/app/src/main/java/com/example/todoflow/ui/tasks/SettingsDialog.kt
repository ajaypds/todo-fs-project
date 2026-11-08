package com.example.todoflow.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.todoflow.data.local.ThemeMode

@Composable
fun SettingsDialog(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    remindersEnabled: Boolean,
    onRemindersChange: (Boolean) -> Unit,
    appLockEnabled: Boolean,
    appLockAvailable: Boolean,
    onAppLockChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Theme", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                listOf(
                    ThemeMode.SYSTEM to "System default",
                    ThemeMode.LIGHT to "Light",
                    ThemeMode.DARK to "Dark"
                ).forEach { (mode, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = themeMode == mode, onClick = { onThemeChange(mode) })
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SettingSwitch(
                    title = "Due date reminders",
                    description = "Notify me at 9:00 on the day a task is due",
                    checked = remindersEnabled,
                    enabled = true,
                    onCheckedChange = onRemindersChange
                )

                Spacer(Modifier.height(8.dp))

                SettingSwitch(
                    title = "App lock",
                    description = if (appLockAvailable) {
                        "Require fingerprint, face or screen lock to open the app"
                    } else {
                        "Set up a screen lock or fingerprint on this device to use this"
                    },
                    checked = appLockEnabled,
                    enabled = appLockAvailable || appLockEnabled,
                    onCheckedChange = onAppLockChange
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
