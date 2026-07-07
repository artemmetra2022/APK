package com.localadb.manager.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localadb.manager.adb.AdbConnectionState
import com.localadb.manager.ui.theme.LocalAdbManagerTheme

@Composable
fun LocalAdbManagerApp(
    viewModel: MainViewModel,
    onPickApk: () -> Unit,
    onPickSplitApk: () -> Unit,
    onPickApkForVerify: () -> Unit,
    onOpenDeveloperSettings: () -> Unit,
) {
    val state by viewModel.connectionState.collectAsState()
    val darkModeOverride by viewModel.darkMode.collectAsState()
    val resolvedDark = darkModeOverride ?: isSystemInDarkTheme()

    LocalAdbManagerTheme(darkTheme = resolvedDark) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (state is AdbConnectionState.Connected) {
                ConnectedApp(
                    viewModel = viewModel,
                    onPickApk = onPickApk,
                    onPickSplitApk = onPickSplitApk,
                    onPickApkForVerify = onPickApkForVerify,
                )
            } else {
                PairingScreen(
                    state = state,
                    onOpenDeveloperSettings = onOpenDeveloperSettings,
                    onSubmitCode = viewModel::pair,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectedApp(
    viewModel: MainViewModel,
    onPickApk: () -> Unit,
    onPickSplitApk: () -> Unit,
    onPickApkForVerify: () -> Unit,
) {
    // 0=Установить 1=Приложения 2=Терминал 3=Оптимизация 4=Настройки
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var prevTab by rememberSaveable { mutableIntStateOf(0) }
    val isReconnecting by viewModel.isReconnecting.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ADB Менеджер") },
                actions = {
                    IconButton(
                        onClick = { viewModel.retryConnect() },
                        enabled = !isReconnecting,
                        modifier = Modifier.size(48.dp),
                    ) { Text("🔄", fontSize = 22.sp) }
                    IconButton(
                        onClick = {
                            if (tab != 4) { prevTab = tab; tab = 4 }
                            else { tab = prevTab }
                        },
                        modifier = Modifier.size(48.dp),
                    ) { Text("⚙️", fontSize = 22.sp) }
                },
            )
        },
        bottomBar = {
            if (tab != 4) {
                NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                        icon = { Text("📥", fontSize = 20.sp) }, label = { Text("Установить") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                        icon = { Text("📱", fontSize = 20.sp) }, label = { Text("Приложения") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                        icon = { Text("⌨️", fontSize = 20.sp) }, label = { Text("Терминал") })
                    NavigationBarItem(selected = tab == 3, onClick = { tab = 3 },
                        icon = { Text("⚡", fontSize = 20.sp) }, label = { Text("Оптимизация") })
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> HomeScreen(
                    viewModel = viewModel,
                    onPickApk = onPickApk,
                    onPickSplitApk = onPickSplitApk,
                    onPickApkForVerify = onPickApkForVerify,
                    onForgetPairing = viewModel::forgetPairing,
                )
                1 -> AppsScreen(viewModel)
                2 -> TerminalScreen(viewModel)
                3 -> OptimizationScreen(viewModel)
                4 -> SettingsScreen(
                    viewModel = viewModel,
                    onBack = { tab = prevTab },          // ← Выход из настроек
                    onForgetPairing = { viewModel.forgetPairing(); tab = 0 },
                    onNewPairing = { viewModel.forgetPairing(); tab = 0 },
                )
            }
        }
    }
}
