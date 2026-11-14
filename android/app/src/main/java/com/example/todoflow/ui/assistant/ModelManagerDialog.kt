package com.example.todoflow.ui.assistant

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.todoflow.ai.AssistantViewModel
import com.example.todoflow.ai.DownloadState
import com.example.todoflow.ai.InstalledModel
import com.example.todoflow.ai.ModelCatalog
import com.example.todoflow.ai.ModelSpec
import com.example.todoflow.ai.RuntimeState

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "${bytes / 1_000_000} MB"
    else -> "${bytes / 1_000} KB"
}

private fun displayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: uri.lastPathSegment

/** Download, import, select and delete the on-device models (similar to Google AI Edge Gallery). */
@Composable
fun ModelManagerDialog(viewModel: AssistantViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val installed by viewModel.installed.collectAsState()
    val active by viewModel.activeModel.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val runtime by viewModel.runtimeState.collectAsState()
    val useGpu by viewModel.useGpu.collectAsState()
    val notice by viewModel.modelNotice.collectAsState()
    val importing by viewModel.importing.collectAsState()
    var token by remember { mutableStateOf(viewModel.huggingFaceToken) }
    var customUrl by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<InstalledModel?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = displayName(context, uri) ?: "model.litertlm"
            viewModel.importModel(name) { context.contentResolver.openInputStream(uri) }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                    Text("On-device AI models", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Models run entirely on your phone. They are large, so they are downloaded (Wi-Fi or mobile data) or imported instead of being part of the app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    notice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    importing?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

                    SectionTitle("Installed")
                    if (installed.isEmpty()) {
                        Text("No model installed yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    installed.forEach { model ->
                        val isActive = model.fileName == active
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = isActive, onClick = { viewModel.selectModel(model.fileName) })
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(model.displayName, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        formatBytes(model.sizeBytes) + if (isActive) " · " + runtimeLabel(runtime) else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { confirmDelete = model }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete ${model.displayName}")
                                }
                            }
                        }
                    }

                    SectionTitle("Download")
                    val recommended = viewModel.recommended
                    ModelCatalog.models.filter { spec -> installed.none { it.fileName == spec.fileName } }.forEach { spec ->
                        DownloadCard(
                            spec = spec,
                            recommended = spec.id == recommended.id,
                            state = downloads[spec.fileName],
                            partialBytes = viewModel.store.partialBytes(spec.fileName),
                            lowRam = viewModel.deviceRamGb in 1 until spec.minRamGb,
                            onDownload = { viewModel.download(spec) },
                            onCancel = { viewModel.cancelDownload(spec.fileName) }
                        )
                    }

                    SectionTitle("Add your own")
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth(), enabled = importing == null) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Import a .litertlm file")
                    }
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text("…or download from a link") },
                        placeholder = { Text("https://…/model.litertlm") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { viewModel.downloadFromUrl(customUrl) }, enabled = customUrl.isNotBlank()) { Text("Download from link") }
                    // Custom downloads show up here too
                    downloads.filterKeys { name -> ModelCatalog.byFileName(name) == null }.forEach { (name, state) ->
                        DownloadProgressRow(name, state, onCancel = { viewModel.cancelDownload(name) })
                    }

                    SectionTitle("Hugging Face token")
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it; viewModel.huggingFaceToken = it },
                        label = { Text("Access token (only for Gemma)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Gemma models are gated: accept the licence on huggingface.co, create a read token and paste it here. It is stored encrypted on this device and only sent to Hugging Face.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Use the GPU (experimental)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Can be faster, but not every phone supports it. It falls back to the CPU if it can't start.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = useGpu, onCheckedChange = { viewModel.setUseGpu(it) })
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    confirmDelete?.let { model ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${model.displayName}?") },
            text = { Text("This frees ${formatBytes(model.sizeBytes)} of storage. You can download or import it again later.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteModel(model.fileName); confirmDelete = null },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

private fun runtimeLabel(state: RuntimeState): String = when (state) {
    is RuntimeState.Loading -> "loading…"
    is RuntimeState.Ready -> "ready (${state.backend})"
    is RuntimeState.Failed -> "failed to load"
    RuntimeState.Unloaded -> "selected"
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun DownloadCard(
    spec: ModelSpec,
    recommended: Boolean,
    state: DownloadState?,
    partialBytes: Long,
    lowRam: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(spec.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text("· ${formatBytes(spec.approxBytes)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (recommended) Text("Recommended", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
            Text(spec.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (lowRam) Text("Needs about ${spec.minRamGb} GB of RAM: may not run well on this phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (spec.gated) Text("Needs a Hugging Face token.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (state) {
                is DownloadState.Running -> DownloadProgressRow(spec.fileName, state, onCancel)
                is DownloadState.Failed -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onDownload) { Text(if (state.downloaded > 0) "Resume" else "Retry") }
                }
                null -> Button(onClick = onDownload) { Text(if (partialBytes > 0) "Resume (${formatBytes(partialBytes)} done)" else "Download") }
            }
        }
    }
}

@Composable
private fun DownloadProgressRow(name: String, state: DownloadState, onCancel: () -> Unit) {
    when (state) {
        is DownloadState.Running -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.total > 0) {
                LinearProgressIndicator(progress = { (state.downloaded.toFloat() / state.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${ModelCatalog.displayName(name)}: ${formatBytes(state.downloaded)}" + if (state.total > 0) " of ${formatBytes(state.total)}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onCancel) { Text("Pause") }
            }
        }
        is DownloadState.Failed -> Text("${ModelCatalog.displayName(name)}: ${state.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}
