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

/** Состояние подключения к ADB, которое отображает экран. */
sealed interface AdbConnectionState {
    data object Disconnected : AdbConnectionState
    data object SearchingForPairing : AdbConnectionState
    data object Pairing : AdbConnectionState
    data object Connecting : AdbConnectionState
    data object Connected : AdbConnectionState
    data class Error(val message: String) : AdbConnectionState
}

/** Данные об одном установленном приложении для экрана списка. */
data class InstalledApp(
    val packageName: String,
    val versionName: String,
    val sizeBytes: Long?,
    val installedAt: String?,
)

/**
 * Единая точка входа для всех операций с ADB: подключение/сопряжение и выполнение команд
 * (install/uninstall/list) через `adb shell`. UI-слой (ViewModel) работает только с этим классом
 * и не знает про детали протокола.
 */
class AdbRepository(context: Context) {

    private val appContext = context.applicationContext
    private val keyStore = AdbKeyStore(appContext)
    private val pairingDiscovery = PairingDiscovery(appContext)

    private val _state = MutableStateFlow<AdbConnectionState>(AdbConnectionState.Disconnected)
    val state: StateFlow<AdbConnectionState> = _state

    private fun manager() = LocalAdbConnectionManager.getInstance(appContext)

    /** Есть ли сохранённая пара ключей — то есть раньше уже было сопряжение. */
    fun hasSavedPairing(): Boolean = keyStore.hasKeys()

    /** Пробует переподключиться сохранёнными ключами без повторного ввода кода. */
    suspend fun reconnect(): Boolean = withContext(Dispatchers.IO) {
        _state.value = AdbConnectionState.Connecting
        try {
            // connectTls сам находит сервис _adb-tls-connect._tcp через mDNS и подключается к нему
            manager().connectTls(appContext, 10_000)
            _state.value = AdbConnectionState.Connected
            true
        } catch (e: Exception) {
            _state.value = AdbConnectionState.Error(
                "Не удалось подключиться. Проверьте, что на телефоне включена «Отладка по Wi-Fi». " +
                    "(${describeError(e)})"
            )
            false
        }
    }

    /**
     * Полный процесс первого сопряжения: ищет сервис `_adb-tls-pairing._tcp` в сети (пока на
     * экране открыт пункт «Сопряжение по коду»), сопрягается по введённому коду, затем подключается.
     */
    suspend fun pairAndConnect(pairingCode: String): Boolean = withContext(Dispatchers.IO) {
        _state.value = AdbConnectionState.SearchingForPairing
        try {
            val service = withTimeout(30_000) { pairingDiscovery.discover().first() }
            _state.value = AdbConnectionState.Pairing
            manager().pair(service.host, service.port, pairingCode)
            reconnect()
        } catch (e: TimeoutCancellationException) {
            _state.value = AdbConnectionState.Error(
                "Не нашли экран сопряжения за 30 секунд. Убедитесь, что на телефоне открыт " +
                    "пункт «Сопряжение по коду», и попробуйте снова."
            )
            false
        } catch (e: Exception) {
            _state.value = AdbConnectionState.Error(
                "Не удалось сопрячься. Проверьте код и повторите — экран с кодом в настройках " +
                    "нужно открыть заново, код одноразовый. (${describeError(e)})"
            )
            false
        }
    }

    /**
     * Устанавливает APK через ADB:
     * 1. Закачивает файл в /data/local/tmp/ через ADB sync-протокол
     * 2. Запускает `pm install -r` по этому пути
     * 3. Если Android отклонил APK из-за устаревшего targetSdk — повторяет с флагом обхода
     * 4. Удаляет временный файл
     */
    suspend fun installApk(apkFile: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val remotePath = "/data/local/tmp/install_temp_${System.currentTimeMillis()}.apk"

            // 1. push файла через ADB sync
            val pushStream = manager().openStream("sync:")
            pushStream.openOutputStream().use { out ->
                val remotePathWithMode = "$remotePath,0644"
                val pathBytes = remotePathWithMode.toByteArray()
                out.write("SEND".toByteArray())
                out.write(pathBytes.size.toLittleEndian())
                out.write(pathBytes)

                val buf = ByteArray(65536)
                apkFile.inputStream().use { input ->
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        out.write("DATA".toByteArray())
                        out.write(read.toLittleEndian())
                        out.write(buf, 0, read)
                    }
                }

                val mtime = (apkFile.lastModified() / 1000).toInt()
                out.write("DONE".toByteArray())
                out.write(mtime.toLittleEndian())
                out.flush()
            }
            runCatching { pushStream.close() }

            // 2. Первая попытка установки
            var installResult = runShell("pm install -r $remotePath").getOrThrow()

            // 3. Если Android отклонил из-за устаревшего targetSdk — повторяем с флагом обхода
            if (installResult.contains("INSTALL_FAILED_DEPRECATED_SDK_VERSION", ignoreCase = true)) {
                installResult = runShell(
                    "pm install -r --bypass-low-target-sdk-block $remotePath"
                ).getOrThrow()
            }

            // 4. Удаляем временный файл
            runShell("rm -f $remotePath")

            installResult
        }
    }

    /**
     * Отключает пакет для текущего пользователя без root.
     * Команда `pm uninstall -k --user 0` убирает приложение из профиля пользователя,
     * но сохраняет APK на системном разделе — его можно вернуть командой [enablePackage].
     */
    suspend fun disablePackage(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        runShell("pm uninstall -k --user 0 $packageName")
    }

    /**
     * Возвращает ранее отключённый системный пакет для текущего пользователя.
     */
    suspend fun enablePackage(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        runShell("pm install-existing --user 0 $packageName")
    }

    /**
     * Возвращает множество пакетов, которые отключены для текущего пользователя
     * (`pm uninstall --user 0` без `-k` или `pm disable-user`).
     */
    suspend fun getDisabledPackages(): Result<Set<String>> = withContext(Dispatchers.IO) {
        // pm list packages -u показывает все пакеты включая удалённые для пользователя,
        // а pm list packages без флагов — только активные. Разница = отключённые.
        runCatching {
            val disabled = runShell("pm list packages -u").getOrThrow()
                .lineSequence()
                .filter { it.startsWith("package:") }
                .map { it.removePrefix("package:").trim() }
                .toSet()

            val active = runShell("pm list packages").getOrThrow()
                .lineSequence()
                .filter { it.startsWith("package:") }
                .map { it.removePrefix("package:").trim() }
                .toSet()

            disabled - active
        }
    }

    private fun Int.toLittleEndian(): ByteArray = byteArrayOf(
        (this and 0xFF).toByte(),
        ((this shr 8) and 0xFF).toByte(),
        ((this shr 16) and 0xFF).toByte(),
        ((this shr 24) and 0xFF).toByte(),
    )

    suspend fun uninstallPackage(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        runShell("pm uninstall \"$packageName\"")
    }

    /** Список пакетов сторонних приложений (без системных) — только имена, для обратной совместимости. */
    suspend fun listInstalledPackages(): Result<List<String>> = withContext(Dispatchers.IO) {
        runShell("pm list packages -3").map { output ->
            output.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("package:") }
                .map { it.removePrefix("package:") }
                .filter { it.isNotEmpty() }
                .sorted()
                .toList()
        }
    }

    /**
     * Подробный список сторонних приложений: имя пакета, версия, размер APK, дата установки.
     * Делает 3 обращения к устройству независимо от количества приложений (+1 на пакет для версии),
     * а не одно гигантское — так проще парсить надёжно, без сложных shell-скриптов в одну строку.
     */
    suspend fun listInstalledAppsDetailed(): Result<List<InstalledApp>> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Пакеты + пути к их APK
            val listOutput = runShell("pm list packages -3 -f").getOrThrow()
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

            // 2. Размеры всех APK одной командой (stat поддерживает несколько путей сразу)
            val sizeByPath = mutableMapOf<String, Long>()
            if (pkgToPath.isNotEmpty()) {
                val pathsArg = pkgToPath.values.joinToString(" ") { "\"$it\"" }
                val statOutput = runShell("stat -c '%n|%s' $pathsArg").getOrNull().orEmpty()
                statOutput.lineSequence().forEach { line ->
                    val parts = line.trim().split("|")
                    if (parts.size == 2) {
                        parts[1].toLongOrNull()?.let { size -> sizeByPath[parts[0]] = size }
                    }
                }
            }

            // 3. Версия и дата установки — отдельным запросом на пакет (надёжнее, чем один общий парсинг)
            pkgToPath.keys.sorted().map { pkg ->
                val info = runShell("dumpsys package $pkg | grep -E 'versionName=|firstInstallTime='")
                    .getOrNull().orEmpty()
                val versionName = Regex("versionName=(\\S+)").find(info)?.groupValues?.get(1) ?: "—"
                val installedAt = Regex("firstInstallTime=(.+)").find(info)?.groupValues?.get(1)?.trim()
                val path = pkgToPath[pkg]
                InstalledApp(
                    packageName = pkg,
                    versionName = versionName,
                    sizeBytes = path?.let { sizeByPath[it] },
                    installedAt = installedAt,
                )
            }
        }
    }

    /** Выполняет произвольную команду ADB shell — используется экраном терминала. */
    suspend fun runRawCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        runShell(command)
    }

    private fun runShell(command: String): Result<String> {
        return try {
            val stream = manager().openStream("shell:$command")
            val output = stream.openInputStream().bufferedReader().readText()
            runCatching { stream.close() }
            Result.success(output)
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            // "Stream closed" / "Broken pipe" — соединение разорвалось.
            // Пробуем переподключиться один раз и повторить команду.
            if (msg.contains("Stream closed", ignoreCase = true) ||
                msg.contains("Broken pipe", ignoreCase = true) ||
                msg.contains("closed", ignoreCase = true)
            ) {
                return try {
                    manager().connectTls(appContext, 10_000)
                    val stream2 = manager().openStream("shell:$command")
                    val output2 = stream2.openInputStream().bufferedReader().readText()
                    runCatching { stream2.close() }
                    Result.success(output2)
                } catch (e2: Exception) {
                    Result.failure(e2)
                }
            }
            Result.failure(e)
        }
    }

    private fun describeError(e: Exception): String = e.message ?: (e::class.simpleName ?: "неизвестная ошибка")

    /** Забывает сохранённое сопряжение — при следующем запуске потребуется ввести код заново. */
    fun forgetPairing() {
        keyStore.clear()
        _state.value = AdbConnectionState.Disconnected
    }
}
