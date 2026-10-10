package com.suara

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

// Utility 8 Penjuru Mata Angin Bahasa Indonesia
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

class NovanService : Service(), TextToSpeech.OnInitListener, SensorEventListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private lateinit var locationManager: LocationManager
    private var locationListener: LocationListener? = null
    private var lastLocation: Location? = null

    private lateinit var sensorManager: SensorManager
    private val gravityValues = FloatArray(3)
    private val geomagneticValues = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    private var lastAnnouncedDirection = ""
    private var lastRoadName = "Mencari nama jalan..."
    private var lastApiCallTime = 0L

    companion object {
        const val CHANNEL_ID = "NovanNavServiceChannel"
        const val NOTIFICATION_ID = 123
        var onStatusUpdate: ((String) -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("id", "ID"))
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
                speak("Navigasi latar belakang diaktifkan.")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Navigasi Suara Aktif")
            .setContentText("Melacak lokasi, kompas, dan nama jalan...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        startTracking()

        return START_STICKY
    }

    private fun startTracking() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }

        locationListener = LocationListener { location ->
            lastLocation = location
            processLocation(location)
        }

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                1.0f,
                locationListener!!
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun processLocation(location: Location) {
        val bearing = location.bearing
        val direction = CompassUtil.getCardinalDirection(bearing)
        val speedKmh = if (location.hasSpeed()) (location.speed * 3.6).toInt() else 0

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastApiCallTime > 15000) {
            lastApiCallTime = currentTime
            fetchStreetName(location.latitude, location.longitude)
        }

        val statusMsg = "Jalan: $lastRoadName\nMenghadap: $direction | Kecepatan: $speedKmh km/jam"
        onStatusUpdate?.invoke(statusMsg)

        if (direction != lastAnnouncedDirection) {
            lastAnnouncedDirection = direction
            speak("Menghadap ke $direction. $lastRoadName")
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
                    val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    val normalizedBearing = (azimuth + 360) % 360

                    val isMoving = lastLocation?.hasSpeed() == true && (lastLocation?.speed ?: 0f) > 0.8f
                    if (!isMoving) {
                        val direction = CompassUtil.getCardinalDirection(normalizedBearing)
                        val statusMsg = "Jalan: $lastRoadName\nMenghadap: $direction (Kompas)"
                        onStatusUpdate?.invoke(statusMsg)
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun fetchStreetName(lat: Double, lon: Double) {
        thread {
            try {
                val urlStr = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon"
                val url = URL(urlStr)
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "NavigasiTunanetraApp/1.0")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val displayName = json.optString("display_name", "Lokasi tidak dikenal")
                    val shortName = displayName.split(",").take(2).joinToString(",")
                    lastRoadName = shortName
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun speak(text: String) {
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, System.currentTimeMillis().toString())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Layanan Navigasi Suara",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        locationListener?.let { locationManager.removeUpdates(it) }
        tts?.stop()
        tts?.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}