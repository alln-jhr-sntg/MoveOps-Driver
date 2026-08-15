package com.lvms.driver.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.lvms.driver.R
import com.lvms.driver.databinding.ActivityTripListBinding
import com.lvms.driver.model.TripDto
import com.lvms.driver.model.TripListResponse
import com.lvms.driver.network.ApiClient
import com.lvms.driver.network.SessionManager
import com.lvms.driver.network.TripApi
import com.lvms.driver.network.parseError
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TripListActivity : BaseNavActivity() {

    private lateinit var binding: ActivityTripListBinding
    private val tripApi by lazy { ApiClient.retrofit.create(TripApi::class.java) }
    private lateinit var adapter: TripAdapter
    private var allTrips: List<TripDto> = emptyList()

    // Tab 0 = Upcoming (pending_start), tab 1 = Ongoing (in_progress).
    // The API now returns every status (History needs completed/cancelled
    // too), so this screen must filter explicitly rather than assume
    // "anything not completed belongs here".
    private var selectedTabPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SessionManager.init(applicationContext)

        setupBottomNav(binding.bottomNav.bottomNavView, R.id.nav_trips)
        setupHeader(binding.appHeader.bellButton, binding.appHeader.logoutButton)
        NotificationBadgeState.render(binding.appHeader.unreadBadgeText)
        binding.appHeader.root.applyInsetPadding(top = true)
        // No applyInsetPadding(bottom = true) here: BottomNavigationView pads
        // itself for the nav bar inset unconditionally, so padding this
        // container too double-counts it and leaves a dead gap above the
        // system nav bar.

        adapter = TripAdapter { trip ->
            val intent = Intent(this, TripDetailActivity::class.java)
            intent.putExtra("trip", trip)
            startActivity(intent)
        }
        binding.tripRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.tripRecyclerView.adapter = adapter

        binding.tripsTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectedTabPosition = tab.position
                renderSelectedTab()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    override fun onResume() {
        super.onResume()
        // Covers first launch (fires right after onCreate) and every
        // return to this screen — e.g. back from a trip just started.
        loadTrips()
        NotificationBadgeState.render(binding.appHeader.unreadBadgeText)
    }

    private fun loadTrips() {
        setLoading(true)

        tripApi.getTrips("trips").enqueue(object : Callback<TripListResponse> {
            override fun onResponse(call: Call<TripListResponse>, response: Response<TripListResponse>) {
                setLoading(false)

                if (response.code() == 401) {
                    SessionManager.clear()
                    startActivity(Intent(this@TripListActivity, LoginActivity::class.java))
                    finish()
                    return
                }

                val body = response.body()
                if (response.isSuccessful && body != null) {
                    allTrips = body.trips
                    renderSelectedTab()
                } else {
                    binding.emptyText.text = response.parseError()
                    binding.emptyText.visibility = View.VISIBLE
                }
            }

            override fun onFailure(call: Call<TripListResponse>, t: Throwable) {
                setLoading(false)
                binding.emptyText.text = "Connection failed: ${t.message}"
                binding.emptyText.visibility = View.VISIBLE
            }
        })
    }

    private fun renderSelectedTab() {
        val status = if (selectedTabPosition == 0) "pending_start" else "in_progress"
        val trips = allTrips.filter { it.tripStatus == status }
        showTrips(trips, status)
    }

    private fun showTrips(trips: List<TripDto>, status: String) {
        if (trips.isEmpty()) {
            binding.emptyText.text = if (status == "pending_start") {
                "No upcoming trips"
            } else {
                "No ongoing trips"
            }
            binding.emptyText.visibility = View.VISIBLE
            binding.tripRecyclerView.visibility = View.GONE
        } else {
            binding.emptyText.visibility = View.GONE
            binding.tripRecyclerView.visibility = View.VISIBLE
            adapter.submitList(trips)
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.loadingSpinner.visibility = if (isLoading) View.VISIBLE else View.GONE
        if (isLoading) {
            binding.emptyText.visibility = View.GONE
            binding.tripRecyclerView.visibility = View.GONE
        }
    }
}
