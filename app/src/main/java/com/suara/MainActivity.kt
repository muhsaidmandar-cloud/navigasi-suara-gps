package com.suara

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Locale

// Utility untuk 8 Penjuru Mata Angin Bahasa Indonesia
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

// Manager untuk Text-to-Speech, GPS, dan Sensor Kompas Hardware
class NavigationVoiceManager(
    private val context: Context,
    private val onStatusUpdate: (String) -> Unit
) : TextToSpeech.OnInitListener, SensorEventListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isTtsReady = false

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var locationListener: LocationListener? = null
    private var lastLocation: Location? = null

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gravityValues = FloatArray(3)
    private val geomagneticValues = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    private var lastAnnouncedDirection: String = ""

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
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        locationListener = LocationListener { location ->
            lastLocation = location
            processLocationAndCompass(location.bearing, location.hasBearing(), location.speed)
        }

        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1.0f,
                    locationListener!!
                )
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(it.values, 0, gravityValues, 0, it.values.size)
                hasGravity = true
            } else if (it.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(it.values, 0, geomagneticValues, 0, it.values.size)
                hasGeomagnetic = true
            }

            if (hasGravity && hasGeomagnetic) {
                val r = FloatArray(9)
                val i = FloatArray(9)
                if (SensorManager.getRotationMatrix(r, i, gravityValues, geomagneticValues)) {
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(r, orientation)
                    val azimuthInDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    val normalizedBearing = (azimuthInDegrees + 360) % 360

                    val isMoving = lastLocation?.hasSpeed() == true && (lastLocation?.speed ?: 0f) > 0.8f
                    if (!isMoving) {
                        processLocationAndCompass(normalizedBearing, true, lastLocation?.speed ?: 0f)
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun processLocationAndCompass(bearing: Float, hasBearing: Boolean, speed: Float) {
        if (!hasBearing) return

        val direction = CompassUtil.getCardinalDirection(bearing)
        val speedKmh = if (speed > 0) (speed * 3.6).toInt() else 0

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
        sensorManager.unregisterListener(this)
        locationListener?.let { locationManager.removeUpdates(it) }
        tts?.stop()
        tts?.shutdown()
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var navManager: NavigationVoiceManager
    private var updateStatusCallback: ((String) -> Unit)? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            updateStatusCallback?.invoke("Mencari sinyal GPS...")
            navManager.startTracking()
        } else {
            updateStatusCallback?.invoke("Izin lokasi ditolak.")
            navManager.speak("Izin lokasi ditolak.")
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)

        navManager = NavigationVoiceManager(this) { newStatus ->
            updateStatusCallback?.invoke(newStatus)
        }

        setContent {
            var statusText by remember { mutableStateOf("Meminta izin lokasi...") }

            updateStatusCallback = { newStatus ->
                statusText = newStatus
            }

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