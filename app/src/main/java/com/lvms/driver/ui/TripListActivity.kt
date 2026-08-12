package com.lvms.driver.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
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

class TripListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripListBinding
    private val tripApi by lazy { ApiClient.retrofit.create(TripApi::class.java) }
    private lateinit var adapter: TripAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SessionManager.init(applicationContext)

        adapter = TripAdapter { trip ->
            val intent = Intent(this, TripDetailActivity::class.java)
            intent.putExtra("trip", trip)
            startActivity(intent)
        }
        binding.tripRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.tripRecyclerView.adapter = adapter

        binding.logoutText.setOnClickListener {
            SessionManager.clear()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Covers first launch (fires right after onCreate) and every
        // return to this screen — e.g. back from a trip just started.
        loadTrips()
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
                    showTrips(body.trips)
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

    private fun showTrips(trips: List<TripDto>) {
        if (trips.isEmpty()) {
            binding.emptyText.text = "No trips assigned right now"
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