package com.localadb.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localadb.manager.adb.isDangerousCommand

// ── Предустановленные избранные команды ──────────────────────────
private data class FavCmd(val label: String, val cmd: String)

private val FAVORITES = listOf(
    FavCmd("Список пакетов", "pm list packages"),
    FavCmd("Сторонние", "pm list packages -3"),
    FavCmd("Системные", "pm list packages -s"),
    FavCmd("Отключённые", "pm list packages -d"),
    FavCmd("Батарея", "dumpsys battery"),
    FavCmd("ОЗУ", "cat /proc/meminfo | grep MemTotal"),
    FavCmd("Модель", "getprop ro.product.model"),
    FavCmd("Android", "getprop ro.build.version.release"),
    FavCmd("CPU нагрузка", "cat /proc/loadavg"),
    FavCmd("Температура", "cat /sys/class/power_supply/battery/temp"),
    FavCmd("Хранилище", "df /data | tail -1"),
    FavCmd("Сеть", "ip addr show wlan0"),
)

// ── Предустановленные макросы (цепочки команд) ───────────────────
private data class Macro(val name: String, val desc: String, val commands: List<String>)

private val MACROS = listOf(
    Macro("📊 Информация о системе", "Модель, Android, ОЗУ, хранилище", listOf(
        "getprop ro.product.model",
        "getprop ro.build.version.release",
        "getprop ro.build.version.sdk",
        "cat /proc/meminfo | grep MemTotal",
        "df /data | tail -1",
    )),
    Macro("🔋 Батарея", "Статус, температура, циклы заряда", listOf(
        "dumpsys battery | grep -E 'level|status|health|temperature'",
        "cat /sys/class/power_supply/battery/temp",
        "cat /sys/class/power_supply/battery/cycle_count",
    )),
    Macro("🌐 Сеть", "IP, Wi-Fi, DNS", listOf(
        "ip addr show wlan0 | grep inet",
        "getprop net.dns1",
        "getprop net.dns2",
        "dumpsys wifi | grep 'mWifiInfo\\|SSID' | head -3",
    )),
    Macro("⚡ Производительность", "CPU, температура, фоновые процессы", listOf(
        "cat /proc/loadavg",
        "cat /sys/class/thermal/thermal_zone0/temp",
        "ps -A | wc -l",
        "dumpsys meminfo | grep 'Total RAM'",
    )),
    Macro("📦 Пакеты", "Количество всех/системных/сторонних", listOf(
        "pm list packages | wc -l",
        "pm list packages -s | wc -l",
        "pm list packages -3 | wc -l",
        "pm list packages -d | wc -l",
    )),
    Macro("🔒 Безопасность", "Root, загрузчик, отладка", listOf(
        "id",
        "getprop ro.boot.verifiedbootstate",
        "getprop ro.debuggable",
        "getprop service.adb.root",
    )),
)

// ── Текст справки /help ──────────────────────────────────────────
private val HELP_TEXT = """
=== Команды ADB shell ===

[Пакеты]
pm list packages           все пакеты
pm list packages -3        только сторонние
pm list packages -s        только системные
pm list packages -d        отключённые
pm path <pkg>              путь к APK

[Установка / удаление]
pm install -r <path>       установить APK
pm install-multiple -r     установить split APK
pm uninstall <pkg>         удалить
pm uninstall -k --user 0   отключить (обратимо)
pm install-existing --user 0 <pkg>  вернуть

[Система]
getprop ro.product.model   модель устройства
getprop ro.build.version.release  версия Android
cat /proc/meminfo          информация об ОЗУ
df /data                   состояние хранилища
cat /proc/loadavg          нагрузка процессора
cat /proc/cpuinfo          информация о CPU

[Батарея]
dumpsys battery            подробная статистика
cat /sys/class/power_supply/battery/temp  температура (°C × 10)
cat /sys/class/power_supply/battery/cycle_count  циклы заряда

[Сеть]
ip addr show wlan0         IP-адрес Wi-Fi
getprop net.dns1           DNS-сервер

[Специальные команды]
/help                      эта справка
/clear                     очистить историю
""".trimIndent()

@Composable
fun TerminalScreen(viewModel: MainViewModel) {
    var command by rememberSaveable { mutableStateOf("") }
    val history by viewModel.terminalHistory.collectAsState()
    val isRunning by viewModel.isRunningCommand.collectAsState()
    var pendingCommand by remember { mutableStateOf<String?>(null) }
    var showFavorites by rememberSaveable { mutableStateOf(false) }
    var showMacros by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun submit(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isBlank()) return
        when (trimmed.lowercase()) {
            "/help" -> {
                viewModel.runTerminalCommand("# /help")
                // Добавляем в историю локально без отправки в ADB
                // runTerminalCommand обернёт в TerminalEntry, но здесь нам нужен HELP_TEXT
                // Делаем через отдельный вызов с подстановкой
                command = ""
                // Показываем /help как локальную команду
                viewModel.addLocalEntry("/help", HELP_TEXT)
                return
            }
            "/clear" -> {
                viewModel.clearTerminalHistory()
                command = ""
                return
            }
            else -> {}
        }
        if (isDangerousCommand(trimmed)) {
            pendingCommand = trimmed
        } else {
            viewModel.runTerminalCommand(trimmed)
            command = ""
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {

        // ── История ──
        LazyColumn(modifier = Modifier.weight(1f), state = listState) {
            items(history, key = { it.hashCode() }) { entry ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp),
                ) {
                    Text("$ ${entry.command}",
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall)
                    if (entry.output.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(entry.output.trim(),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Поле ввода + кнопка отправки ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                placeholder = { Text("введите команду…") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { submit(command) },
                enabled = !isRunning && command.isNotBlank(),
                modifier = Modifier
                    .height(56.dp)
                    .width(72.dp),
            ) {
                Text("▶", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Кнопки разделов ──
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { showFavorites = !showFavorites; if (showFavorites) showMacros = false },
                modifier = Modifier.weight(1f).height(40.dp),
            ) { Text(if (showFavorites) "☆ Скрыть" else "☆ Избранное") }
            OutlinedButton(
                onClick = { showMacros = !showMacros; if (showMacros) showFavorites = false },
                modifier = Modifier.weight(1f).height(40.dp),
            ) { Text(if (showMacros) "📋 Скрыть" else "📋 Макросы") }
            OutlinedButton(
                onClick = { viewModel.clearTerminalHistory() },
                modifier = Modifier.height(40.dp),
            ) { Text("🗑") }
        }

        // ── Избранные команды ──
        if (showFavorites) {
            Spacer(Modifier.height(8.dp))
            Text("Избранные команды", style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FAVORITES.forEach { fav ->
                    SuggestionChip(
                        onClick = { command = fav.cmd },
                        label = { Text(fav.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }

        // ── Макросы ──
        if (showMacros) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Макросы (цепочки команд)", style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MACROS.forEach { macro ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(macro.name, style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold)
                            Text(macro.desc, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                macro.commands.forEach { cmd ->
                                    viewModel.runTerminalCommand(cmd)
                                }
                                showMacros = false
                            },
                            enabled = !isRunning,
                            modifier = Modifier.height(40.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary),
                        ) { Text("▶", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    // ── Диалог опасной команды ──
    pendingCommand?.let { cmd ->
        AlertDialog(
            onDismissRequest = { pendingCommand = null },
            title = { Text("⚠️ Потенциально опасная команда") },
            text = { Text("«$cmd»\n\nМожет удалить данные или приложения. Выполнить?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.runTerminalCommand(cmd)
                    command = ""
                    pendingCommand = null
                }) { Text("Выполнить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCommand = null }) { Text("Отмена") }
            },
        )
    }

    // Авто-скролл вниз
    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) listState.animateScrollToItem(history.size - 1)
    }
}
