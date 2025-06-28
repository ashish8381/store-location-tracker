package com.personal.storelocation;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private MapView map;
    private AppDatabase db;
    private Marker currentLocationMarker;
    private Polyline routeLine;

    private static final int FOREGROUND_PERMISSION_REQUEST_CODE = 101;
    private static final int BACKGROUND_PERMISSION_REQUEST_CODE = 102;

    private TextView tvSelectedDate;
    private ImageView btnPrevDate, btnNextDate;
    private Calendar selectedCalendar;
    private FloatingActionButton btnHourlySummary;

    private GeoPoint lastClickedPoint;

    boolean isbtnclicked = false;

    ImageView mserviceEnable, btnSetting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Initializing");
        setContentView(R.layout.activity_main);

        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        btnPrevDate = findViewById(R.id.btnPrevDate);
        btnNextDate = findViewById(R.id.btnNextDate);
        btnHourlySummary = findViewById(R.id.btnHourlySummary);
        mserviceEnable = findViewById(R.id.btnEmergencyShare);
        btnSetting = findViewById(R.id.btnSetting);
        selectedCalendar = Calendar.getInstance();

        btnPrevDate.setOnClickListener(v -> {
            selectedCalendar.add(Calendar.DAY_OF_MONTH, -1);
            updateSelectedDateText();
            loadStoredRoute();
        });

        btnNextDate.setOnClickListener(v -> {
            Calendar today = Calendar.getInstance();
            Calendar nextDate = (Calendar) selectedCalendar.clone();
            nextDate.add(Calendar.DAY_OF_MONTH, 1);
            if (!nextDate.after(today)) {
                selectedCalendar = nextDate;
                updateSelectedDateText();
                loadStoredRoute();
            } else {
                Toast.makeText(this, "You can't select a future date", Toast.LENGTH_SHORT).show();
            }
        });

        tvSelectedDate.setOnClickListener(v -> {
            int year = selectedCalendar.get(Calendar.YEAR);
            int month = selectedCalendar.get(Calendar.MONTH);
            int day = selectedCalendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog dialog = new DatePickerDialog(this, (view, y, m, d) -> {
                selectedCalendar.set(y, m, d);
                updateSelectedDateText();
                loadStoredRoute();
            }, year, month, day);
            dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
            dialog.show();
        });


        btnSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingActivity.class));
            }
        });

        mserviceEnable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                isbtnclicked=true;
                boolean newState = !isServiceEnabled(MainActivity.this);

                // Optionally start/stop service
                if (newState) {
                    checkAndRequestPermissions();
                } else {
                    saveServiceState(MainActivity.this, newState);
                    updateServiceIcon(mserviceEnable, newState);
                    stopService(new Intent(MainActivity.this, LocationService.class));
                    Toast.makeText(MainActivity.this, "Location service stopped.", Toast.LENGTH_SHORT).show();
                }


            }
        });

        updateSelectedDateText();

        map = findViewById(R.id.map);
        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setBuiltInZoomControls(true);
        map.setMultiTouchControls(true);

        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "location-db").fallbackToDestructiveMigration().build();

        currentLocationMarker = new Marker(map);
        currentLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        currentLocationMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_location));
        currentLocationMarker.setTitle("Current Location");
        map.getOverlays().add(currentLocationMarker);


        routeLine = new Polyline();
        routeLine.setColor(Color.BLUE);
        routeLine.setWidth(5.0f);
        map.getOverlays().add(routeLine);

        loadStoredRoute();

        btnHourlySummary.setOnClickListener(v -> {
            Executors.newSingleThreadExecutor().execute(() -> {
                long start = getStartOfDay(selectedCalendar.getTimeInMillis());
                long end = getEndOfDay(selectedCalendar.getTimeInMillis());
                List<LocationEntity> list = db.locationDao().getLocationsBetween(start, end);
                runOnUiThread(() -> showHourlyBottomSheet(list));
            });
        });

        if (isServiceEnabled(MainActivity.this)) {
            checkAndRequestPermissions();
        }else{
            updateServiceIcon(mserviceEnable, false);
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(locationUpdateReceiver, new IntentFilter("LOCATION_UPDATED"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(locationUpdateReceiver, new IntentFilter("LOCATION_UPDATED"));
        }

        MapEventsReceiver mapEventsReceiver = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                Log.d(TAG, "Map tapped at: " + p.getLatitude() + ", " + p.getLongitude());

                // Save checkpoint
                runOnUiThread(() -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle("Add Checkpoint Note");

                    final EditText input = new EditText(MainActivity.this);
                    input.setHint("Enter note...");
                    builder.setView(input);

                    builder.setPositiveButton("Save", (dialog, which) -> {
                        String note = input.getText().toString();

                        Executors.newSingleThreadExecutor().execute(() -> {
                            LocationEntity checkpoint = new LocationEntity();
                            checkpoint.latitude = p.getLatitude();
                            checkpoint.longitude = p.getLongitude();
                            checkpoint.timestamp = System.currentTimeMillis();
                            checkpoint.isCheckpoint = true;
                            checkpoint.note = note;

                            db.locationDao().insert(checkpoint);
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Checkpoint saved", Toast.LENGTH_SHORT).show());
                        });
                    });

                    builder.setNegativeButton("Cancel", null);
                    builder.show();
                });


                Toast.makeText(MainActivity.this, "Checkpoint added!", Toast.LENGTH_SHORT).show();
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        };

        MapEventsOverlay eventsOverlay = new MapEventsOverlay(mapEventsReceiver);
        map.getOverlays().add(eventsOverlay);

        Log.e(TAG, "onCreate: " + isServiceEnabled(MainActivity.this));
    }

    public void saveServiceState(Context context, boolean isEnabled) {
        SharedPreferences prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("service_enabled", isEnabled);
        editor.apply();
    }

    private void updateServiceIcon(ImageView imageView, boolean isEnabled) {
        if (isEnabled) {
            imageView.setImageResource(R.drawable.emergency_share_24px); // your enabled icon
            imageView.setColorFilter(ContextCompat.getColor(this, R.color.green), PorterDuff.Mode.SRC_IN);

        } else {
            imageView.setImageResource(R.drawable.emergency_share_off_24px); // your disabled icon
            imageView.setColorFilter(ContextCompat.getColor(this, R.color.red), PorterDuff.Mode.SRC_IN);

        }
    }


    public boolean isServiceEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        return prefs.getBoolean("service_enabled", false); // false by default
    }


    private void updateSelectedDateText() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        tvSelectedDate.setText(sdf.format(selectedCalendar.getTime()));
    }

    private void showHourlyBottomSheet(List<LocationEntity> dayData) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottomsheet_hourly_summary, null);
        dialog.setContentView(view);

        RecyclerView rv = view.findViewById(R.id.rvHourlySummary);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new HourlySummaryAdapter(generateHourlySummary(dayData)));

        Button btnReplayRoute = view.findViewById(R.id.btnReplayRoute);
        btnReplayRoute.setOnClickListener(v -> replayRoute(dayData));

        Button btnAddCheckpoint = view.findViewById(R.id.btnAddCheckpoint);
        btnAddCheckpoint.setOnClickListener(v -> addCheckpoint());

        dialog.show();
    }

    private void addCheckpoint() {
        if (lastClickedPoint == null) {
            Toast.makeText(this, "Tap on map to set checkpoint", Toast.LENGTH_SHORT).show();
            return;
        }

        LocationEntity checkpoint = new LocationEntity();
        checkpoint.latitude = lastClickedPoint.getLatitude();
        checkpoint.longitude = lastClickedPoint.getLongitude();
        checkpoint.timestamp = System.currentTimeMillis();

        Executors.newSingleThreadExecutor().execute(() -> db.locationDao().insert(checkpoint));
        Toast.makeText(this, "Checkpoint added", Toast.LENGTH_SHORT).show();
    }

    private void replayRoute(List<LocationEntity> route) {
        Executors.newSingleThreadExecutor().execute(() -> {
            for (LocationEntity l : route) {
                GeoPoint point = new GeoPoint(l.latitude, l.longitude);
                runOnUiThread(() -> {
                    currentLocationMarker.setPosition(point);
                    currentLocationMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_location));
                    map.getController().animateTo(point);
                    map.invalidate();
                });
                try {
                    Thread.sleep(300); // simulate animation
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private List<HourlyInfo> generateHourlySummary(List<LocationEntity> locations) {
        Map<Integer, List<LocationEntity>> hourlyMap = new TreeMap<>();

        for (LocationEntity loc : locations) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(loc.timestamp);
            int hour = cal.get(Calendar.HOUR_OF_DAY);

            if (!hourlyMap.containsKey(hour)) hourlyMap.put(hour, new ArrayList<>());
            hourlyMap.get(hour).add(loc);
        }

        List<HourlyInfo> summaryList = new ArrayList<>();

        for (Map.Entry<Integer, List<LocationEntity>> entry : hourlyMap.entrySet()) {
            int hour = entry.getKey();
            List<LocationEntity> points = entry.getValue();

            float distance = 0f;
            Location prev = null;
            boolean hasCheckpoint = false;
            String note = "";
            for (LocationEntity l : points) {
                if (l.isCheckpoint) hasCheckpoint = true;

                Location curr = new Location("");
                curr.setLatitude(l.latitude);
                curr.setLongitude(l.longitude);
                note = l.note;
                if (prev != null) distance += prev.distanceTo(curr);
                prev = curr;
            }

            summaryList.add(new HourlyInfo(hour, distance, points.size(), hasCheckpoint, note));
        }

        return summaryList;
    }

    private void loadStoredRoute() {
        Executors.newSingleThreadExecutor().execute(() -> {
            long start = getStartOfDay(selectedCalendar.getTimeInMillis());
            long end = getEndOfDay(selectedCalendar.getTimeInMillis());
            List<LocationEntity> list = db.locationDao().getLocationsBetween(start, end);

            List<GeoPoint> points = new ArrayList<>();
            List<Marker> checkpointMarkers = new ArrayList<>();

            for (LocationEntity l : list) {
                GeoPoint point = new GeoPoint(l.latitude, l.longitude);
                points.add(point);

                if (l.isCheckpoint) {
                    Marker checkpointMarker = new Marker(map);
                    checkpointMarker.setPosition(point);
                    checkpointMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    checkpointMarker.setIcon(getResources().getDrawable(R.drawable.ic_location)); // use distinct icon
                    checkpointMarker.setTitle(l.note != null ? l.note : "Checkpoint");

                    checkpointMarker.setOnMarkerClickListener((marker, mapView) -> {
                        showEditDeleteDialog(l);
                        return true;
                    });

                    checkpointMarkers.add(checkpointMarker);
                }
            }

            runOnUiThread(() -> {
                map.getOverlays().remove(routeLine);
                routeLine.setPoints(points);
                map.getOverlays().add(routeLine);

                // Remove old checkpoint markers if needed
                map.getOverlays().removeIf(overlay -> overlay instanceof Marker && ((Marker) overlay).getTitle() != null && ((Marker) overlay).getTitle().startsWith("Checkpoint"));

                for (Marker marker : checkpointMarkers) {
                    map.getOverlays().add(marker);
                }

                if (!points.isEmpty()) {
                    map.getController().setZoom(18.0);
                    map.getController().animateTo(points.get(points.size() - 1));
                }

                map.invalidate(); // Refresh map
            });
        });
    }


    private void showEditDeleteDialog(LocationEntity checkpoint) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("Edit or Delete Checkpoint");

            final EditText input = new EditText(MainActivity.this);
            input.setText(checkpoint.note);
            builder.setView(input);

            builder.setPositiveButton("Update", (dialog, which) -> {
                String updatedNote = input.getText().toString();
                Executors.newSingleThreadExecutor().execute(() -> {
                    checkpoint.note = updatedNote;
                    db.locationDao().update(checkpoint);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Checkpoint updated", Toast.LENGTH_SHORT).show();
                        loadStoredRoute(); // refresh
                    });
                });
            });

            builder.setNegativeButton("Delete", (dialog, which) -> {
                Executors.newSingleThreadExecutor().execute(() -> {
                    db.locationDao().delete(checkpoint);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Checkpoint deleted", Toast.LENGTH_SHORT).show();
                        loadStoredRoute(); // refresh
                    });
                });
            });

            builder.setNeutralButton("Cancel", null);
            builder.show();
        });
    }


    private long getStartOfDay(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private long getEndOfDay(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTimeInMillis();
    }

    private void checkAndRequestPermissions() {
        List<String> permissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                ContextCompat.checkSelfPermission(this, "android.permission.FOREGROUND_SERVICE_LOCATION") != PackageManager.PERMISSION_GRANTED)
            permissions.add("android.permission.FOREGROUND_SERVICE_LOCATION");

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), FOREGROUND_PERMISSION_REQUEST_CODE);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, BACKGROUND_PERMISSION_REQUEST_CODE);
            } else {
                startLocationService();
            }
        }
    }

    private void startLocationService() {

        Intent i = new Intent(this, LocationService.class);
        ContextCompat.startForegroundService(this, i);
        boolean newState ;
        if(isbtnclicked){
            newState= !isServiceEnabled(MainActivity.this);
        }else{
            newState= isServiceEnabled(MainActivity.this);
        }
        saveServiceState(MainActivity.this, newState);
        updateServiceIcon(mserviceEnable, newState);
        Toast.makeText(MainActivity.this, "Location service started.", Toast.LENGTH_SHORT).show();

        Log.e(TAG, "startLocationService: " + isServiceEnabled(MainActivity.this));
        isbtnclicked=false;

    }

    private final BroadcastReceiver locationUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            double lat = intent.getDoubleExtra("lat", 0);
            double lng = intent.getDoubleExtra("lng", 0);
            GeoPoint point = new GeoPoint(lat, lng);
            currentLocationMarker.setPosition(point);
            List<GeoPoint> pts = routeLine.getActualPoints();
            pts.add(point);
            routeLine.setPoints(pts);
            map.getController().animateTo(point);

            map.invalidate();

        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = true;
        for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) granted = false;

        if (requestCode == FOREGROUND_PERMISSION_REQUEST_CODE && granted)
            checkAndRequestPermissions();
        else if (requestCode == BACKGROUND_PERMISSION_REQUEST_CODE && granted)
            startLocationService();
        else showSettingsDialog("Location permission required.");
    }

    private void showSettingsDialog(String msg) {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage(msg + "\n\nPlease enable it in settings.")
                .setPositiveButton("Open Settings", (d, w) -> {
                    Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                }).setNegativeButton("Cancel", null).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(locationUpdateReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
    }
}
