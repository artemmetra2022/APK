package com.localadb.manager.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.localadb.manager.adb.AdbConnectionState

@Composable
fun PairingScreen(
    state: AdbConnectionState,
    onOpenDeveloperSettings: () -> Unit,
    onSubmitCode: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    val isBusy = state is AdbConnectionState.SearchingForPairing ||
        state is AdbConnectionState.Pairing ||
        state is AdbConnectionState.Connecting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            "Первое подключение",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "1. Откройте настройки разработчика и включите «Отладка по Wi-Fi».\n" +
                "2. Откройте «Сопряжение по коду».\n" +
                "3. Введите шестизначный код ниже, пока экран с кодом открыт на телефоне.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onOpenDeveloperSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Открыть настройки разработчика")
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { new -> if (new.length <= 6) code = new.filter(Char::isDigit) },
            label = { Text("Код сопряжения") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSubmitCode(code) },
            enabled = code.length == 6 && !isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Сопрячь устройство")
        }
        Spacer(Modifier.height(24.dp))
        StatusIndicator(state)
    }
}

@Composable
private fun StatusIndicator(state: AdbConnectionState) {
    when (state) {
        is AdbConnectionState.SearchingForPairing -> StatusRow("Ищу сервис сопряжения в сети…")
        is AdbConnectionState.Pairing -> StatusRow("Сопряжение…")
        is AdbConnectionState.Connecting -> StatusRow("Подключение…")
        is AdbConnectionState.Error -> Text(
            state.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        else -> {}
    }
}

@Composable
private fun StatusRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(text)
    }
}
