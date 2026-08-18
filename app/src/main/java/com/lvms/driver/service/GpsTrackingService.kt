package com.lvms.driver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.lvms.driver.model.GpsPayload
import com.lvms.driver.model.StatusResponse
import com.lvms.driver.network.ApiClient
import com.lvms.driver.network.GpsApi
import com.lvms.driver.ui.HomeActivity
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
        const val TRIP_ENDED_CHANNEL_ID = "trip_ended_channel"
        const val NOTIFICATION_ID = 1001
        const val TRIP_ENDED_NOTIFICATION_ID = 1002
        const val UPDATE_INTERVAL_MS = 10_000L
        const val MAX_ACCEPTABLE_ACCURACY_METERS = 50f
        const val ACTION_REFRESH = "com.lvms.driver.action.REFRESH_GPS"

        // Same process as the activities, so plain companion properties are
        // enough for other screens to read tracking state — no IPC needed.
        var lastPingCount = 0
            private set
        var lastPingAtMillis = 0L
            private set
        var isTracking = false
            private set

        // True while a driver-triggered Refresh is waiting on its immediate
        // fix + upload. HomeActivity's status ticker reads this to know
        // when to stop showing the refresh spinner — no listener plumbing
        // needed since it's just another companion property.
        var refreshInFlight = false
            private set

        // Driver-facing "Refresh": starts tracking if it isn't running
        // (recovers from the known force-stop/swipe-from-Recents gap), and
        // always requests one immediate high-accuracy fix on top of the
        // normal UPDATE_INTERVAL_MS cadence.
        fun refresh(context: Context, tripId: Int) {
            val intent = Intent(context, GpsTrackingService::class.java).apply {
                action = ACTION_REFRESH
                putExtra("trip_id", tripId)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val newTripId = intent?.getIntExtra("trip_id", -1) ?: -1
        if (newTripId != tripId) {
            // A new trip in this Service instance (always true on a normal
            // trip start, since a torn-down service is recreated with
            // tripId's -1 default). lastPingCount/lastPingAtMillis are
            // process-lifetime, so without this reset a driver starting a
            // new trip would briefly see the previous trip's final ping
            // reflected as this trip's GPS status.
            pingCount = 0
            lastPingCount = 0
            lastPingAtMillis = 0L
        }
        tripId = newTripId

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } catch (e: SecurityException) {
                // No location permission (e.g. Refresh tapped before the
                // driver has ever granted it — HomeActivity is expected to
                // gate that, but this is the last line of defense so a gap
                // there stops the service instead of crashing the app).
                // Still must call startForeground() once promptly or the OS
                // kills the app with a "did not call startForeground" crash
                // regardless, so fall back to the untyped overload purely to
                // satisfy that contract, then stop cleanly.
                startForeground(NOTIFICATION_ID, buildNotification())
                stopSelf()
                return START_NOT_STICKY
            }
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        if (intent?.action == ACTION_REFRESH) {
            // Restart tracking only if it isn't already running — calling
            // startLocationUpdates() while a callback is already registered
            // would double up periodic updates.
            if (!isTracking) {
                isTracking = true
                startLocationUpdates()
            }
            requestImmediateFix()
        } else {
            isTracking = true
            startLocationUpdates()
        }
        return START_REDELIVER_INTENT
    }

    // Driver-triggered Refresh: one immediate high-accuracy fix, posted
    // through the same postLocation() path (and therefore the same 50m
    // accuracy filter) as the periodic updates.
    private fun requestImmediateFix() {
        refreshInFlight = true
        try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    postLocation(location) { refreshInFlight = false }
                } else {
                    refreshInFlight = false
                }
            }.addOnFailureListener {
                refreshInFlight = false
            }
        } catch (e: SecurityException) {
            refreshInFlight = false
        }
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

    // onComplete is only used by the driver-triggered Refresh path (to clear
    // refreshInFlight); the periodic callback in startLocationUpdates()
    // doesn't pass one. It fires exactly once regardless of which of the
    // three exits below is taken.
    private fun postLocation(location: Location, onComplete: (() -> Unit)? = null) {
        if (location.hasAccuracy() && location.accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) {
            onComplete?.invoke()
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
                    lastPingAtMillis = System.currentTimeMillis()
                    updateNotification()
                } else if (response.code() == 409) {
                    // Server says this trip is no longer in_progress — stop
                    // tracking outright, do not retry.
                    handleTripEnded()
                }
                // Any other failed ping is skipped, not retried — the next
                // one is only 10 seconds away regardless.
                onComplete?.invoke()
            }

            override fun onFailure(call: Call<StatusResponse>, t: Throwable) {
                // Same reasoning — let the next interval handle it.
                onComplete?.invoke()
            }
        })
    }

    // Called when the server returns 409 {"error":"trip_not_active"} — the
    // trip ended (completed/cancelled) from somewhere other than this app.
    private fun handleTripEnded() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        isTracking = false
        postTripEndedNotification()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun postTripEndedNotification() {
        val openIntent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, TRIP_ENDED_CHANNEL_ID)
            .setContentTitle("Trip ended")
            .setContentText("This trip is no longer active. Tracking has stopped.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        getSystemService(NotificationManager::class.java).notify(TRIP_ENDED_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannels() {
        val trackingChannel = NotificationChannel(CHANNEL_ID, "Trip Tracking", NotificationManager.IMPORTANCE_LOW)
        trackingChannel.description = "Shows while your location is being shared during an active trip"

        val endedChannel = NotificationChannel(TRIP_ENDED_CHANNEL_ID, "Trip Status", NotificationManager.IMPORTANCE_DEFAULT)
        endedChannel.description = "Notifies you when trip tracking stops"

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(trackingChannel)
        manager.createNotificationChannel(endedChannel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MoveOps \u2014 Trip in progress")
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
        isTracking = false
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}