package com.localadb.manager.adb

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Хранит пару ключей ADB (приватный RSA-ключ + самоподписанный сертификат) во внутреннем
 * хранилище приложения — той же папке /data/data/<package>/files, которая недоступна другим
 * приложениям без root. Это тот же принцип, что и файл ~/.android/adbkey у обычного adb:
 * сгенерировали один раз — и переиспользуем при каждом подключении, чтобы не сопрягаться заново.
 */
class AdbKeyStore(context: Context) {

    private val keyFile = File(context.filesDir, "adb_key.pk8")
    private val certFile = File(context.filesDir, "adb_cert.der")

    /** Есть ли уже сохранённая пара ключей (то есть раньше уже было успешное сопряжение). */
    fun hasKeys(): Boolean = keyFile.exists() && certFile.exists()

    /** Загружает существующую пару ключей или создаёт и сохраняет новую. */
    fun loadOrCreate(): Pair<PrivateKey, Certificate> {
        if (hasKeys()) {
            val loaded = runCatching { load() }.getOrNull()
            if (loaded != null) return loaded
            // Файлы оказались повреждены — сгенерируем ключи заново
        }
        return createAndSave()
    }

    private fun load(): Pair<PrivateKey, Certificate> {
        val privateKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(certFile.inputStream())
        return privateKey to certificate
    }

    private fun createAndSave(): Pair<PrivateKey, Certificate> {
        val keyPair = generateRsaKeyPair()
        val certificate = generateSelfSignedCertificate(keyPair)
        keyFile.writeBytes(keyPair.private.encoded)
        certFile.writeBytes(certificate.encoded)
        return keyPair.private to certificate
    }

    private fun generateRsaKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        return generator.generateKeyPair()
    }

    private fun generateSelfSignedCertificate(keyPair: KeyPair): X509Certificate {
        val subject = X500Name("CN=LocalADBManager")
        val serial = BigInteger.valueOf(System.currentTimeMillis())
        val notBefore = Date()
        val notAfter = Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(365L * 10))

        val builder = JcaX509v3CertificateBuilder(
            subject, serial, notBefore, notAfter, subject, keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA512withRSA").build(keyPair.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    /** Удаляет сохранённые ключи. После этого потребуется сопряжение заново. */
    fun clear() {
        keyFile.delete()
        certFile.delete()
    }
}
