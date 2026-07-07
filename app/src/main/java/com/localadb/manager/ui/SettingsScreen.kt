package com.localadb.manager.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onForgetPairing: () -> Unit,
    onNewPairing: () -> Unit,
) {
    val context = LocalContext.current
    val darkMode by viewModel.darkMode.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val isLoadingDeviceInfo by viewModel.isLoadingDeviceInfo.collectAsState()
    val backupStatus by viewModel.backupStatus.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(backupStatus) {
        backupStatus?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearBackupStatus()
        }
    }

    Column(Modifier.fillMaxSize()) {
        SnackbarHost(snackbarHostState)

        // ── Шапка с кнопкой назад ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Spacer(Modifier.width(8.dp))
            Text("Настройки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {

            // ── Тема ──────────────────────────────────────────────
            SectionHeader("🎨 Оформление")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Тема приложения", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        ThemeBtn("☀️ Светлая", darkMode == false, Modifier.weight(1f)) { viewModel.setDarkMode(false) }
                        Spacer(Modifier.width(8.dp))
                        ThemeBtn("🌗 Системная", darkMode == null, Modifier.weight(1f)) { viewModel.setDarkMode(null) }
                        Spacer(Modifier.width(8.dp))
                        ThemeBtn("🌙 Тёмная", darkMode == true, Modifier.weight(1f)) { viewModel.setDarkMode(true) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Подключение ───────────────────────────────────────
            SectionHeader("🔗 Подключение")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    ConnRow("Новое сопряжение", "Ввести новый код", onNewPairing)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    ConnRow("Сбросить подключение", "Забыть ключи ADB", onForgetPairing, isDestructive = true)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Резервная копия ───────────────────────────────────
            SectionHeader("💾 Резервная копия")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(viewModel.backupInfo, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Button(onClick = { viewModel.saveBackup() }, Modifier.weight(1f)) { Text("Создать копию") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { viewModel.restoreBackup() }, Modifier.weight(1f)) { Text("Восстановить") }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Информация об устройстве ──────────────────────────
            SectionHeader("📱 Информация об устройстве")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    when {
                        isLoadingDeviceInfo -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Загружаю…", style = MaterialTheme.typography.bodySmall)
                        }
                        deviceInfo == null -> OutlinedButton(
                            onClick = { viewModel.loadDeviceInfo() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Загрузить информацию") }
                        else -> {
                            val info = deviceInfo!!
                            InfoRow("Модель", info.model)
                            InfoRow("Android", info.androidVersion)
                            InfoRow("API", info.apiLevel)
                            InfoRow("Процессор", info.cpu)
                            InfoRow("ОЗУ", info.totalRamMb)
                            InfoRow("Свободно", info.freeStorageGb)
                            InfoRow("CPU нагрузка", info.cpuLoad)
                            InfoRow("Температура", info.batteryTemp)
                            InfoRow("Батарея", info.batteryStatus)
                            InfoRow("Циклов заряда", info.cycleCount)
                            InfoRow("Root", info.isRooted)
                            InfoRow("Загрузчик", info.bootloader)
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { viewModel.loadDeviceInfo() }, Modifier.fillMaxWidth()) { Text("Обновить") }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Поделиться ────────────────────────────────────────
            SectionHeader("📤 Поделиться приложением")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Button(
                        onClick = {
                            context.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "ADB Менеджер")
                                    putExtra(Intent.EXTRA_TEXT,
                                        "ADB Менеджер — управление приложениями через Wireless Debugging " +
                                            "без root и компьютера.\nhttps://github.com/artemmetra2022/APK")
                                }, "Поделиться"
                            ))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Поделиться") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, top = 4.dp))
}

@Composable
private fun ThemeBtn(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = modifier.height(40.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    } else OutlinedButton(onClick = onClick, modifier = modifier.height(40.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ConnRow(title: String, subtitle: String, onClick: () -> Unit, isDestructive: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onClick, Modifier.height(36.dp)) {
            Text(if (isDestructive) "Сбросить" else "Открыть", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label:", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
