package com.localadb.manager

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupData(
    val disabledPackages: Set<String>,
    val theme: String,
    val timestamp: Long,
    val deviceModel: String,
)

class BackupManager(context: Context) {

    private val backupFile = File(context.filesDir, "backup.json")

    fun save(disabledPackages: Set<String>, theme: String?, deviceModel: String): Result<String> {
        return runCatching {
            val json = JSONObject().apply {
                put("version", 1)
                put("timestamp", System.currentTimeMillis())
                put("deviceModel", deviceModel)
                put("theme", theme ?: "system")
                put("disabledPackages", JSONArray(disabledPackages.toList()))
            }
            backupFile.writeText(json.toString(2))
            val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
            "Резервная копия сохранена ($date, ${disabledPackages.size} пакетов)"
        }
    }

    fun load(): Result<BackupData> {
        return runCatching {
            if (!backupFile.exists()) throw Exception("Резервная копия не найдена")
            val json = JSONObject(backupFile.readText())
            val array = json.getJSONArray("disabledPackages")
            val packages = (0 until array.length()).map { array.getString(it) }.toSet()
            BackupData(
                disabledPackages = packages,
                theme = json.optString("theme", "system"),
                timestamp = json.optLong("timestamp", 0),
                deviceModel = json.optString("deviceModel", "—"),
            )
        }
    }

    fun hasBackup(): Boolean = backupFile.exists()

    fun getBackupInfo(): String {
        if (!backupFile.exists()) return "Резервная копия отсутствует"
        return runCatching {
            val json = JSONObject(backupFile.readText())
            val ts = json.optLong("timestamp", 0)
            val model = json.optString("deviceModel", "—")
            val count = json.getJSONArray("disabledPackages").length()
            val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ts))
            "Создана: $date\nУстройство: $model\nПакетов: $count"
        }.getOrElse { "Ошибка чтения резервной копии" }
    }
}
