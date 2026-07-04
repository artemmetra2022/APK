package com.localadb.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localadb.manager.adb.ManufacturerProfile
import com.localadb.manager.adb.OPTIMIZATION_DATABASE
import com.localadb.manager.adb.OptimizationRisk

@Composable
fun OptimizationScreen(viewModel: MainViewModel) {
    val disabledPackages by viewModel.disabledPackages.collectAsState()
    val statuses by viewModel.optimizationStatus.collectAsState()
    var selectedManufacturer by remember { mutableStateOf<ManufacturerProfile?>(null) }

    LaunchedEffect(Unit) { viewModel.loadDisabledPackages() }

    if (selectedManufacturer == null) {
        // Экран выбора производителя
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
        ) {
            Text(
                "Оптимизация устройства",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Отключение фоновых сервисов производителя, которые потребляют память и батарею. " +
                    "Все изменения обратимы — пакеты можно включить обратно.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text("Выберите производителя:", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))

            OPTIMIZATION_DATABASE.forEach { profile ->
                val disabledCount = profile.apps.count { it.packageName in disabledPackages }
                Card(
                    onClick = { selectedManufacturer = profile },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(profile.emoji, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, fontWeight = FontWeight.Bold)
                            Text(
                                "${profile.apps.size} пакетов" +
                                    if (disabledCount > 0) " · $disabledCount отключено" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("›", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("⚠️ Нельзя безопасно отключать:", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Google Play Services, Android System, SystemUI, Bluetooth, Wi-Fi, " +
                            "Contacts Storage, Settings, Package Installer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    } else {
        // Экран со списком пакетов выбранного производителя
        val profile = selectedManufacturer!!
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { selectedManufacturer = null }) { Text("← Назад") }
                Spacer(Modifier.width(12.dp))
                Text(
                    "${profile.emoji} ${profile.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(profile.apps, key = { it.packageName }) { app ->
                    val isDisabled = app.packageName in disabledPackages
                    val status = statuses[app.packageName]

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDisabled)
                                MaterialTheme.colorScheme.surfaceVariant
                            else
                                MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(app.displayName, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.width(6.dp))
                                        // Значок уровня риска
                                        Text(
                                            if (app.risk == OptimizationRisk.LOW) "🟢" else "🟡",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        app.description,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            status?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (it.contains("✅"))
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.height(4.dp))
                            }

                            Row {
                                if (isDisabled) {
                                    Button(
                                        onClick = { viewModel.enablePackage(app.packageName) },
                                        enabled = status == null || !status.contains("…"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondary,
                                        ),
                                        modifier = Modifier.height(36.dp),
                                    ) {
                                        Text("Включить обратно", style = MaterialTheme.typography.bodySmall)
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.disablePackage(app.packageName) },
                                        enabled = status == null || !status.contains("…"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                        ),
                                        modifier = Modifier.height(36.dp),
                                    ) {
                                        Text("Отключить", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}
