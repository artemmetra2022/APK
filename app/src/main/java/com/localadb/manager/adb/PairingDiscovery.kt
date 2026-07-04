package com.localadb.manager.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Адрес и порт найденного ADB-сервиса в локальной сети. */
data class DiscoveredService(val host: String, val port: Int)

private const val SERVICE_TYPE_PAIRING = "_adb-tls-pairing._tcp"

/**
 * Ищет в локальной Wi-Fi сети сервис `_adb-tls-pairing._tcp`, который телефон транслирует
 * через mDNS, пока на экране открыт пункт «Сопряжение по коду» в настройках разработчика.
 * Как только пользователь откроет этот экран, наше приложение находит IP и порт автоматически —
 * вводить их вручную не нужно, только 6-значный код.
 */
class PairingDiscovery(private val context: Context) {

    private val nsdManager by lazy {
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    fun discover(): Flow<DiscoveredService> = callbackFlow {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Не смогли разрешить конкретный найденный сервис — просто пропускаем его
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress ?: return
                trySend(DiscoveredService(host, serviceInfo.port))
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                nsdManager.resolveService(serviceInfo, resolveListener)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(IllegalStateException("Не удалось начать поиск в сети (код $errorCode)"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                // Не критично: поиск и так завершится вместе с корутиной/экраном
            }
        }

        nsdManager.discoverServices(
            SERVICE_TYPE_PAIRING,
            NsdManager.PROTOCOL_DNS_SD,
            discoveryListener,
        )

        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        }
    }
}
