package com.localadb.manager.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.localadb.manager.adb.AppFilter
import com.localadb.manager.adb.InstalledApp
import androidx.compose.foundation.Image

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppsScreen(viewModel: MainViewModel) {
    val apps by viewModel.apps.collectAsState()
    val isLoading by viewModel.isLoadingApps.collectAsState()
    val error by viewModel.appsError.collectAsState()
    val appPermissions by viewModel.appPermissions.collectAsState()
    val loadingPermissions by viewModel.loadingPermissions.collectAsState()
    val currentFilter by viewModel.appFilter.collectAsState()
    var query by remember { mutableStateOf("") }
    var expandedPkg by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<InstalledApp?>(null) }

    LaunchedEffect(Unit) { viewModel.loadApps() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {

        // ── Фильтр тип приложений ──
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = currentFilter == AppFilter.USER,
                onClick = { viewModel.setAppFilter(AppFilter.USER) }, label = { Text("Пользователь") })
            FilterChip(selected = currentFilter == AppFilter.SYSTEM,
                onClick = { viewModel.setAppFilter(AppFilter.SYSTEM) }, label = { Text("Системные") })
            FilterChip(selected = currentFilter == AppFilter.ALL,
                onClick = { viewModel.setAppFilter(AppFilter.ALL) }, label = { Text("Все") })
        }
        Spacer(Modifier.height(8.dp))

        // ── Поиск ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Поиск") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { viewModel.loadApps() }, modifier = Modifier.size(48.dp)) {
                Text("🔄", style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.height(8.dp))

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }

        if (isLoading && apps.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(24.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Загружаю список…")
            }
        } else {
            val filtered = apps.filter {
                it.displayName.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
            Text("${filtered.size} приложений", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            LazyColumn {
                items(filtered, key = { it.packageName }) { app ->
                    val isExpanded = expandedPkg == app.packageName
                    AppCard(
                        app = app, isExpanded = isExpanded,
                        permissions = appPermissions[app.packageName],
                        isLoadingPermissions = app.packageName in loadingPermissions,
                        onToggleExpand = {
                            expandedPkg = if (isExpanded) null else {
                                viewModel.loadAppPermissions(app.packageName)
                                app.packageName
                            }
                        },
                        onDeleteClick = { pendingDelete = app },
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }

    pendingDelete?.let { app ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить приложение?") },
            text = { Text("${app.displayName}\n${app.packageName}\n\nЭто действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = { viewModel.uninstallApp(app.packageName); pendingDelete = null }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Отмена") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppCard(
    app: InstalledApp,
    isExpanded: Boolean,
    permissions: Map<String, List<String>>?,
    isLoadingPermissions: Boolean,
    onToggleExpand: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val context = LocalContext.current
    // Правильный способ получения иконки — через getApplicationInfo() а не напрямую по имени пакета
    val icon = remember(app.packageName) {
        runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(app.packageName, 0)
            pm.getApplicationIcon(info)
        }.getOrNull()
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggleExpand() }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Иконка
                if (icon != null) {
                    Image(painter = rememberDrawablePainter(icon), contentDescription = null,
                        modifier = Modifier.size(44.dp))
                } else {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Text("📦", style = MaterialTheme.typography.titleLarge)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    // Главное — отображаемое имя
                    Text(app.displayName, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium)
                    // Версия и размер
                    Text("v${app.versionName}  •  ${formatSize(app.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (isExpanded) "▲" else "▼", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDeleteClick, modifier = Modifier.size(40.dp)) { Text("🗑") }
            }

            // ── Раскрывающийся блок ──
            AnimatedVisibility(visible = isExpanded) {
                Column {
                    HorizontalDivider()
                    Column(Modifier.padding(12.dp)) {
                        // Имя пакета в раскрытом блоке
                        Text("Пакет: ${app.packageName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        app.installedAt?.let {
                            Text("Установлено: $it", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Разрешения", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        when {
                            isLoadingPermissions -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Загружаю…", style = MaterialTheme.typography.bodySmall)
                            }
                            permissions == null -> Text("—", style = MaterialTheme.typography.bodySmall)
                            permissions.isEmpty() -> Text("Нет значимых разрешений",
                                style = MaterialTheme.typography.bodySmall)
                            else -> permissions.forEach { (category, perms) ->
                                Text(category, style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(2.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()) {
                                    perms.forEach { perm ->
                                        SuggestionChip(onClick = {},
                                            label = { Text(perm, style = MaterialTheme.typography.labelSmall) })
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return "—"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) "%.1f МБ".format(mb) else "${bytes / 1024} КБ"
}
