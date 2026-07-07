package com.localadb.manager.adb

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

sealed interface AdbConnectionState {
    data object Disconnected : AdbConnectionState
    data object SearchingForPairing : AdbConnectionState
    data object Pairing : AdbConnectionState
    data object Connecting : AdbConnectionState
    data object Connected : AdbConnectionState
    data class Error(val message: String) : AdbConnectionState
}

enum class AppFilter { ALL, USER, SYSTEM }

data class InstalledApp(
    val packageName: String,
    val displayName: String,
    val versionName: String,
    val sizeBytes: Long?,
    val installedAt: String?,
    val isSystem: Boolean = false,
)

data class DeviceInfo(
    val model: String, val androidVersion: String, val apiLevel: String,
    val cpu: String, val totalRamMb: String, val freeStorageGb: String,
    val cpuLoad: String, val batteryTemp: String, val batteryStatus: String,
    val cycleCount: String, val isRooted: String, val bootloader: String,
)

class AdbRepository(context: Context) {

    private val appContext = context.applicationContext
    private val keyStore = AdbKeyStore(appContext)
    private val pairingDiscovery = PairingDiscovery(appContext)

    private val _state = MutableStateFlow<AdbConnectionState>(AdbConnectionState.Disconnected)
    val state: StateFlow<AdbConnectionState> = _state

    private fun manager() = LocalAdbConnectionManager.getInstance(appContext)

    fun hasSavedPairing(): Boolean = keyStore.hasKeys()

    suspend fun reconnect(): Boolean = withContext(Dispatchers.IO) {
        _state.value = AdbConnectionState.Connecting
        try {
            manager().connectTls(appContext, 10_000)
            _state.value = AdbConnectionState.Connected
            true
        } catch (e: Exception) {
            _state.value = AdbConnectionState.Error("Не удалось подключиться. (${e.message})")
            false
        }
    }

    suspend fun pairAndConnect(pairingCode: String): Boolean = withContext(Dispatchers.IO) {
        _state.value = AdbConnectionState.SearchingForPairing
        try {
            val service = withTimeout(30_000) { pairingDiscovery.discover().first() }
            _state.value = AdbConnectionState.Pairing
            manager().pair(service.host, service.port, pairingCode)
            reconnect()
        } catch (e: TimeoutCancellationException) {
            _state.value = AdbConnectionState.Error("Не нашли экран сопряжения за 30 секунд. Откройте «Сопряжение по коду» и повторите.")
            false
        } catch (e: Exception) {
            _state.value = AdbConnectionState.Error("Не удалось сопрячься. (${e.message})")
            false
        }
    }

    // ── Общий push файла через ADB sync (используется для install и installMultiple) ──
    private fun pushFileViaSync(localFile: File, remotePath: String): Result<Unit> = runCatching {
        val syncStream = manager().openStream("sync:")
        val syncIn = syncStream.openInputStream()
        val syncOut = syncStream.openOutputStream()

        val pathWithMode = "$remotePath,0644"
        val pathBytes = pathWithMode.toByteArray(Charsets.UTF_8)
        syncOut.write("SEND".toByteArray())
        syncOut.write(pathBytes.size.toLittleEndian())
        syncOut.write(pathBytes)

        val buf = ByteArray(65536)
        localFile.inputStream().use { fileIn ->
            var n: Int
            while (fileIn.read(buf).also { n = it } > 0) {
                syncOut.write("DATA".toByteArray())
                syncOut.write(n.toLittleEndian())
                syncOut.write(buf, 0, n)
            }
        }

        val mtime = (localFile.lastModified() / 1000).toInt()
        syncOut.write("DONE".toByteArray())
        syncOut.write(mtime.toLittleEndian())
        syncOut.flush()

        // Читаем OKAY/FAIL после DONE
        val responseId = ByteArray(4).also { syncIn.read(it) }
        val responseLenBytes = ByteArray(4).also { syncIn.read(it) }
        val responseLen = responseLenBytes.fromLittleEndian()
        if (String(responseId) == "FAIL" && responseLen > 0) {
            val errorBytes = ByteArray(responseLen.coerceAtMost(4096)).also { syncIn.read(it) }
            throw Exception("ADB sync FAIL: ${String(errorBytes)}")
        }
        runCatching { syncStream.close() }
    }

    suspend fun installApk(apkFile: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val remotePath = "/data/local/tmp/adb_i_${System.currentTimeMillis()}.apk"
            pushFileViaSync(apkFile, remotePath).getOrThrow()
            var result = runShell("pm install -r \"$remotePath\"", allowReconnect = false).getOrThrow()
            if (result.contains("INSTALL_FAILED_DEPRECATED_SDK_VERSION", ignoreCase = true)) {
                result = runShell("pm install -r --bypass-low-target-sdk-block \"$remotePath\"", allowReconnect = false).getOrThrow()
            }
            runCatching { runShell("rm -f \"$remotePath\"") }
            result
        }
    }

    /** Установка Split APK: пушим каждый файл и запускаем pm install-multiple. */
    suspend fun installMultipleApks(apkFiles: List<File>): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val remotePaths = apkFiles.mapIndexed { idx, file ->
                val path = "/data/local/tmp/split_${idx}_${System.currentTimeMillis()}.apk"
                pushFileViaSync(file, path).getOrThrow()
                path
            }
            val pathsArg = remotePaths.joinToString(" ") { "\"$it\"" }
            val result = runShell("pm install-multiple -r $pathsArg", allowReconnect = false).getOrThrow()
            remotePaths.forEach { runCatching { runShell("rm -f \"$it\"") } }
            result
        }
    }

    suspend fun uninstallPackage(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        runShell("pm uninstall \"$packageName\"")
    }

    suspend fun disablePackage(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        runShell("pm uninstall -k --user 0 $packageName")
    }

    /**
     * Возвращает пакет: пробует несколько команд, т.к. разные прошивки возвращают разные строки.
     * pm install-existing → cmd package install-existing → pm enable (последний запасной)
     */
    suspend fun enablePackage(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        var result = runShell("pm install-existing --user 0 $packageName")
        val out1 = result.getOrNull().orEmpty()
        if (out1.contains("Failure", ignoreCase = true) || out1.isBlank()) {
            val r2 = runShell("cmd package install-existing $packageName")
            val out2 = r2.getOrNull().orEmpty()
            if (out2.contains("Failure", ignoreCase = true) || out2.isBlank()) {
                result = runShell("pm enable --user 0 $packageName")
            } else {
                result = r2
            }
        }
        result
    }

    /** Используем pm list packages -d напрямую — единственный надёжный способ. */
    suspend fun getDisabledPackages(): Result<Set<String>> = withContext(Dispatchers.IO) {
        runCatching {
            runShell("pm list packages -d").getOrThrow()
                .lineSequence()
                .filter { it.startsWith("package:") }
                .map { it.removePrefix("package:").trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }
    }

    suspend fun listInstalledAppsDetailed(filter: AppFilter = AppFilter.ALL): Result<List<InstalledApp>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val pm = appContext.packageManager
                val flagArg = when (filter) {
                    AppFilter.USER -> "-3"
                    AppFilter.SYSTEM -> "-s"
                    AppFilter.ALL -> ""
                }
                val listOutput = runShell("pm list packages $flagArg -f").getOrThrow()

                val pkgToPath = LinkedHashMap<String, String>()
                listOutput.lineSequence().forEach { rawLine ->
                    val line = rawLine.trim().removePrefix("package:")
                    val idx = line.lastIndexOf('=')
                    if (idx > 0) {
                        val path = line.substring(0, idx)
                        val pkg = line.substring(idx + 1)
                        if (pkg.isNotEmpty() && path.isNotEmpty()) pkgToPath[pkg] = path
                    }
                }

                val sizeByPath = mutableMapOf<String, Long>()
                if (pkgToPath.isNotEmpty()) {
                    val pathsArg = pkgToPath.values.joinToString(" ") { "\"$it\"" }
                    runShell("stat -c '%n|%s' $pathsArg").getOrNull().orEmpty()
                        .lineSequence().forEach { line ->
                            val parts = line.trim().split("|")
                            if (parts.size == 2) parts[1].toLongOrNull()?.let { sizeByPath[parts[0]] = it }
                        }
                }

                pkgToPath.keys.sorted().map { pkg ->
                    // Получаем отображаемое имя — для системных пакетов часто падает NameNotFoundException,
                    // поэтому пробуем флаг MATCH_ALL и другие fallback'и
                    val displayName = runCatching {
                        val info = pm.getApplicationInfo(pkg, 0)
                        pm.getApplicationLabel(info).toString().takeIf { it.isNotBlank() && it != pkg }
                    }.getOrNull() ?: runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg,
                            android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS)).toString()
                    }.getOrNull() ?: pkg

                    val infoText = runShell("dumpsys package $pkg | grep -E 'versionName=|firstInstallTime=' | head -2")
                        .getOrNull().orEmpty()
                    val versionName = Regex("versionName=(\\S+)").find(infoText)?.groupValues?.get(1) ?: "—"
                    val installedAt = Regex("firstInstallTime=(.+)").find(infoText)?.groupValues?.get(1)?.trim()
                    val path = pkgToPath[pkg]
                    val isSystem = path?.let {
                        !it.startsWith("/data/app/") && !it.startsWith("/data/user/")
                    } ?: (filter == AppFilter.SYSTEM)

                    InstalledApp(packageName = pkg, displayName = displayName,
                        versionName = versionName, sizeBytes = path?.let { sizeByPath[it] },
                        installedAt = installedAt, isSystem = isSystem)
                }
            }
        }

    suspend fun getAppPermissions(packageName: String): Result<Map<String, List<String>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val output = runShell("dumpsys package $packageName").getOrThrow()
                val granted = output.lineSequence()
                    .filter { it.contains("permission.") && it.contains("granted=true") }
                    .mapNotNull { line -> Regex("([a-zA-Z_.]+\\.permission\\.\\w+)").find(line)?.groupValues?.get(1) }
                    .map { it.substringAfterLast('.') }
                    .distinct().toList()

                val result = LinkedHashMap<String, MutableList<String>>()
                granted.forEach { perm ->
                    result.getOrPut(PERMISSION_CATEGORY_MAP[perm] ?: "🔧 Прочее") { mutableListOf() }.add(perm)
                }
                result.mapValues { it.value.sorted() }
            }
        }

    suspend fun getDeviceInfo(): Result<DeviceInfo> = withContext(Dispatchers.IO) {
        runCatching {
            fun shell(cmd: String) = runShell(cmd).getOrNull()?.trim() ?: "—"
            val model = shell("getprop ro.product.model")
            val android = shell("getprop ro.build.version.release")
            val api = shell("getprop ro.build.version.sdk")
            val cpu = shell("getprop ro.hardware").ifBlank {
                shell("cat /proc/cpuinfo | grep 'Hardware' | head -1 | cut -d: -f2").trim()
            }
            val ramRaw = shell("cat /proc/meminfo | grep MemTotal | awk '{print \$2}'")
            val ramMb = ramRaw.toLongOrNull()?.let { "${it / 1024} МБ" } ?: "—"
            val storageRaw = shell("df /data | tail -1 | awk '{print \$4}'")
            val storageGb = storageRaw.toLongOrNull()?.let { "%.1f ГБ".format(it / 1024.0 / 1024.0) } ?: "—"
            val load = shell("cat /proc/loadavg | awk '{print \$1\" / \"\$2\" / \"\$3}'")
            val tempRaw = shell("cat /sys/class/power_supply/battery/temp")
            val temp = tempRaw.toIntOrNull()?.let { "${it / 10.0}°C" } ?: "—"
            val battStatus = shell("dumpsys battery | grep 'level\\|status\\|health' | head -3")
            val cycles = shell("cat /sys/class/power_supply/battery/cycle_count")
            val rooted = if (shell("id").contains("root", ignoreCase = true)) "Root" else "Нет root"
            val bootloader = shell("getprop ro.boot.verifiedbootstate").ifBlank { shell("getprop ro.secure") }
            DeviceInfo(model, android, api, cpu, ramMb, storageGb, load, temp, battStatus, cycles, rooted, bootloader)
        }
    }

    suspend fun runRawCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        runShell(command)
    }

    private fun runShell(command: String, allowReconnect: Boolean = true): Result<String> {
        return try {
            val stream = manager().openStream("shell:$command")
            val output = stream.openInputStream().bufferedReader().readText()
            runCatching { stream.close() }
            Result.success(output)
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            val isConnErr = msg.contains("Stream closed", ignoreCase = true) ||
                msg.contains("Broken pipe", ignoreCase = true) ||
                msg.contains("Connection reset", ignoreCase = true)
            if (allowReconnect && isConnErr) {
                return try {
                    manager().connectTls(appContext, 10_000)
                    val s2 = manager().openStream("shell:$command")
                    val out2 = s2.openInputStream().bufferedReader().readText()
                    runCatching { s2.close() }
                    Result.success(out2)
                } catch (e2: Exception) { Result.failure(e2) }
            }
            Result.failure(e)
        }
    }

    private fun Int.toLittleEndian(): ByteArray = byteArrayOf(
        (this and 0xFF).toByte(), ((this shr 8) and 0xFF).toByte(),
        ((this shr 16) and 0xFF).toByte(), ((this shr 24) and 0xFF).toByte())

    private fun ByteArray.fromLittleEndian(): Int =
        (this[0].toInt() and 0xFF) or ((this[1].toInt() and 0xFF) shl 8) or
            ((this[2].toInt() and 0xFF) shl 16) or ((this[3].toInt() and 0xFF) shl 24)

    private val PERMISSION_CATEGORY_MAP = mapOf(
        "INTERNET" to "📡 Сеть", "ACCESS_NETWORK_STATE" to "📡 Сеть",
        "ACCESS_WIFI_STATE" to "📡 Сеть", "CHANGE_NETWORK_STATE" to "📡 Сеть",
        "ACCESS_FINE_LOCATION" to "📍 Геолокация", "ACCESS_COARSE_LOCATION" to "📍 Геолокация",
        "ACCESS_BACKGROUND_LOCATION" to "📍 Геолокация",
        "CAMERA" to "📷 Камера", "RECORD_AUDIO" to "🎤 Микрофон",
        "READ_CONTACTS" to "👥 Контакты", "WRITE_CONTACTS" to "👥 Контакты",
        "READ_EXTERNAL_STORAGE" to "📂 Хранилище", "WRITE_EXTERNAL_STORAGE" to "📂 Хранилище",
        "MANAGE_EXTERNAL_STORAGE" to "📂 Хранилище", "READ_MEDIA_IMAGES" to "📂 Хранилище",
        "READ_MEDIA_VIDEO" to "📂 Хранилище", "READ_MEDIA_AUDIO" to "📂 Хранилище",
        "READ_PHONE_STATE" to "📞 Телефон", "CALL_PHONE" to "📞 Телефон",
        "READ_CALL_LOG" to "📞 Телефон", "READ_SMS" to "📨 SMS",
        "SEND_SMS" to "📨 SMS", "RECEIVE_SMS" to "📨 SMS",
        "READ_CALENDAR" to "📅 Календарь", "WRITE_CALENDAR" to "📅 Календарь",
        "POST_NOTIFICATIONS" to "🔔 Уведомления", "RECEIVE_BOOT_COMPLETED" to "🔔 Уведомления",
        "BLUETOOTH" to "🔵 Bluetooth", "BLUETOOTH_CONNECT" to "🔵 Bluetooth",
        "BLUETOOTH_SCAN" to "🔵 Bluetooth", "BODY_SENSORS" to "❤️ Датчики",
        "ACTIVITY_RECOGNITION" to "🏃 Активность",
        "USE_BIOMETRIC" to "🔐 Биометрия", "USE_FINGERPRINT" to "🔐 Биометрия",
    )

    fun forgetPairing() {
        keyStore.clear()
        _state.value = AdbConnectionState.Disconnected
    }
}
