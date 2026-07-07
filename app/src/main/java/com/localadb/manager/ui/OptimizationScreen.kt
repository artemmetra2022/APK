package com.localadb.manager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.localadb.manager.adb.ManufacturerProfile
import com.localadb.manager.adb.OPTIMIZATION_DATABASE
import com.localadb.manager.adb.OptimizableApp
import com.localadb.manager.adb.OptimizationRisk

private enum class RiskFilter { ALL, LOW, MEDIUM }

@Composable
fun OptimizationScreen(viewModel: MainViewModel) {
    val disabledPackages by viewModel.disabledPackages.collectAsState()
    val statuses by viewModel.optimizationStatus.collectAsState()
    val isAutoOptimizing by viewModel.isAutoOptimizing.collectAsState()
    var selectedManufacturer by remember { mutableStateOf<ManufacturerProfile?>(null) }
    var showAutoOptimizeDialog by remember { mutableStateOf(false) }
    var showCancelOptimizeDialog by remember { mutableStateOf(false) }
    var autoOptimizeManufacturer by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadDisabledPackages() }

    if (selectedManufacturer == null) {
        ManufacturerListScreen(
            disabledPackages = disabledPackages,
            isAutoOptimizing = isAutoOptimizing,
            onSelect = { selectedManufacturer = it },
            onAutoOptimize = { name ->
                autoOptimizeManufacturer = name
                showAutoOptimizeDialog = true
            },
            onCancelOptimize = { name ->
                autoOptimizeManufacturer = name
                showCancelOptimizeDialog = true
            },
        )
    } else {
        PackageListScreen(
            profile = selectedManufacturer!!,
            disabledPackages = disabledPackages,
            statuses = statuses,
            onBack = { selectedManufacturer = null },
            onDisable = viewModel::disablePackage,
            onEnable = viewModel::enablePackage,
            onBatchDisable = viewModel::batchDisable,
        )
    }

    // Диалог автооптимизации
    if (showAutoOptimizeDialog) {
        val profile = OPTIMIZATION_DATABASE.firstOrNull { it.name == autoOptimizeManufacturer }
        val priorityList = profile?.apps?.filter { it.isPriority && it.packageName !in disabledPackages }
            ?: emptyList()
        AlertDialog(
            onDismissRequest = { showAutoOptimizeDialog = false },
            title = { Text("⚡ Автооптимизация") },
            text = {
                Column {
                    Text("Будут отключены ${priorityList.size} приоритетных пакетов для ${autoOptimizeManufacturer}:")
                    Spacer(Modifier.height(8.dp))
                    priorityList.take(10).forEach { app ->
                        Text("• ${app.displayName}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (priorityList.size > 10) {
                        Text("... и ещё ${priorityList.size - 10}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Все изменения обратимы.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.autoOptimize(autoOptimizeManufacturer)
                    showAutoOptimizeDialog = false
                }) { Text("Применить") }
            },
            dismissButton = {
                TextButton(onClick = { showAutoOptimizeDialog = false }) { Text("Отмена") }
            },
        )
    }

    // Диалог отмены автооптимизации
    if (showCancelOptimizeDialog) {
        val profile = OPTIMIZATION_DATABASE.firstOrNull { it.name == autoOptimizeManufacturer }
        val toRestore = profile?.apps?.filter { it.isPriority && it.packageName in disabledPackages }
            ?: emptyList()
        AlertDialog(
            onDismissRequest = { showCancelOptimizeDialog = false },
            title = { Text("Отменить оптимизацию?") },
            text = {
                Text("Будут возвращены ${toRestore.size} пакетов для $autoOptimizeManufacturer.")
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.cancelAutoOptimize(autoOptimizeManufacturer)
                    showCancelOptimizeDialog = false
                }) { Text("Восстановить") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelOptimizeDialog = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun ManufacturerListScreen(
    disabledPackages: Set<String>,
    isAutoOptimizing: Boolean,
    onSelect: (ManufacturerProfile) -> Unit,
    onAutoOptimize: (String) -> Unit,
    onCancelOptimize: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Оптимизация устройства", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Отключение фоновых сервисов. Все изменения обратимы.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            if (isAutoOptimizing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Выполняется оптимизация…", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        items(OPTIMIZATION_DATABASE) { profile ->
            val disabledCount = profile.apps.count { it.packageName in disabledPackages }
            val priorityCount = profile.apps.count { it.isPriority }
            val canAutoOptimize = profile.apps.any { it.isPriority && it.packageName !in disabledPackages }
            val canUndo = profile.apps.any { it.isPriority && it.packageName in disabledPackages }

            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(profile.emoji, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, fontWeight = FontWeight.Bold)
                            Text(
                                "${profile.apps.size} пакетов · $priorityCount приоритетных" +
                                    if (disabledCount > 0) " · $disabledCount отключено" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Кнопка открытия списка
                        OutlinedButton(
                            onClick = { onSelect(profile) },
                            modifier = Modifier.weight(1f).height(40.dp),
                        ) { Text("Список", style = MaterialTheme.typography.bodySmall) }

                        // Автооптимизация
                        if (canAutoOptimize) {
                            Button(
                                onClick = { onAutoOptimize(profile.name) },
                                modifier = Modifier.weight(1f).height(40.dp),
                                enabled = !isAutoOptimizing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error),
                            ) { Text("⚡ Авто", style = MaterialTheme.typography.bodySmall) }
                        }
                        // Отмена оптимизации
                        if (canUndo) {
                            OutlinedButton(
                                onClick = { onCancelOptimize(profile.name) },
                                modifier = Modifier.height(40.dp),
                            ) { Text("↩", style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()) {
                Text("⚠️ Не отключать: Google Play Services, SystemUI, Settings, Bluetooth, Wi-Fi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun PackageListScreen(
    profile: ManufacturerProfile,
    disabledPackages: Set<String>,
    statuses: Map<String, String>,
    onBack: () -> Unit,
    onDisable: (String) -> Unit,
    onEnable: (String) -> Unit,
    onBatchDisable: (List<String>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var riskFilter by remember { mutableStateOf(RiskFilter.ALL) }
    var selected by remember { mutableStateOf(setOf<String>()) }

    val activeApps = profile.apps.filter { it.packageName !in disabledPackages }
    val disabledApps = profile.apps.filter { it.packageName in disabledPackages }

    fun applyFilters(list: List<OptimizableApp>) = list
        .filter { query.isBlank() || it.displayName.contains(query, ignoreCase = true) }
        .filter { when (riskFilter) {
            RiskFilter.ALL -> true
            RiskFilter.LOW -> it.risk == OptimizationRisk.LOW
            RiskFilter.MEDIUM -> it.risk == OptimizationRisk.MEDIUM
        }}

    val priorityActive = applyFilters(activeApps.filter { it.isPriority })
    val regularActive = applyFilters(activeApps.filter { !it.isPriority })
    val filteredDisabled = applyFilters(disabledApps)

    Column(Modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("← Назад") }
            Spacer(Modifier.width(12.dp))
            Text("${profile.emoji} ${profile.name}", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
        }

        OutlinedTextField(value = query, onValueChange = { query = it },
            label = { Text("Поиск по названию") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = riskFilter == RiskFilter.ALL,
                onClick = { riskFilter = RiskFilter.ALL }, label = { Text("Все") })
            FilterChip(selected = riskFilter == RiskFilter.LOW,
                onClick = { riskFilter = RiskFilter.LOW }, label = { Text("🟢 Низкий") })
            FilterChip(selected = riskFilter == RiskFilter.MEDIUM,
                onClick = { riskFilter = RiskFilter.MEDIUM }, label = { Text("🟡 Средний") })
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            if (priorityActive.isNotEmpty()) {
                item {
                    Text("🔥 Рекомендуется отключить в первую очередь",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 6.dp))
                }
                items(priorityActive, key = { "p_${it.packageName}" }) { app ->
                    PackageRow(app = app, isDisabled = false, isSelected = app.packageName in selected,
                        status = statuses[app.packageName],
                        onCheckedChange = { checked ->
                            selected = if (checked) selected + app.packageName else selected - app.packageName
                        }, onEnable = { onEnable(app.packageName) })
                }
                item { HorizontalDivider(Modifier.padding(vertical = 6.dp)) }
            }

            if (regularActive.isNotEmpty()) {
                if (priorityActive.isNotEmpty()) {
                    item { Text("Остальные", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)) }
                }
                items(regularActive, key = { "a_${it.packageName}" }) { app ->
                    PackageRow(app = app, isDisabled = false, isSelected = app.packageName in selected,
                        status = statuses[app.packageName],
                        onCheckedChange = { checked ->
                            selected = if (checked) selected + app.packageName else selected - app.packageName
                        }, onEnable = { onEnable(app.packageName) })
                }
            }

            if (filteredDisabled.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text("✅ Уже отключено", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 6.dp))
                }
                items(filteredDisabled, key = { "d_${it.packageName}" }) { app ->
                    PackageRow(app = app, isDisabled = true, isSelected = false,
                        status = statuses[app.packageName], onCheckedChange = {},
                        onEnable = { onEnable(app.packageName) })
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }

        // Кнопка пакетного отключения
        if (selected.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selected = emptySet() }) { Text("Снять выбор") }
                Button(
                    onClick = { onBatchDisable(selected.toList()); selected = emptySet() },
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Отключить (${selected.size})") }
            }
        }
    }
}

@Composable
private fun PackageRow(
    app: OptimizableApp, isDisabled: Boolean, isSelected: Boolean,
    status: String?, onCheckedChange: (Boolean) -> Unit, onEnable: () -> Unit,
) {
    val context = LocalContext.current
    val icon = remember(app.packageName) {
        runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(app.packageName, 0)
            pm.getApplicationIcon(info)
        }.getOrNull()
    }
    val isBusy = status?.endsWith("…") == true

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDisabled) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!isDisabled) {
                Checkbox(checked = isSelected, onCheckedChange = onCheckedChange, enabled = !isBusy)
            } else {
                Spacer(Modifier.width(12.dp))
            }

            if (icon != null) {
                Image(painter = rememberDrawablePainter(icon), contentDescription = null,
                    modifier = Modifier.size(36.dp))
            } else {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Text("📦") }
            }
            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.displayName, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(4.dp))
                    Text(if (app.risk == OptimizationRisk.LOW) "🟢" else "🟡",
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(app.packageName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(app.description, style = MaterialTheme.typography.bodySmall)
                status?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = when {
                            it.contains("✅") -> MaterialTheme.colorScheme.primary
                            it.contains("Ошибка") -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        })
                }
            }

            if (isDisabled) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onEnable, enabled = !isBusy,
                    modifier = Modifier.height(40.dp)) {
                    Text("Вернуть", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
