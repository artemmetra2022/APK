package com.localadb.manager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.localadb.manager.ui.LocalAdbManagerApp
import com.localadb.manager.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Одиночный APK
    private val pickApkLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.onApkPicked(it, contentResolver) }
    }

    // Несколько файлов для Split APK
    private val pickSplitApkLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.installSplitApks(uris, contentResolver)
    }

    // Проверка подписи — один APK
    private val pickApkForVerifyLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.verifyApkSignature(it, contentResolver) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocalAdbManagerApp(
                viewModel = viewModel,
                onPickApk = {
                    pickApkLauncher.launch(arrayOf("application/vnd.android.package-archive"))
                },
                onPickSplitApk = {
                    pickSplitApkLauncher.launch(arrayOf(
                        "application/vnd.android.package-archive",
                        "application/octet-stream",
                        "*/*",
                    ))
                },
                onPickApkForVerify = {
                    pickApkForVerifyLauncher.launch(arrayOf(
                        "application/vnd.android.package-archive",
                        "application/octet-stream",
                    ))
                },
                onOpenDeveloperSettings = {
                    runCatching {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                    }
                },
            )
        }
    }
}
