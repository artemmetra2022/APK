package com.localadb.manager.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
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
import com.localadb.manager.adb.AdbConnectionState
import com.localadb.manager.ui.theme.LocalAdbManagerTheme

@Composable
fun LocalAdbManagerApp(
    viewModel: MainViewModel,
    onPickApk: () -> Unit,
    onOpenDeveloperSettings: () -> Unit,
) {
    val state by viewModel.connectionState.collectAsState()
    val darkModeOverride by viewModel.darkMode.collectAsState()
    val resolvedDarkTheme = darkModeOverride ?: isSystemInDarkTheme()

    LocalAdbManagerTheme(darkTheme = resolvedDarkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (state is AdbConnectionState.Connected) {
                ConnectedApp(viewModel = viewModel, onPickApk = onPickApk, darkMode = darkModeOverride)
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
private fun ConnectedApp(viewModel: MainViewModel, onPickApk: () -> Unit, darkMode: Boolean?) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val isReconnecting by viewModel.isReconnecting.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ADB Менеджер") },
                actions = {
                    IconButton(onClick = { viewModel.retryConnect() }, enabled = !isReconnecting) {
                        if (isReconnecting) {
                            CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
                        } else {
                            Text("🔄")
                        }
                    }
                    IconButton(onClick = {
                        val next = when (darkMode) {
                            null -> true
                            true -> false
                            false -> null
                        }
                        viewModel.setDarkMode(next)
                    }) {
                        Text(when (darkMode) {
                            null -> "🌗"
                            true -> "🌙"
                            false -> "☀️"
                        })
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Text("📥") },
                    label = { Text("Установить") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Text("📱") },
                    label = { Text("Приложения") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Text("⌨️") },
                    label = { Text("Терминал") },
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Text("⚡") },
                    label = { Text("Оптимизация") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> HomeScreen(
                    viewModel = viewModel,
                    onPickApk = onPickApk,
                    onForgetPairing = viewModel::forgetPairing,
                )
                1 -> AppsScreen(viewModel)
                2 -> TerminalScreen(viewModel)
                3 -> OptimizationScreen(viewModel)
            }
        }
    }
}
