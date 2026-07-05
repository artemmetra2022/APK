package com.localadb.manager

import android.app.Application
import org.conscrypt.Conscrypt
import java.security.Security
import timber.log.Timber

/**
 * Устанавливает библиотечную (не системную/урезанную) реализацию Conscrypt как провайдер TLS
 * самым первым — это нужно для того, чтобы libadb-android мог вызвать exportKeyingMaterial
 * при TLS-пейринге ADB. Системная версия Conscrypt на части прошивок не даёт доступ к этому
 * методу как к обычному API — из-за скрытых ограничений Android.
 */
class LocalAdbApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
        // Инициализация Timber для логирования. В релизе следует заменить/ограничить дерево.
        Timber.plant(Timber.DebugTree())
    }
}
