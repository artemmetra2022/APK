package com.localadb.manager.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onPickApk: () -> Unit,
    onPickSplitApk: () -> Unit,
    onPickApkForVerify: () -> Unit,
    onForgetPairing: () -> Unit,
) {
    val isInstalling by viewModel.isInstalling.collectAsState()
    val installResult by viewModel.installResult.collectAsState()
    val pendingApkInfo by viewModel.pendingApkInfo.collectAsState()
    val signatureResult by viewModel.signatureResult.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Text("✅", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(8.dp))
        Text("Подключено", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        // ── Установить APK ──
        Button(
            onClick = onPickApk,
            enabled = !isInstalling,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            if (isInstalling) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Установка…")
            } else {
                Text("📦  Выбрать и установить APK")
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Установить Split APK ──
        OutlinedButton(
            onClick = onPickSplitApk,
            enabled = !isInstalling,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("📂  Установить Split APK (несколько файлов)")
        }

        Spacer(Modifier.height(12.dp))

        // ── Проверить подпись ──
        OutlinedButton(
            onClick = onPickApkForVerify,
            enabled = !isInstalling,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("🔏  Проверить подпись APK")
        }

        // ── Результат установки ──
        installResult?.let { result ->
            Spacer(Modifier.height(16.dp))
            Text(result, style = MaterialTheme.typography.bodyMedium,
                color = if (result.contains("✅")) MaterialTheme.colorScheme.primary
                else if (result.contains("Ошибка")) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface)
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onForgetPairing) { Text("Забыть сопряжение") }
    }

    // ── Диалог подтверждения установки ──────────────────────────
    pendingApkInfo?.let { info ->
        val isUpdate = info.installedVersionName != null
        val isSame = info.installedVersionName == info.versionName

        AlertDialog(
            onDismissRequest = { viewModel.cancelInstall() },
            title = { Text(if (isUpdate) "Приложение уже установлено" else "Установить приложение?") },
            text = {
                Column {
                    Text(info.packageName, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (isUpdate) {
                        Text("Установлена версия: ${info.installedVersionName}")
                        Text("Новая версия:       ${info.versionName}")
                        if (isSame) {
                            Spacer(Modifier.height(4.dp))
                            Text("Версии совпадают — будет переустановка.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text("Версия: ${info.versionName}")
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isUpdate) {
                        Button(onClick = { viewModel.confirmInstall() }) { Text("Обновить") }
                        OutlinedButton(onClick = { viewModel.confirmInstall() }) { Text("Всё равно установить") }
                    } else {
                        Button(onClick = { viewModel.confirmInstall() }) { Text("Установить") }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelInstall() }) { Text("Отмена") }
            },
        )
    }

    // ── Диалог результата проверки подписи ───────────────────────
    signatureResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.clearSignatureResult() },
            title = { Text("🔏 Подпись APK") },
            text = {
                Text(result,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall)
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearSignatureResult() }) { Text("Закрыть") }
            },
        )
    }
}
