package com.zxq.bmagent.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.zxq.bmagent.android.service.ServiceLocator
import com.zxq.bmagent.android.ui.AgentScreen
import com.zxq.bmagent.android.ui.theme.BetterMonitorAgentTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                    isGranted: Boolean ->
                // Handle permission result if needed, for now just proceeding
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkPermissions()

        val repository = ServiceLocator.provideAgentRepository(this)

        setContent {
            BetterMonitorAgentTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AgentScreen(repository = repository, modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    val intent =
                            android.content.Intent(
                                    android.provider.Settings
                                            .ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                            )
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data =
                            android.net.Uri.parse(
                                    String.format("package:%s", applicationContext.packageName)
                            )
                    startActivityForResult(intent, 2296)
                } catch (e: Exception) {
                    val intent = android.content.Intent()
                    intent.action =
                            android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    startActivityForResult(intent, 2296)
                }
            }
        } else {
            val permissions =
                    arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
            requestPermissionLauncher.launch(
                    permissions[0]
            ) // Simplify for now, ideally request all
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
