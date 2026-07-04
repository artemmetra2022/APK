package com.localadb.manager.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localadb.manager.ThemePreference
import com.localadb.manager.adb.AdbConnectionState
import com.localadb.manager.adb.AdbRepository
import com.localadb.manager.adb.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Одна запись в истории терминала: команда и её вывод. */
data class TerminalEntry(val command: String, val output: String)

/** Информация об APK для диалога подтверждения установки. */
data class ApkInfo(
    val packageName: String,
    val versionName: String,
    val installedVersionName: String?, // null = не установлен
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AdbRepository(application)
    private val themePreference = ThemePreference(application)

    val connectionState: StateFlow<AdbConnectionState> = repository.state

    private val _isInstalling = MutableStateFlow(false)
    val isInstalling: StateFlow<Boolean> = _isInstalling.asStateFlow()

    private val _installResult = MutableStateFlow<String?>(null)
    val installResult: StateFlow<String?> = _installResult.asStateFlow()

    private val _isReconnecting = MutableStateFlow(false)
    val isReconnecting: StateFlow<Boolean> = _isReconnecting.asStateFlow()

    private val _darkMode = MutableStateFlow(themePreference.get())
    val darkMode: StateFlow<Boolean?> = _darkMode.asStateFlow()

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _appsError = MutableStateFlow<String?>(null)
    val appsError: StateFlow<String?> = _appsError.asStateFlow()

    private val _terminalHistory = MutableStateFlow<List<TerminalEntry>>(emptyList())
    val terminalHistory: StateFlow<List<TerminalEntry>> = _terminalHistory.asStateFlow()

    private val _isRunningCommand = MutableStateFlow(false)
    val isRunningCommand: StateFlow<Boolean> = _isRunningCommand.asStateFlow()

    // Диалог подтверждения установки
    private val _pendingApkInfo = MutableStateFlow<ApkInfo?>(null)
    val pendingApkInfo: StateFlow<ApkInfo?> = _pendingApkInfo.asStateFlow()
    private var _pendingApkFile: File? = null

    // Оптимизация — множество отключённых пакетов
    private val _disabledPackages = MutableStateFlow<Set<String>>(emptySet())
    val disabledPackages: StateFlow<Set<String>> = _disabledPackages.asStateFlow()

    private val _optimizationStatus = MutableStateFlow<Map<String, String>>(emptyMap())
    val optimizationStatus: StateFlow<Map<String, String>> = _optimizationStatus.asStateFlow()

    init {
        if (repository.hasSavedPairing()) {
            viewModelScope.launch { repository.reconnect() }
        }
    }

    fun pair(code: String) {
        viewModelScope.launch { repository.pairAndConnect(code) }
    }

    fun retryConnect() {
        viewModelScope.launch {
            _isReconnecting.value = true
            repository.reconnect()
            _isReconnecting.value = false
        }
    }

    fun forgetPairing() {
        repository.forgetPairing()
    }

    fun setDarkMode(value: Boolean?) {
        themePreference.set(value)
        _darkMode.value = value
    }

    fun loadApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            _appsError.value = null
            val result = repository.listInstalledAppsDetailed()
            result.fold(
                onSuccess = { _apps.value = it },
                onFailure = { _appsError.value = "Не удалось получить список: ${it.message}" },
            )
            _isLoadingApps.value = false
        }
    }

    fun uninstallApp(packageName: String) {
        viewModelScope.launch {
            val result = repository.uninstallPackage(packageName)
            if (result.isSuccess) {
                _apps.value = _apps.value.filterNot { it.packageName == packageName }
            } else {
                _appsError.value = "Не удалось удалить $packageName: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun runTerminalCommand(command: String) {
        viewModelScope.launch {
            _isRunningCommand.value = true
            val result = repository.runRawCommand(command)
            val output = result.fold(
                onSuccess = { it.ifBlank { "(пусто)" } },
                onFailure = { "Ошибка: ${it.message}" },
            )
            _terminalHistory.value = _terminalHistory.value + TerminalEntry(command, output)
            _isRunningCommand.value = false
        }
    }

    /** Загружает список отключённых системных пакетов. */
    fun loadDisabledPackages() {
        viewModelScope.launch {
            repository.getDisabledPackages().onSuccess { _disabledPackages.value = it }
        }
    }

    /** Отключает системный пакет (обратимо, без root). */
    fun disablePackage(packageName: String) {
        viewModelScope.launch {
            _optimizationStatus.value = _optimizationStatus.value + (packageName to "Отключаю…")
            val result = repository.disablePackage(packageName)
            val status = result.fold(
                onSuccess = { if (it.contains("Success", ignoreCase = true)) "Отключено ✅" else it.trim() },
                onFailure = { "Ошибка: ${it.message}" },
            )
            _optimizationStatus.value = _optimizationStatus.value + (packageName to status)
            if (status == "Отключено ✅") {
                _disabledPackages.value = _disabledPackages.value + packageName
            }
        }
    }

    /** Возвращает ранее отключённый системный пакет. */
    fun enablePackage(packageName: String) {
        viewModelScope.launch {
            _optimizationStatus.value = _optimizationStatus.value + (packageName to "Включаю…")
            val result = repository.enablePackage(packageName)
            val status = result.fold(
                onSuccess = { if (it.contains("installed", ignoreCase = true) || it.contains("Success", ignoreCase = true)) "Включено ✅" else it.trim() },
                onFailure = { "Ошибка: ${it.message}" },
            )
            _optimizationStatus.value = _optimizationStatus.value + (packageName to status)
            if (status == "Включено ✅") {
                _disabledPackages.value = _disabledPackages.value - packageName
            }
        }
    }

    /** Вызывается после того, как пользователь выбрал APK через системный диалог. */
    fun onApkPicked(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _installResult.value = null
            _isInstalling.value = true

            withContext(Dispatchers.IO) {
                runCatching {
                    val cacheDir = getApplication<Application>().cacheDir
                    val tempFile = File(cacheDir, "install_temp.apk")
                    val opened = contentResolver.openInputStream(uri)
                        ?: error("Не удалось открыть файл")
                    opened.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }

                    // Читаем метаданные APK до установки
                    val pm = getApplication<Application>().packageManager
                    val archiveInfo = pm.getPackageArchiveInfo(tempFile.absolutePath, 0)

                    if (archiveInfo == null) {
                        // Не удалось прочитать метаданные — ставим без диалога
                        _pendingApkFile = tempFile
                        proceedInstall()
                        return@runCatching
                    }

                    val newPkg = archiveInfo.packageName
                    val newVersion = archiveInfo.versionName ?: "неизвестно"

                    // Проверяем, установлена ли уже такая версия
                    val installedVersion = runCatching {
                        pm.getPackageInfo(newPkg, 0).versionName
                    }.getOrNull()

                    _pendingApkFile = tempFile

                    if (installedVersion == null) {
                        // Не установлено — ставим сразу без диалога
                        proceedInstall()
                    } else {
                        // Показываем диалог подтверждения
                        _pendingApkInfo.value = ApkInfo(newPkg, newVersion, installedVersion)
                        _isInstalling.value = false
                    }
                }.onFailure {
                    _installResult.value = "Ошибка: ${it.message}"
                    _isInstalling.value = false
                }
            }
        }
    }

    /** Пользователь подтвердил установку в диалоге. */
    fun confirmInstall() {
        _pendingApkInfo.value = null
        _isInstalling.value = true
        viewModelScope.launch { proceedInstall() }
    }

    /** Пользователь отменил установку в диалоге. */
    fun cancelInstall() {
        _pendingApkInfo.value = null
        _pendingApkFile?.delete()
        _pendingApkFile = null
        _isInstalling.value = false
    }

    private suspend fun proceedInstall() {
        val file = _pendingApkFile ?: run {
            _installResult.value = "Ошибка: файл APK не найден"
            _isInstalling.value = false
            return
        }
        val outcome = withContext(Dispatchers.IO) {
            runCatching { repository.installApk(file).getOrThrow() }
        }
        file.delete()
        _pendingApkFile = null
        _installResult.value = outcome.fold(
            onSuccess = { output ->
                if (output.contains("Success", ignoreCase = true)) "Установлено успешно ✅"
                else "Результат: ${output.trim()}"
            },
            onFailure = { "Ошибка установки: ${it.message}" },
        )
        _isInstalling.value = false
    }
}
