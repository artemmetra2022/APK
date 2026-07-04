package com.localadb.manager.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.localadb.manager.adb.isDangerousCommand

@Composable
fun TerminalScreen(viewModel: MainViewModel) {
    var command by remember { mutableStateOf("") }
    val history by viewModel.terminalHistory.collectAsState()
    val isRunning by viewModel.isRunningCommand.collectAsState()
    var pendingCommand by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    fun submit(cmd: String) {
        if (cmd.isBlank()) return
        if (isDangerousCommand(cmd)) {
            pendingCommand = cmd
        } else {
            viewModel.runTerminalCommand(cmd)
            command = ""
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "adb shell — команды выполняются от имени shell на этом устройстве",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f), state = listState) {
            items(history) { entry ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp),
                ) {
                    Text(
                        "$ ${entry.command}",
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        entry.output,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
        Row {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                placeholder = { Text("pm list packages, ls /sdcard, ...") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { submit(command) }, enabled = !isRunning) {
                Text("➤")
            }
        }
    }

    pendingCommand?.let { cmd ->
        AlertDialog(
            onDismissRequest = { pendingCommand = null },
            title = { Text("Потенциально опасная команда") },
            text = {
                Text(
                    "«$cmd»\n\nЭта команда может удалить данные или приложения. " +
                        "Выполнить всё равно?",
                )
            },
            confirmButton = {
                TextButton(onClick = {
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

    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) listState.animateScrollToItem(history.size - 1)
    }
}
