package com.suara

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private var updateStatusCallback: ((String) -> Unit)? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            startNavService()
        } else {
            updateStatusCallback?.invoke("Izin lokasi ditolak.")
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)

        // Menghubungkan pembaruan status dari NovanService ke UI Compose
        NovanService.onStatusUpdate = { newStatus ->
            updateStatusCallback?.invoke(newStatus)
        }

        setContent {
            var statusText by remember { mutableStateOf("Tekan tombol untuk mulai navigasi") }
            var isRunning by remember { mutableStateOf(false) }

            updateStatusCallback = { newStatus ->
                statusText = newStatus
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LayarUtama(
                        statusText = statusText,
                        isRunning = isRunning,
                        onToggleClick = { active ->
                            isRunning = active
                            if (active) {
                                requestPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                    )
                                )
                            } else {
                                stopNavService()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun startNavService() {
        val intent = Intent(this, NovanService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopNavService() {
        val intent = Intent(this, NovanService::class.java)
        stopService(intent)
        updateStatusCallback?.invoke("Navigasi dihentikan.")
    }

    override fun onDestroy() {
        super.onDestroy()
        NovanService.onStatusUpdate = null
    }
}

@Composable
fun LayarUtama(
    statusText: String,
    isRunning: Boolean,
    onToggleClick: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("navigasi suara gps", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Text(statusText, style = MaterialTheme.typography.bodyLarge)
        
        Spacer(modifier = Modifier.height(36.dp))

        // Tombol interaktif untuk menyalakan/mematikan layanan navigasi latar belakang
        Button(
            onClick = { onToggleClick(!isRunning) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth(0.7f).height(50.dp)
        ) {
            Text(
                text = if (isRunning) "Berhenti Bersuara" else "Mulai Navigasi",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}