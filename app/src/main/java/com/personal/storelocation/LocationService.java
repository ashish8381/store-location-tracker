package com.personal.storelocation;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.room.Room;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.concurrent.Executors;

public class LocationService extends Service {

    private static final String TAG = "LocationService";

    private FusedLocationProviderClient fusedLocationClient;
    private LocationDao locationDao;
    private Location lastSavedLocation = null;
    private static final String CHANNEL_ID = "loc_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "location-db").build();
        locationDao = db.locationDao();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        startForeground(1, getNotification());
        requestLocationUpdates();
    }

    private void requestLocationUpdates() {
        Log.d(TAG, "Requesting location updates");
        LocationRequest request = LocationRequest.create()
                .setInterval(20_000)
                .setFastestInterval(10_000)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing location permissions");
            return;
        }

        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
    }

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult result) {
            Location newLocation = result.getLastLocation();
            if (newLocation == null) {
                Log.w(TAG, "Received null location");
                return;
            }

            Log.d(TAG, "New location: " + newLocation.getLatitude() + ", " + newLocation.getLongitude());

            if (lastSavedLocation == null || newLocation.distanceTo(lastSavedLocation) > 50) {
                lastSavedLocation = newLocation;

                LocationEntity entity = new LocationEntity();
                entity.latitude = newLocation.getLatitude();
                entity.longitude = newLocation.getLongitude();
                entity.timestamp = System.currentTimeMillis();

                Log.i(TAG, "Saving location to DB: " + entity.latitude + ", " + entity.longitude);
                Executors.newSingleThreadExecutor().execute(() -> locationDao.insert(entity));
            } else {
                Log.d(TAG, "Location within 50m. Skipped saving.");
            }

            // Send broadcast for UI updates
            Intent intent = new Intent("LOCATION_UPDATED");
            intent.putExtra("lat", newLocation.getLatitude());
            intent.putExtra("lng", newLocation.getLongitude());
            sendBroadcast(intent);
            Log.d(TAG, "Broadcast sent with updated location");
        }
    };

    private Notification getNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Tracking",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Location tracking is active.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created");
            }
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Tracking Location")
                .setContentText("Location service is running")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation) // Temporarily test with this
                .setOngoing(true)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
