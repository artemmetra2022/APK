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
import com.localadb.manager.ui.theme.LocalAdbManagerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Системный файловый менеджер (Storage Access Framework) — без дополнительных разрешений
    private val pickApkLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.onApkPicked(it, contentResolver) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocalAdbManagerTheme {
                LocalAdbManagerApp(
                    viewModel = viewModel,
                    onPickApk = {
                        pickApkLauncher.launch(arrayOf("application/vnd.android.package-archive"))
                    },
                    onOpenDeveloperSettings = { openDeveloperSettings() },
                )
            }
        }
    }

    private fun openDeveloperSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        runCatching { startActivity(intent) }
    }
}
