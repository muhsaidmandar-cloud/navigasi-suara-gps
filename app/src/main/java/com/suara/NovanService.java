package com.suara;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

class CompassUtilJava {
    public static String getCardinalDirection(float bearing) {
        float normalized = (bearing % 360 + 360) % 360;
        if (normalized >= 337.5 || normalized < 22.5) return "Utara";
        if (normalized >= 22.5 && normalized < 67.5) return "Timur Laut";
        if (normalized >= 67.5 && normalized < 112.5) return "Timur";
        if (normalized >= 112.5 && normalized < 157.5) return "Tenggara";
        if (normalized >= 157.5 && normalized < 202.5) return "Selatan";
        if (normalized >= 202.5 && normalized < 247.5) return "Barat Daya";
        if (normalized >= 247.5 && normalized < 292.5) return "Barat";
        if (normalized >= 292.5 && normalized < 337.5) return "Barat Laut";
        return "Utara";
    }
}

public class NovanService extends Service implements TextToSpeech.OnInitListener, SensorEventListener {

    private TextToSpeech tts;
    private boolean isTtsReady = false;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location lastLocation;

    private SensorManager sensorManager;
    private final float[] gravityValues = new float[3];
    private final float[] geomagneticValues = new float[3];
    private boolean hasGravity = false;
    private boolean hasGeomagnetic = false;

    private String lastAnnouncedDirection = "";
    private String lastRoadName = "Mencari nama jalan...";
    private long lastApiCallTime = 0L;

    public static final String CHANNEL_ID = "NovanNavServiceChannel";
    public static final int NOTIFICATION_ID = 123;
    
    // Interface sederhana pengganti lambda Kotlin agar aman di Java
    public interface StatusCallback {
        void onUpdate(String status);
    }
    public static StatusCallback statusCallback = null;

    @Override
    public void onCreate() {
        super.onCreate();
        tts = new TextToSpeech(this, this);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(new Locale("id", "ID"));
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true;
                speak("Navigasi latar belakang diaktifkan.");
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Navigasi Suara Aktif")
                .setContentText("Melacak lokasi, kompas, dan nama jalan...")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build();

        startForeground(NOTIFICATION_ID, notification);
        startTracking();

        return START_STICKY;
    }

    private void startTracking() {
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
        if (magnetometer != null) {
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        }

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                lastLocation = location;
                processLocation(location);
            }
        };

        try {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1.0f, locationListener);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void processLocation(Location location) {
        float bearing = location.getBearing();
        String direction = CompassUtilJava.getCardinalDirection(bearing);
        int speedKmh = location.hasSpeed() ? (int) (location.getSpeed() * 3.6) : 0;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastApiCallTime > 15000) {
            lastApiCallTime = currentTime;
            fetchStreetName(location.getLatitude(), location.getLongitude());
        }

        String statusMsg = "Jalan: " + lastRoadName + "\nMenghadap: " + direction + " | Kecepatan: " + speedKmh + " km/jam";
        if (statusCallback != null) {
            statusCallback.onUpdate(statusMsg);
        }

        if (!direction.equals(lastAnnouncedDirection)) {
            lastAnnouncedDirection = direction;
            speak("Menghadap ke " + direction + ". " + lastRoadName);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, gravityValues, 0, event.values.length);
            hasGravity = true;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, geomagneticValues, 0, event.values.length);
            hasGeomagnetic = true;
        }

        if (hasGravity && hasGeomagnetic) {
            float[] r = new float[9];
            float[] i = new float[9];
            if (SensorManager.getRotationMatrix(r, i, gravityValues, geomagneticValues)) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(r, orientation);
                float azimuth = (float) Math.toDegrees(orientation[0]);
                float normalized = (azimuth + 360) % 360;

                boolean isMoving = lastLocation != null && lastLocation.hasSpeed() && lastLocation.getSpeed() > 0.8f;
                if (!isMoving) {
                    String direction = CompassUtilJava.getCardinalDirection(normalized);
                    String statusMsg = "Jalan: " + lastRoadName + "\nMenghadap: " + direction + " (Kompas)";
                    if (statusCallback != null) {
                        statusCallback.onUpdate(statusMsg);
                    }
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void fetchStreetName(double lat, double lon) {
        new Thread(() -> {
            try {
                String urlStr = "https://nominatim.openstreetmap.org/reverse?format=json&lat=" + lat + "&lon=" + lon;
                URL url = new URL(urlStr);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "NavigasiTunanetraApp/1.0");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                if (connection.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(response.toString());
                    String displayName = json.optString("display_name", "Lokasi tidak dikenal");
                    String[] parts = displayName.split(",");
                    if (parts.length >= 2) {
                        lastRoadName = parts[0].trim() + ", " + parts[1].trim();
                    } else {
                        lastRoadName = displayName;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void speak(String text) {
        if (isTtsReady && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, String.valueOf(System.currentTimeMillis()));
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Layanan Navigasi Suara",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        if (locationListener != null && locationManager != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}