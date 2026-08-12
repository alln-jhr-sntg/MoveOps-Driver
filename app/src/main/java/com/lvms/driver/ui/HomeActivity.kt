package com.lvms.driver.ui

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import com.lvms.driver.R
import com.lvms.driver.databinding.ActivityHomeBinding
import com.lvms.driver.model.NotificationListResponse
import com.lvms.driver.model.TripDto
import com.lvms.driver.model.TripListResponse
import com.lvms.driver.network.ApiClient
import com.lvms.driver.network.NotificationApi
import com.lvms.driver.network.SessionManager
import com.lvms.driver.network.TripApi
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
    private var currentTrip: TripDto? = null
    private var nextTrip: TripDto? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SessionManager.init(applicationContext)

        setupBottomNav(binding.bottomNav.bottomNavView, R.id.nav_home)
        setupHeader(binding.appHeader.bellButton, binding.appHeader.logoutButton)
        NotificationBadgeState.render(binding.appHeader.unreadBadgeText)
        binding.appHeader.root.applyInsetPadding(top = true)
        binding.bottomNav.root.applyInsetPadding(bottom = true)

        bindGreeting()

        binding.viewDetailsButton.setOnClickListener { openDetail(currentTrip) }
        binding.markCompletedButton.setOnClickListener { openActiveTrip(currentTrip) }
        binding.nextViewDetailsButton.setOnClickListener { openDetail(nextTrip) }
    }

    override fun onResume() {
        super.onResume()
        loadTrips()
        bindGpsStatus()
        loadUnreadCount()
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
        if (GpsTrackingService.isTracking) {
            binding.gpsDot.setBackgroundResource(R.drawable.dot_gps_active)
            binding.gpsStatusText.text = "GPS active"
            val lastPing = GpsTrackingService.lastPingAtMillis
            binding.gpsLastUpdateText.text = if (lastPing > 0) {
                "Last update: ${DateUtils.getRelativeTimeSpanString(lastPing, System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS)}"
            } else {
                "Waiting for first update…"
            }
        } else {
            binding.gpsDot.setBackgroundResource(R.drawable.dot_gps_inactive)
            binding.gpsStatusText.text = "GPS inactive"
            binding.gpsLastUpdateText.text = "Tracking is not currently running"
        }
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
