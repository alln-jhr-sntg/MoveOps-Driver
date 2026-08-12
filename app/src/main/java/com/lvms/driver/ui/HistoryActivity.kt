package com.lvms.driver.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.lvms.driver.R
import com.lvms.driver.databinding.ActivityHistoryBinding
import com.lvms.driver.model.TripDto
import com.lvms.driver.model.TripListResponse
import com.lvms.driver.network.ApiClient
import com.lvms.driver.network.SessionManager
import com.lvms.driver.network.TripApi
import com.lvms.driver.network.parseError
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HistoryActivity : BaseNavActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val tripApi by lazy { ApiClient.retrofit.create(TripApi::class.java) }
    private lateinit var adapter: TripAdapter
    private var allTrips: List<TripDto> = emptyList()

    // Tab 0 = Completed, tab 1 = Cancelled.
    private var selectedTabPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SessionManager.init(applicationContext)

        setupBottomNav(binding.bottomNav.root, R.id.nav_history)
        setupHeader(binding.appHeader.bellButton, binding.appHeader.logoutButton)
        NotificationBadgeState.render(binding.appHeader.unreadBadgeText)

        adapter = TripAdapter { trip ->
            val intent = Intent(this, TripDetailActivity::class.java)
            intent.putExtra("trip", trip)
            startActivity(intent)
        }
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.historyRecyclerView.adapter = adapter

        binding.historyTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
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
                    startActivity(Intent(this@HistoryActivity, LoginActivity::class.java))
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
        val status = if (selectedTabPosition == 0) "completed" else "cancelled"
        val trips = allTrips.filter { it.tripStatus == status }
        showTrips(trips, status)
    }

    private fun showTrips(trips: List<TripDto>, status: String) {
        if (trips.isEmpty()) {
            binding.emptyText.text = if (status == "completed") {
                "No completed trips yet"
            } else {
                "No cancelled trips"
            }
            binding.emptyText.visibility = View.VISIBLE
            binding.historyRecyclerView.visibility = View.GONE
        } else {
            binding.emptyText.visibility = View.GONE
            binding.historyRecyclerView.visibility = View.VISIBLE
            adapter.submitList(trips)
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.loadingSpinner.visibility = if (isLoading) View.VISIBLE else View.GONE
        if (isLoading) {
            binding.emptyText.visibility = View.GONE
            binding.historyRecyclerView.visibility = View.GONE
        }
    }
}
