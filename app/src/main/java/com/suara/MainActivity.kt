package com.suara

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale

// Utility 8 Penjuru Mata Angin
object CompassUtil {
    fun getCardinalDirection(bearing: Float): String {
        val normalizedBearing = (bearing % 360 + 360) % 360
        return when {
            normalizedBearing >= 337.5 || normalizedBearing < 22.5 -> "Utara"
            normalizedBearing >= 22.5 && normalizedBearing < 67.5 -> "Timur Laut"
            normalizedBearing >= 67.5 && normalizedBearing < 112.5 -> "Timur"
            normalizedBearing >= 112.5 && normalizedBearing < 157.5 -> "Tenggara"
            normalizedBearing >= 157.5 && normalizedBearing < 202.5 -> "Selatan"
            normalizedBearing >= 202.5 && normalizedBearing < 247.5 -> "Barat Daya"
            normalizedBearing >= 247.5 && normalizedBearing < 292.5 -> "Barat"
            normalizedBearing >= 292.5 && normalizedBearing < 337.5 -> "Barat Laut"
            else -> "Utara"
        }
    }
}

class NavigationVoiceManager(private val context: Context, private val onStatusUpdate: (String) -> Unit) : TextToSpeech.OnInitListener {
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isTtsReady = false
    private var lastAnnouncedDirection: String = ""
    private var locationCallback: LocationCallback? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("id", "ID"))
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
                speak("Navigasi suara siap dimulai.")
            }
        }
    }

    fun startTracking() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).apply {
            setMinUpdateIntervalMillis(500L)
            setMinUpdateDistanceMeters(1.0f)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    processLocation(location)
                }
            }
        }

        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun processLocation(location: Location) {
        val direction = CompassUtil.getCardinalDirection(location.bearing)
        val speedKmh = if (location.hasSpeed()) (location.speed * 3.6).toInt() else 0
        
        val statusMessage = "Menghadap: $direction\nKecepatan: $speedKmh km/jam"
        onStatusUpdate(statusMessage)

        if (direction != lastAnnouncedDirection) {
            lastAnnouncedDirection = direction
            speak("Menghadap ke $direction")
        }
    }

    fun speak(text: String) {
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, System.currentTimeMillis().toString())
        }
    }

    fun stop() {
        locationCallback?.let { fusedLocationClient.removeUpdates(it) }
        tts?.stop()
        tts?.shutdown()
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var navManager: NavigationVoiceManager
    
    // Menggunakan backing property untuk state teks agar aman dari error kompilasi Activity
    private var _statusText by mutableStateOf("Meminta izin lokasi...")
    val statusText: String get() = _statusText

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            _statusText = "Mencari sinyal GPS..."
            navManager.startTracking()
        } else {
            _statusText = "Izin lokasi ditolak."
            navManager.speak("Izin lokasi ditolak.")
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        
        navManager = NavigationVoiceManager(this) { newStatus ->
            _statusText = newStatus
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LayarUtama(statusText = statusText)
                }
            }
        }

        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        navManager.stop()
    }
}

@Composable
fun LayarUtama(statusText: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("navigasi suara gps", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(statusText, style = MaterialTheme.typography.bodyLarge)
    }
}