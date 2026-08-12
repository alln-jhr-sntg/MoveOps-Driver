package com.lvms.driver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.lvms.driver.model.GpsPayload
import com.lvms.driver.model.StatusResponse
import com.lvms.driver.network.ApiClient
import com.lvms.driver.network.GpsApi
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class GpsTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val gpsApi by lazy { ApiClient.retrofit.create(GpsApi::class.java) }
    private var tripId: Int = -1
    private var pingCount = 0

    companion object {
        const val CHANNEL_ID = "gps_tracking_channel"
        const val NOTIFICATION_ID = 1001
        const val UPDATE_INTERVAL_MS = 10_000L
        const val MAX_ACCEPTABLE_ACCURACY_METERS = 50f

        // Same process as the activities, so a plain companion property is
        // enough for TripDetailActivity to read current ping count — no IPC needed.
        var lastPingCount = 0
            private set
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        tripId = intent?.getIntExtra("trip_id", -1) ?: -1

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        startLocationUpdates()
        return START_REDELIVER_INTENT
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { postLocation(it) }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        } catch (e: SecurityException) {
            // Permission got revoked mid-trip (Settings, OS prompt, etc.) — stop cleanly.
            stopSelf()
        }
    }

    private fun postLocation(location: Location) {
        if (location.hasAccuracy() && location.accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) {
            return
        }
        val payload = GpsPayload(
            tripId = tripId,
            latitude = location.latitude,
            longitude = location.longitude,
            speedKph = (location.speed * 3.6).takeIf { location.hasSpeed() },
            headingDegrees = location.bearing.toInt().takeIf { location.hasBearing() },
            accuracyMeters = location.accuracy.toDouble().takeIf { location.hasAccuracy() }
        )

        gpsApi.postGps("gps", payload).enqueue(object : Callback<StatusResponse> {
            override fun onResponse(call: Call<StatusResponse>, response: Response<StatusResponse>) {
                if (response.isSuccessful) {
                    pingCount++
                    lastPingCount = pingCount
                    updateNotification()
                }
                // A failed ping is skipped, not retried — the next one is
                // only 15 seconds away regardless.
            }

            override fun onFailure(call: Call<StatusResponse>, t: Throwable) {
                // Same reasoning — let the next interval handle it.
            }
        })
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Trip Tracking", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Shows while your location is being shared during an active trip"
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LVMS \u2014 Trip in progress")
            .setContentText("Sharing your location for this trip")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}