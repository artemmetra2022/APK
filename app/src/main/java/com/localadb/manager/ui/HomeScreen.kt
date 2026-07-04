package com.localadb.manager.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onPickApk: () -> Unit,
    onForgetPairing: () -> Unit,
) {
    val isInstalling by viewModel.isInstalling.collectAsState()
    val installResult by viewModel.installResult.collectAsState()
    val pendingApkInfo by viewModel.pendingApkInfo.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text("✅", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(8.dp))
        Text("Подключено", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onPickApk,
            enabled = !isInstalling,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            if (isInstalling) {
                CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Установка…")
            } else {
                Text("Выбрать и установить APK")
            }
        }

        installResult?.let { result ->
            Spacer(Modifier.height(16.dp))
            Text(result, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.weight(1f))
        TextButton(onClick = onForgetPairing) { Text("Забыть сопряжение") }
    }

    // Диалог подтверждения установки (когда пакет уже установлен)
    pendingApkInfo?.let { info ->
        val title: String
        val message: String
        when {
            info.installedVersionName == null -> {
                title = "Установить приложение?"
                message = "${info.packageName}\nВерсия: ${info.versionName}"
            }
            info.installedVersionName == info.versionName -> {
                title = "Переустановить?"
                message = "${info.packageName}\nУже установлена версия ${info.installedVersionName}.\nПереустановить ту же версию?"
            }
            else -> {
                title = "Обновить / откатить?"
                message = "${info.packageName}\nУстановлена: ${info.installedVersionName}\nНовая: ${info.versionName}"
            }
        }
        AlertDialog(
            onDismissRequest = { viewModel.cancelInstall() },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmInstall() }) { Text("Установить") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelInstall() }) { Text("Отмена") }
            },
        )
    }
}
