package com.localadb.manager.adb

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.security.PrivateKey
import java.security.cert.Certificate

/**
 * Конкретная реализация [AbsAdbConnectionManager] из библиотеки libadb-android.
 * Библиотека сама реализует пейринг (SPAKE2), TLS-подключение и протокол ADB —
 * здесь нужно только сообщить ей, какие ключи использовать.
 *
 * ВАЖНО: если Android Studio не найдёт класс `io.github.muntashirakon.adb.AbsAdbConnectionManager`
 * после синхронизации Gradle — это, скорее всего, означает, что фактическое имя пакета библиотеки
 * немного отличается. Наведите курсор на подчёркнутый импорт и нажмите Alt+Enter (Quick Fix) —
 * Android Studio предложит правильный импорт автоматически, если класс есть в зависимостях.
 */
class LocalAdbConnectionManager private constructor(
    private val privateKey: PrivateKey,
    private val certificate: Certificate,
) : AbsAdbConnectionManager() {

    init {
        setApi(Build.VERSION.SDK_INT)
    }

    override fun getPrivateKey(): PrivateKey = privateKey

    override fun getCertificate(): Certificate = certificate

    override fun getDeviceName(): String = "LocalADBManager"

    companion object {
        @Volatile
        private var instance: LocalAdbConnectionManager? = null

        fun getInstance(context: Context): LocalAdbConnectionManager {
            val existing = instance
            if (existing != null) return existing
            synchronized(this) {
                val existingAfterLock = instance
                if (existingAfterLock != null) return existingAfterLock
                val (key, cert) = AdbKeyStore(context.applicationContext).loadOrCreate()
                val created = LocalAdbConnectionManager(key, cert)
                instance = created
                return created
            }
        }
    }
}
