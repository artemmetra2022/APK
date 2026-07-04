package com.localadb.manager.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localadb.manager.adb.InstalledApp

@Composable
fun AppsScreen(viewModel: MainViewModel) {
    val apps by viewModel.apps.collectAsState()
    val isLoading by viewModel.isLoadingApps.collectAsState()
    val error by viewModel.appsError.collectAsState()
    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<InstalledApp?>(null) }

    LaunchedEffect(Unit) { viewModel.loadApps() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Поиск по имени пакета") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { viewModel.loadApps() }) { Text("🔄") }
        }
        Spacer(Modifier.height(8.dp))

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }

        if (isLoading && apps.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Читаю список приложений…")
                }
            }
        } else {
            val filtered = apps.filter { it.packageName.contains(query, ignoreCase = true) }
            if (filtered.isEmpty()) {
                Text("Ничего не найдено", style = MaterialTheme.typography.bodyMedium)
            }
            LazyColumn {
                items(filtered, key = { it.packageName }) { app ->
                    AppRow(app = app, onDeleteClick = { pendingDelete = app })
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    pendingDelete?.let { app ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить приложение?") },
            text = { Text("${app.packageName}\n\nЭто действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.uninstallApp(app.packageName)
                    pendingDelete = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun AppRow(app: InstalledApp, onDeleteClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(app.packageName, fontWeight = FontWeight.Bold)
                Text("Версия: ${app.versionName}", style = MaterialTheme.typography.bodySmall)
                Text("Размер: ${formatSize(app.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Установлено: ${app.installedAt ?: "неизвестно"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onDeleteClick) { Text("🗑") }
        }
    }
}

private fun formatSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return "—"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) "%.1f МБ".format(mb) else "${bytes / 1024} КБ"
}
