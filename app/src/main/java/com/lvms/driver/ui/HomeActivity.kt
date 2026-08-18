package com.lvms.driver.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.lvms.driver.R
import com.lvms.driver.databinding.ActivityHomeBinding
import com.lvms.driver.model.NotificationListResponse
import com.lvms.driver.model.TripDto
import com.lvms.driver.model.TripListResponse
import com.lvms.driver.model.VersionInfo
import com.lvms.driver.network.ApiClient
import com.lvms.driver.network.NotificationApi
import com.lvms.driver.network.SessionManager
import com.lvms.driver.network.TripApi
import com.lvms.driver.network.VersionApi
import com.lvms.driver.network.parseError
import com.lvms.driver.service.GpsTrackingService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * The post-login landing screen. Shows the driver's current trip (if any),
 * live GPS status, and the next upcoming trip. The odometer entry flow for
 * starting/completing a trip stays entirely in ActiveTripActivity — this
 * screen only launches it.
 */
class HomeActivity : BaseNavActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val tripApi by lazy { ApiClient.retrofit.create(TripApi::class.java) }
    private val notificationApi by lazy { ApiClient.retrofit.create(NotificationApi::class.java) }
    private val versionApi by lazy { ApiClient.retrofit.create(VersionApi::class.java) }
    private var currentTrip: TripDto? = null
    private var nextTrip: TripDto? = null
    private var pendingGpsRefreshTripId: Int? = null

    // Requests location (+ notifications on Tiramisu+) before letting a
    // Refresh tap reach GpsTrackingService — a trip started administratively
    // (not through this device's ActiveTripActivity "Confirm Start", which
    // already gates on these same permissions) can have tracking still
    // completely ungranted the first time the driver opens the app.
    private val gpsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val tripId = pendingGpsRefreshTripId
        pendingGpsRefreshTripId = null
        if (tripId != null && results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            GpsTrackingService.refresh(this, tripId)
        }
        bindGpsStatus()
    }

    // Ticks bindGpsStatus() once a second while the screen is visible, so
    // "Last update: X ago" and the Live/Delayed/Stale tier actually advance
    // instead of freezing at whatever they were when onResume last ran.
    private val gpsTickHandler = Handler(Looper.getMainLooper())
    private val gpsTickRunnable = object : Runnable {
        override fun run() {
            bindGpsStatus()
            gpsTickHandler.postDelayed(this, GPS_TICK_INTERVAL_MS)
        }
    }

    companion object {
        // Static asset, not an /api/ route — same host as ApiClient.BASE_URL.
        private const val VERSION_CHECK_URL =
            "https://darkgoldenrod-chough-131870.hostingersite.com/public/downloads/version.json"
        private const val GPS_TICK_INTERVAL_MS = 1_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SessionManager.init(applicationContext)

        setupBottomNav(binding.bottomNav.bottomNavView, R.id.nav_home)
        setupHeader(binding.appHeader.bellButton, binding.appHeader.logoutButton)
        NotificationBadgeState.render(binding.appHeader.unreadBadgeText)
        binding.appHeader.root.applyInsetPadding(top = true)
        // No applyInsetPadding(bottom = true) here: BottomNavigationView pads
        // itself for the nav bar inset unconditionally, so padding this
        // container too double-counts it and leaves a dead gap above the
        // system nav bar.

        bindGreeting()

        binding.viewDetailsButton.setOnClickListener { openDetail(currentTrip) }
        binding.markCompletedButton.setOnClickListener { openActiveTrip(currentTrip) }
        binding.nextViewDetailsButton.setOnClickListener { openDetail(nextTrip) }
        binding.gpsRefreshButton.setOnClickListener { onRefreshGpsClicked() }
    }

    override fun onResume() {
        super.onResume()
        loadTrips()
        bindGpsStatus()
        loadUnreadCount()
        checkForUpdate()
        gpsTickHandler.postDelayed(gpsTickRunnable, GPS_TICK_INTERVAL_MS)
    }

    override fun onPause() {
        super.onPause()
        gpsTickHandler.removeCallbacks(gpsTickRunnable)
    }

    private fun onRefreshGpsClicked() {
        val trip = currentTrip ?: return
        if (hasRequiredLocationPermissions()) {
            GpsTrackingService.refresh(this, trip.tripId)
        } else {
            pendingGpsRefreshTripId = trip.tripId
            gpsPermissionLauncher.launch(requiredLocationPermissions())
        }
        bindGpsStatus() // reflect refreshInFlight immediately, don't wait for the next tick
    }

    // Same permission set and SDK gating as ActiveTripActivity's
    // hasRequiredPermissions()/requiredPermissions() — kept as a separate
    // copy here since the two screens have no shared base to hang a
    // utility off, matching this app's existing "no Utils file" convention.
    private fun hasRequiredLocationPermissions(): Boolean {
        val locationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        return locationGranted && notificationsGranted
    }

    private fun requiredLocationPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Best-effort — a failed check just leaves the banner hidden. Never
    // blocks or interrupts the driver's actual work.
    private fun checkForUpdate() {
        versionApi.getLatestVersion(VERSION_CHECK_URL).enqueue(object : Callback<VersionInfo> {
            override fun onResponse(call: Call<VersionInfo>, response: Response<VersionInfo>) {
                val info = response.body() ?: return
                if (!response.isSuccessful || info.versionCode <= installedVersionCode()) {
                    return
                }
                binding.updateVersionText.text = "Version ${info.versionName} is ready to install"
                binding.updateBanner.visibility = View.VISIBLE
                binding.updateNowButton.setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)))
                }
            }

            override fun onFailure(call: Call<VersionInfo>, t: Throwable) {
                // Silent — no network is not worth bothering the driver about.
            }
        })
    }

    private fun installedVersionCode(): Int {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }
    }

    // Home and Notifications are the only screens that hit the network for
    // the unread count — the rest of the bottom nav just renders whatever
    // was last cached in NotificationBadgeState.
    private fun loadUnreadCount() {
        notificationApi.getNotifications("notifications").enqueue(object : Callback<NotificationListResponse> {
            override fun onResponse(call: Call<NotificationListResponse>, response: Response<NotificationListResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    NotificationBadgeState.unreadCount = body.unreadCount
                    NotificationBadgeState.render(binding.appHeader.unreadBadgeText)
                }
            }

            override fun onFailure(call: Call<NotificationListResponse>, t: Throwable) {
                // Badge just keeps showing its last known value.
            }
        })
    }

    private fun bindGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeOfDay = when {
            hour < 12 -> "Good morning"
            hour < 18 -> "Good afternoon"
            else -> "Good evening"
        }
        val firstName = SessionManager.getFirstName().orEmpty()
        binding.greetingText.text = "$timeOfDay, $firstName"
        binding.dateText.text = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Calendar.getInstance().time)
    }

    private fun loadTrips() {
        binding.loadingSpinner.visibility = View.VISIBLE
        binding.emptyStateText.visibility = View.GONE

        tripApi.getTrips("trips").enqueue(object : Callback<TripListResponse> {
            override fun onResponse(call: Call<TripListResponse>, response: Response<TripListResponse>) {
                binding.loadingSpinner.visibility = View.GONE

                if (response.code() == 401) {
                    SessionManager.clear()
                    startActivity(Intent(this@HomeActivity, LoginActivity::class.java))
                    finish()
                    return
                }

                val body = response.body()
                if (response.isSuccessful && body != null) {
                    showTrips(body.trips)
                } else {
                    binding.emptyStateText.text = response.parseError()
                    binding.emptyStateText.visibility = View.VISIBLE
                }
            }

            override fun onFailure(call: Call<TripListResponse>, t: Throwable) {
                binding.loadingSpinner.visibility = View.GONE
                binding.emptyStateText.text = "Connection failed: ${t.message}"
                binding.emptyStateText.visibility = View.VISIBLE
            }
        })
    }

    private fun showTrips(trips: List<TripDto>) {
        currentTrip = trips.firstOrNull { it.tripStatus == "in_progress" }
        nextTrip = trips
            .filter { it.tripStatus == "pending_start" }
            .minByOrNull { it.departureDatetime }

        bindCurrentTrip(currentTrip)
        bindNextTrip(nextTrip)
        bindGpsStatus()

        val nothingToShow = currentTrip == null && nextTrip == null
        binding.emptyStateText.visibility = if (nothingToShow) View.VISIBLE else View.GONE
        if (nothingToShow) {
            binding.emptyStateText.text = "No trips assigned right now"
        }
    }

    private fun bindCurrentTrip(trip: TripDto?) {
        if (trip == null) {
            binding.currentTripSection.visibility = View.GONE
            binding.gpsStatusSection.visibility = View.GONE
            return
        }
        binding.currentTripSection.visibility = View.VISIBLE
        binding.currentReservationCodeText.text = trip.reservationCode
        binding.currentStatusBadge.text = "IN PROGRESS"
        binding.currentDestinationText.text = trip.destination
        binding.currentVehicleText.text = "${trip.vehicleBrand} ${trip.vehicleModel} — ${trip.plateNumber}"
        binding.currentDepartureText.text = "Departure: ${formatDateTime(trip.departureDatetime, "MMM d, yyyy — h:mm a")}"
    }

    private fun bindGpsStatus() {
        if (currentTrip == null) {
            binding.gpsStatusSection.visibility = View.GONE
            return
        }
        binding.gpsStatusSection.visibility = View.VISIBLE

        val isTracking = GpsTrackingService.isTracking
        val lastPing = GpsTrackingService.lastPingAtMillis
        val classification = GpsStatus.classify(isTracking, lastPing)

        binding.gpsDot.setBackgroundResource(classification.dotRes)
        binding.gpsStatusText.text = classification.label
        binding.gpsLastUpdateText.text = when {
            !isTracking -> "Tracking is not currently running"
            lastPing <= 0L -> "Waiting for first update…"
            else -> "Last update: ${GpsStatus.formatAge(System.currentTimeMillis() - lastPing)}"
        }

        val refreshing = GpsTrackingService.refreshInFlight
        binding.gpsRefreshSpinner.visibility = if (refreshing) View.VISIBLE else View.GONE
        binding.gpsRefreshButton.isEnabled = !refreshing
    }

    private fun bindNextTrip(trip: TripDto?) {
        if (trip == null) {
            binding.nextTripSection.visibility = View.GONE
            return
        }
        binding.nextTripSection.visibility = View.VISIBLE
        binding.nextReservationCodeText.text = trip.reservationCode
        binding.nextStatusBadge.text = "PENDING START"
        binding.nextDestinationText.text = trip.destination
        binding.nextDepartureText.text = "Departure: ${formatDateTime(trip.departureDatetime, "MMM d, yyyy — h:mm a")}"
    }

    private fun openDetail(trip: TripDto?) {
        if (trip == null) return
        val intent = Intent(this, TripDetailActivity::class.java)
        intent.putExtra("trip", trip)
        startActivity(intent)
    }

    private fun openActiveTrip(trip: TripDto?) {
        if (trip == null) return
        val intent = Intent(this, ActiveTripActivity::class.java)
        intent.putExtra("trip", trip)
        startActivity(intent)
    }
}
