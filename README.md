
# 📍 Store Location Tracker

An Android app that continuously tracks the user's location in the background and stores coordinates in a local Room database — only if the distance change exceeds 50 meters. The route is displayed on an OpenStreetMap-based map with real-time updates.

## 🚀 Features
- 📌 Location tracking via foreground service
- ✅ Saves location only if user moves > 50 meters
- 🗺️ Real-time location path drawn on OSM map (osmdroid)
- 🧠 Uses Room for local persistent storage
- 🔔 Notification shown while tracking in background
- 🔒 Runtime permission handling for Android 10 to Android 14+

## 🧩 Tech Stack
- Java
- Room Database
- osmdroid (`org.osmdroid:osmdroid-android:6.1.16`)
- FusedLocationProvider (Google Play Services)
- Foreground Service API

## 📁 Project Structure

```
StoreLocation/
├── MainActivity.java
├── LocationService.java
├── LocationEntity.java
├── LocationDao.java
├── AppDatabase.java
├── res/
│   ├── layout/activity_main.xml
│   ├── drawable/ic_notif.xml
│   └── mipmap/ic_launcher.png
├── AndroidManifest.xml
```

## 🛠️ Setup Instructions

1. Clone the Repo

```bash
git clone https://github.com/yourusername/store-location-tracker.git
cd store-location-tracker
```

2. Add Dependencies

In `build.gradle (app-level)`:

```gradle
implementation 'org.osmdroid:osmdroid-android:6.1.16'
implementation 'com.google.android.gms:play-services-location:21.0.1'
```

3. Add Permissions to `AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.INTERNET" />
```

And declare your service:

```xml
<service
    android:name=".LocationService"
    android:foregroundServiceType="location"
    android:exported="false" />
```

4. Request Runtime Permissions

Be sure to request runtime permissions dynamically for Android 10–14.

## 🛡️ License

MIT License. Free to use and modify.

## 🙋‍♂️ Author

**Ashish** – [@ashish](https://github.com/yourusername)
