plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.localadb.manager"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.localadb.manager"
        // Wireless Debugging (пейринг по коду) появился в Android 11 — это жёсткий минимум
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Пока отключено намеренно: минификация/обфускация — отдельный источник ошибок сборки,
            // включим после того, как убедимся, что всё стабильно работает.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        // Версия компилятора Compose, совместимая с Kotlin 1.9.24
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            // Три jar-а BouncyCastle (bcpkix/bcutil/bcprov) содержат одинаковый служебный
            // файл — без исключения Android не может собрать APK (дубликат пути).
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // --- ADB через Wireless Debugging ---
    // Библиотека реализует SPAKE2-пейринг, TLS-подключение и протокол ADB на чистом Java/Kotlin,
    // специально для запуска НА Android (в отличие от большинства других ADB-библиотек).
    // https://github.com/MuntashirAkon/libadb-android
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")

    // Полноценная реализация Conscrypt (TLS-провайдер). Системная урезанная версия Android
    // на некоторых прошивках не содержит exportKeyingMaterial как доступный метод — используем
    // свою, чтобы не зависеть от скрытых API конкретного устройства.
    implementation("org.conscrypt:conscrypt-android:2.5.3")

    // Нужна для генерации самоподписанного X.509-сертификата ключа ADB
    // (без обращения к скрытым sun.security API Android).
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
}

// libadb-android транзитивно тянет другую сборку BouncyCastle (bcprov-jdk15to18),
// которая конфликтует с явно подключённой bcprov-jdk18on выше (дублирующиеся классы).
// Убираем везде "jdk15to18"-вариант, оставляем только jdk18on.
configurations.all {
    exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
    exclude(group = "org.bouncycastle", module = "bcpkix-jdk15to18")
}
