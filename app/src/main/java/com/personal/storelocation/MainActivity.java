package com.personal.storelocation;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.room.Room;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private MapView map;
    private AppDatabase db;
    private Marker currentLocationMarker;
    private Polyline routeLine;

    private static final int PERMISSION_REQUEST_CODE = 101;
    private static final int FOREGROUND_PERMISSION_REQUEST_CODE = 101;
    private static final int BACKGROUND_PERMISSION_REQUEST_CODE = 102;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Initializing");
        setContentView(R.layout.activity_main);

        checkAndRequestPermissions();

        map = findViewById(R.id.map);
        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setBuiltInZoomControls(true);
        map.setMultiTouchControls(true);


        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "location-db").build();

        currentLocationMarker = new Marker(map);
        currentLocationMarker.setIcon(getResources().getDrawable(R.drawable.ic_location));
        map.getOverlays().add(currentLocationMarker);

        routeLine = new Polyline();
        routeLine.setColor(Color.BLUE);
        routeLine.setWidth(5.0f);
        map.getOverlays().add(routeLine);
        map.setTileSource(TileSourceFactory.MAPNIK);


        loadStoredRoute();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "Registering receiver with NOT_EXPORTED flag");
            registerReceiver(locationUpdateReceiver, new IntentFilter("LOCATION_UPDATED"),
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            Log.d(TAG, "Registering receiver (pre-TIRAMISU)");
            registerReceiver(locationUpdateReceiver, new IntentFilter("LOCATION_UPDATED"));
        }
    }

    private void checkAndRequestPermissions() {
        Log.d(TAG, "Checking permissions...");

        List<String> foregroundPermissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            foregroundPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            foregroundPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        // Android 14+ (UpsideDownCake)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                ContextCompat.checkSelfPermission(this, "android.permission.FOREGROUND_SERVICE_LOCATION") != PackageManager.PERMISSION_GRANTED)
            foregroundPermissions.add("android.permission.FOREGROUND_SERVICE_LOCATION");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        }


        if (!foregroundPermissions.isEmpty()) {
            Log.d(TAG, "Requesting foreground permissions");
            ActivityCompat.requestPermissions(this, foregroundPermissions.toArray(new String[0]), FOREGROUND_PERMISSION_REQUEST_CODE);
        } else {
            // If foreground granted, check background for Android Q+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                Log.d(TAG, "Requesting background location permission");
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                        BACKGROUND_PERMISSION_REQUEST_CODE);
            } else {
                Log.d(TAG, "All permissions granted. Starting location service.");
                startLocationService();
            }
        }
    }


    private void startLocationService() {
        Log.d(TAG, "Starting LocationService");
        Intent serviceIntent = new Intent(this, LocationService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void loadStoredRoute() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<LocationEntity> list = db.locationDao().getAllLocations();
            List<GeoPoint> points = new ArrayList<>();

            for (LocationEntity l : list) {
                Log.d("MAP_LOAD", "DB point: " + l.latitude + ", " + l.longitude);
                points.add(new GeoPoint(l.latitude, l.longitude));
            }

            runOnUiThread(() -> {
                if (!points.isEmpty()) {
                    routeLine.setPoints(points);

                    // Move marker to last point
                    GeoPoint lastPoint = points.get(points.size() - 1);
                    currentLocationMarker.setPosition(lastPoint);
                    currentLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

                    map.getController().setZoom(18.0);
                    map.getController().setCenter(lastPoint);

                    map.invalidate(); // Force redraw
                    Log.d("MAP_LOAD", "Map centered on: " + lastPoint);
                } else {
                    Log.w("MAP_LOAD", "No points to draw");
                }
            });
        });
    }


    private final BroadcastReceiver locationUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            double lat = intent.getDoubleExtra("lat", 0);
            double lng = intent.getDoubleExtra("lng", 0);
            Log.d(TAG, "Received location broadcast: " + lat + ", " + lng);
            GeoPoint point = new GeoPoint(lat, lng);
            currentLocationMarker.setPosition(point);

            List<GeoPoint> currentPoints = routeLine.getActualPoints();
            currentPoints.add(point);
            routeLine.setPoints(currentPoints);

            map.getController().animateTo(point);
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (requestCode == FOREGROUND_PERMISSION_REQUEST_CODE) {
            if (allGranted) {
                Log.d(TAG, "Foreground location granted. Checking background...");
                checkAndRequestPermissions(); // Check background next
            } else {
                Log.w(TAG, "User denied foreground location.");

                for (String perm : permissions) {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, perm)) {
                        // Permanently denied
                        showSettingsDialog("Foreground location permission permanently denied.");
                        return;
                    }
                }

                Toast.makeText(this, "Location permission is required.", Toast.LENGTH_LONG).show();
            }

        } else if (requestCode == BACKGROUND_PERMISSION_REQUEST_CODE) {
            if (allGranted) {
                Log.d(TAG, "Background location granted. Starting service.");
                startLocationService();
            } else {
                Log.w(TAG, "User denied background location.");

                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                    showSettingsDialog("Background location permission permanently denied.");
                    return;
                }

                Toast.makeText(this, "Background location is required for full tracking.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showSettingsDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage(message + "\n\nPlease enable it in app settings.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Unregistering receiver and destroying MainActivity");
        unregisterReceiver(locationUpdateReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        map.onPause();
    }
}
