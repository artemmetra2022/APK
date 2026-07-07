package com.localadb.manager.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localadb.manager.BackupManager
import com.localadb.manager.ThemePreference
import com.localadb.manager.adb.AdbConnectionState
import com.localadb.manager.adb.AdbRepository
import com.localadb.manager.adb.AppFilter
import com.localadb.manager.adb.DeviceInfo
import com.localadb.manager.adb.InstalledApp
import com.localadb.manager.adb.OPTIMIZATION_DATABASE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class TerminalEntry(val command: String, val output: String)

data class ApkInfo(
    val packageName: String,
    val versionName: String,
    val installedVersionName: String?,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AdbRepository(application)
    private val themePreference = ThemePreference(application)
    private val backupManager = BackupManager(application)

    val connectionState: StateFlow<AdbConnectionState> = repository.state

    private val _isInstalling = MutableStateFlow(false)
    val isInstalling: StateFlow<Boolean> = _isInstalling.asStateFlow()

    private val _installResult = MutableStateFlow<String?>(null)
    val installResult: StateFlow<String?> = _installResult.asStateFlow()

    private val _isReconnecting = MutableStateFlow(false)
    val isReconnecting: StateFlow<Boolean> = _isReconnecting.asStateFlow()

    private val _darkMode = MutableStateFlow(themePreference.get())
    val darkMode: StateFlow<Boolean?> = _darkMode.asStateFlow()

    // ── Список приложений ──────────────────────────────────────────
    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _appsError = MutableStateFlow<String?>(null)
    val appsError: StateFlow<String?> = _appsError.asStateFlow()

    // Дефолт ALL — чтобы показывать все пакеты сразу
    private val _appFilter = MutableStateFlow(AppFilter.ALL)
    val appFilter: StateFlow<AppFilter> = _appFilter.asStateFlow()

    private val _appPermissions = MutableStateFlow<Map<String, Map<String, List<String>>>>(emptyMap())
    val appPermissions: StateFlow<Map<String, Map<String, List<String>>>> = _appPermissions.asStateFlow()

    private val _loadingPermissions = MutableStateFlow<Set<String>>(emptySet())
    val loadingPermissions: StateFlow<Set<String>> = _loadingPermissions.asStateFlow()

    // ── Терминал ──────────────────────────────────────────────────
    private val _terminalHistory = MutableStateFlow<List<TerminalEntry>>(emptyList())
    val terminalHistory: StateFlow<List<TerminalEntry>> = _terminalHistory.asStateFlow()

    private val _isRunningCommand = MutableStateFlow(false)
    val isRunningCommand: StateFlow<Boolean> = _isRunningCommand.asStateFlow()

    // ── Оптимизация ───────────────────────────────────────────────
    private val _disabledPackages = MutableStateFlow<Set<String>>(emptySet())
    val disabledPackages: StateFlow<Set<String>> = _disabledPackages.asStateFlow()

    private val _optimizationStatus = MutableStateFlow<Map<String, String>>(emptyMap())
    val optimizationStatus: StateFlow<Map<String, String>> = _optimizationStatus.asStateFlow()

    private val _isAutoOptimizing = MutableStateFlow(false)
    val isAutoOptimizing: StateFlow<Boolean> = _isAutoOptimizing.asStateFlow()

    // ── Установка — диалог ────────────────────────────────────────
    private val _pendingApkInfo = MutableStateFlow<ApkInfo?>(null)
    val pendingApkInfo: StateFlow<ApkInfo?> = _pendingApkInfo.asStateFlow()
    private var pendingApkFile: File? = null

    // ── Настройки ─────────────────────────────────────────────────
    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()

    private val _isLoadingDeviceInfo = MutableStateFlow(false)
    val isLoadingDeviceInfo: StateFlow<Boolean> = _isLoadingDeviceInfo.asStateFlow()

    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus.asStateFlow()

    val backupInfo: String get() = backupManager.getBackupInfo()

    // ── Проверка подписи APK ──────────────────────────────────────
    private val _signatureResult = MutableStateFlow<String?>(null)
    val signatureResult: StateFlow<String?> = _signatureResult.asStateFlow()

    init {
        if (repository.hasSavedPairing()) {
            viewModelScope.launch { repository.reconnect() }
        }
    }

    // ── Подключение ───────────────────────────────────────────────

    fun pair(code: String) { viewModelScope.launch { repository.pairAndConnect(code) } }

    fun retryConnect() {
        viewModelScope.launch {
            _isReconnecting.value = true
            repository.reconnect()
            _isReconnecting.value = false
        }
    }

    fun forgetPairing() = repository.forgetPairing()

    // ── Тема ─────────────────────────────────────────────────────

    fun setDarkMode(value: Boolean?) {
        themePreference.set(value)
        _darkMode.value = value
    }

    // ── Приложения ────────────────────────────────────────────────

    fun setAppFilter(filter: AppFilter) {
        _appFilter.value = filter
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            _appsError.value = null
            repository.listInstalledAppsDetailed(_appFilter.value).fold(
                onSuccess = { _apps.value = it },
                onFailure = { _appsError.value = "Ошибка: ${it.message}" },
            )
            _isLoadingApps.value = false
        }
    }

    fun uninstallApp(packageName: String) {
        viewModelScope.launch {
            repository.uninstallPackage(packageName).fold(
                onSuccess = { _apps.value = _apps.value.filterNot { it.packageName == packageName } },
                onFailure = { _appsError.value = "Не удалось удалить: ${it.message}" },
            )
        }
    }

    fun loadAppPermissions(packageName: String) {
        if (_appPermissions.value.containsKey(packageName)) return
        viewModelScope.launch {
            _loadingPermissions.value = _loadingPermissions.value + packageName
            repository.getAppPermissions(packageName).onSuccess { perms ->
                _appPermissions.value = _appPermissions.value + (packageName to perms)
            }
            _loadingPermissions.value = _loadingPermissions.value - packageName
        }
    }

    // ── Терминал ──────────────────────────────────────────────────

    fun runTerminalCommand(command: String) {
        viewModelScope.launch {
            _isRunningCommand.value = true
            val output = repository.runRawCommand(command).fold(
                onSuccess = { it.ifBlank { "(пусто)" } },
                onFailure = { "Ошибка: ${it.message}" },
            )
            _terminalHistory.value = _terminalHistory.value + TerminalEntry(command, output)
            _isRunningCommand.value = false
        }
    }

    fun clearTerminalHistory() { _terminalHistory.value = emptyList() }

    /** Добавляет запись в историю без отправки команды в ADB (для /help, /clear). */
    fun addLocalEntry(command: String, output: String) {
        _terminalHistory.value = _terminalHistory.value + TerminalEntry(command, output)
    }

    // ── Оптимизация ───────────────────────────────────────────────

    fun loadDisabledPackages() {
        viewModelScope.launch {
            repository.getDisabledPackages().onSuccess { _disabledPackages.value = it }
        }
    }

    private suspend fun refreshDisabledPackages() {
        repository.getDisabledPackages().onSuccess { _disabledPackages.value = it }
    }

    fun disablePackage(packageName: String) {
        viewModelScope.launch {
            _optimizationStatus.value = _optimizationStatus.value + (packageName to "Отключаю…")
            val out = repository.disablePackage(packageName).getOrNull().orEmpty()
            val ok = !out.contains("Failure", ignoreCase = true) && !out.contains("Exception", ignoreCase = true)
            _optimizationStatus.value = _optimizationStatus.value +
                (packageName to if (ok) "Отключено ✅" else "Ошибка: ${out.ifBlank { "—" }}")
            refreshDisabledPackages()
        }
    }

    fun enablePackage(packageName: String) {
        viewModelScope.launch {
            _optimizationStatus.value = _optimizationStatus.value + (packageName to "Включаю…")
            val out = repository.enablePackage(packageName).getOrNull().orEmpty()
            val ok = !out.contains("Failure", ignoreCase = true) && !out.contains("Exception", ignoreCase = true)
            _optimizationStatus.value = _optimizationStatus.value +
                (packageName to if (ok) "Включено ✅" else "Ошибка: ${out.ifBlank { "—" }}")
            refreshDisabledPackages()
        }
    }

    fun batchDisable(packages: List<String>) {
        viewModelScope.launch {
            _optimizationStatus.value = _optimizationStatus.value + packages.associateWith { "Отключаю…" }
            packages.forEach { pkg ->
                val out = repository.disablePackage(pkg).getOrNull().orEmpty()
                val ok = !out.contains("Failure", ignoreCase = true)
                _optimizationStatus.value = _optimizationStatus.value +
                    (pkg to if (ok) "Отключено ✅" else "Ошибка: ${out.ifBlank { "—" }}")
            }
            refreshDisabledPackages()
        }
    }

    /**
     * Автооптимизация — выполняется INLINE в одной корутине без вызова batchDisable()
     * чтобы избежать race condition между двумя корутинами.
     */
    fun autoOptimize(manufacturerName: String) {
        val profile = OPTIMIZATION_DATABASE.firstOrNull { it.name == manufacturerName } ?: return
        val toDisable = profile.apps
            .filter { it.isPriority && it.packageName !in _disabledPackages.value }
            .map { it.packageName }
        if (toDisable.isEmpty()) return
        viewModelScope.launch {
            _isAutoOptimizing.value = true
            _optimizationStatus.value = _optimizationStatus.value + toDisable.associateWith { "Отключаю…" }
            toDisable.forEach { pkg ->
                val out = repository.disablePackage(pkg).getOrNull().orEmpty()
                val ok = !out.contains("Failure", ignoreCase = true)
                _optimizationStatus.value = _optimizationStatus.value +
                    (pkg to if (ok) "Отключено ✅" else "Ошибка: ${out.ifBlank { "—" }}")
            }
            refreshDisabledPackages()
            _isAutoOptimizing.value = false
        }
    }

    fun cancelAutoOptimize(manufacturerName: String) {
        val profile = OPTIMIZATION_DATABASE.firstOrNull { it.name == manufacturerName } ?: return
        val toEnable = profile.apps
            .filter { it.isPriority && it.packageName in _disabledPackages.value }
            .map { it.packageName }
        if (toEnable.isEmpty()) return
        viewModelScope.launch {
            _isAutoOptimizing.value = true
            toEnable.forEach { pkg ->
                _optimizationStatus.value = _optimizationStatus.value + (pkg to "Включаю…")
                val out = repository.enablePackage(pkg).getOrNull().orEmpty()
                val ok = !out.contains("Failure", ignoreCase = true)
                _optimizationStatus.value = _optimizationStatus.value +
                    (pkg to if (ok) "Включено ✅" else "Ошибка: ${out.ifBlank { "—" }}")
            }
            refreshDisabledPackages()
            _isAutoOptimizing.value = false
        }
    }

    // ── Установка APK ────────────────────────────────────────────

    /**
     * Ключевое исправление: разделяем IO-операции и обновление StateFlow на главном потоке.
     * Раньше _pendingApkInfo обновлялся внутри withContext(IO) что могло не вызывать
     * recomposition вовремя. Теперь withContext возвращает значение, и мы обновляем
     * StateFlow уже в контексте main-потока (viewModelScope по умолчанию — Main).
     */
    fun onApkPicked(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _installResult.value = null
            _isInstalling.value = true

            // Шаг 1: Копируем файл (IO)
            val tempFile = withContext(Dispatchers.IO) {
                runCatching {
                    val f = File(getApplication<Application>().cacheDir, "install_temp_${System.currentTimeMillis()}.apk")
                    contentResolver.openInputStream(uri)?.use { input ->
                        f.outputStream().use { input.copyTo(it) }
                    } ?: throw Exception("Не удалось открыть файл")
                    f
                }.getOrNull()
            }

            if (tempFile == null || !tempFile.exists()) {
                _installResult.value = "Ошибка: не удалось скопировать APK"
                _isInstalling.value = false
                return@launch
            }

            pendingApkFile = tempFile

            // Шаг 2: Читаем метаданные (IO) — возвращаем ApkInfo или null
            val apkInfo: ApkInfo? = withContext(Dispatchers.IO) {
                runCatching {
                    val pm = getApplication<Application>().packageManager
                    @Suppress("DEPRECATION")
                    val archiveInfo = pm.getPackageArchiveInfo(tempFile.absolutePath, 0)
                        ?: return@runCatching null

                    val newPkg = archiveInfo.packageName ?: return@runCatching null
                    val newVersion = archiveInfo.versionName ?: "—"

                    // Проверяем, установлено ли уже
                    val installedVersion = runCatching {
                        pm.getPackageInfo(newPkg, 0).versionName
                    }.getOrNull()

                    if (installedVersion != null) {
                        ApkInfo(newPkg, newVersion, installedVersion)
                    } else null
                }.getOrNull()
            }

            // Шаг 3: Обновляем UI на главном потоке (viewModelScope = Main)
            if (apkInfo != null) {
                // Показываем диалог — устанавливаем на ГЛАВНОМ потоке
                _pendingApkInfo.value = apkInfo
                _isInstalling.value = false
            } else {
                // Сразу устанавливаем
                proceedInstall()
            }
        }
    }

    fun confirmInstall() {
        _pendingApkInfo.value = null
        _isInstalling.value = true
        viewModelScope.launch { proceedInstall() }
    }

    fun cancelInstall() {
        _pendingApkInfo.value = null
        pendingApkFile?.delete()
        pendingApkFile = null
        _isInstalling.value = false
    }

    private suspend fun proceedInstall() {
        val file = pendingApkFile ?: run {
            _installResult.value = "Ошибка: файл не найден"
            _isInstalling.value = false
            return
        }
        val outcome = withContext(Dispatchers.IO) {
            runCatching { repository.installApk(file).getOrThrow() }
        }
        runCatching { file.delete() }
        pendingApkFile = null
        _installResult.value = outcome.fold(
            onSuccess = { out ->
                when {
                    out.contains("Success", ignoreCase = true) -> "Установлено успешно ✅"
                    out.contains("Failure", ignoreCase = true) -> "Ошибка: ${out.trim()}"
                    else -> "Результат: ${out.trim().ifBlank { "готово" }}"
                }
            },
            onFailure = { "Ошибка: ${it.message}" },
        )
        _isInstalling.value = false
    }

    // ── Split APK ─────────────────────────────────────────────────

    fun installSplitApks(uris: List<Uri>, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _installResult.value = null
            _isInstalling.value = true
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val cacheDir = getApplication<Application>().cacheDir
                    val tempFiles = uris.mapIndexed { idx, uri ->
                        File(cacheDir, "split_${idx}_${System.currentTimeMillis()}.apk").also { f ->
                            contentResolver.openInputStream(uri)?.use { input ->
                                f.outputStream().use { input.copyTo(it) }
                            } ?: throw Exception("Не удалось открыть файл $idx")
                        }
                    }
                    val result = repository.installMultipleApks(tempFiles).getOrThrow()
                    tempFiles.forEach { runCatching { it.delete() } }
                    result
                }
            }
            _installResult.value = outcome.fold(
                onSuccess = { if (it.contains("Success", ignoreCase = true)) "Split APK установлен ✅" else it.trim() },
                onFailure = { "Ошибка Split: ${it.message}" },
            )
            _isInstalling.value = false
        }
    }

    // ── Проверка подписи APK ──────────────────────────────────────

    fun verifyApkSignature(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _signatureResult.value = "Проверяю подпись…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val pm = getApplication<Application>().packageManager
                    val f = File(getApplication<Application>().cacheDir, "verify_${System.currentTimeMillis()}.apk")
                    contentResolver.openInputStream(uri)?.use { it.copyTo(f.outputStream()) }
                        ?: throw Exception("Не удалось открыть файл")

                    @Suppress("DEPRECATION")
                    val info = pm.getPackageArchiveInfo(f.absolutePath,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                            android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
                        else android.content.pm.PackageManager.GET_SIGNATURES
                    ) ?: throw Exception("Не удалось прочитать APK")

                    @Suppress("DEPRECATION")
                    val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                        info.signingInfo?.apkContentsSigners ?: emptyArray()
                    else info.signatures ?: emptyArray()

                    runCatching { f.delete() }

                    buildString {
                        appendLine("📦 Пакет: ${info.packageName}")
                        appendLine("📌 Версия: ${info.versionName}")
                        appendLine("🔏 Подписей: ${sigs.size}")
                        sigs.forEachIndexed { i, sig ->
                            runCatching {
                                val cert = java.security.cert.CertificateFactory.getInstance("X.509")
                                    .generateCertificate(java.io.ByteArrayInputStream(sig.toByteArray()))
                                    as java.security.cert.X509Certificate
                                appendLine("── Подпись ${i + 1} ──")
                                appendLine("Кому: ${cert.subjectDN.name.take(80)}")
                                appendLine("Действует до: ${cert.notAfter}")
                                val fp = java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(sig.toByteArray())
                                    .joinToString(":") { "%02X".format(it) }
                                appendLine("SHA-256: ${fp.take(47)}…")
                            }
                        }
                    }
                }
            }
            _signatureResult.value = result.getOrElse { "Ошибка: ${it.message}" }
        }
    }

    fun clearSignatureResult() { _signatureResult.value = null }

    // ── Настройки ─────────────────────────────────────────────────

    fun loadDeviceInfo() {
        viewModelScope.launch {
            _isLoadingDeviceInfo.value = true
            repository.getDeviceInfo().onSuccess { _deviceInfo.value = it }
            _isLoadingDeviceInfo.value = false
        }
    }

    fun saveBackup() {
        viewModelScope.launch {
            val model = _deviceInfo.value?.model ?: "—"
            val theme = when (_darkMode.value) { true -> "dark"; false -> "light"; null -> "system" }
            backupManager.save(_disabledPackages.value, theme, model).fold(
                onSuccess = { _backupStatus.value = it },
                onFailure = { _backupStatus.value = "Ошибка: ${it.message}" },
            )
        }
    }

    fun restoreBackup() {
        viewModelScope.launch {
            backupManager.load().fold(
                onSuccess = { data ->
                    val toEnable = _disabledPackages.value - data.disabledPackages
                    val toDisable = data.disabledPackages - _disabledPackages.value
                    toEnable.forEach { repository.enablePackage(it) }
                    toDisable.forEach { repository.disablePackage(it) }
                    setDarkMode(when (data.theme) { "dark" -> true; "light" -> false; else -> null })
                    refreshDisabledPackages()
                    _backupStatus.value = "Восстановлено: ${data.disabledPackages.size} пакетов"
                },
                onFailure = { _backupStatus.value = "Ошибка: ${it.message}" },
            )
        }
    }

    fun clearBackupStatus() { _backupStatus.value = null }
}
