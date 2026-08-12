package com.lvms.driver.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.lvms.driver.databinding.ActivityActiveTripBinding
import com.lvms.driver.model.CompleteTripRequest
import com.lvms.driver.model.StartTripRequest
import com.lvms.driver.model.TripActionResponse
import com.lvms.driver.model.TripDto
import com.lvms.driver.network.ApiClient
import com.lvms.driver.network.SessionManager
import com.lvms.driver.network.TripApi
import com.lvms.driver.network.parseError
import com.lvms.driver.service.GpsTrackingService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ActiveTripActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActiveTripBinding
    private val tripApi by lazy { ApiClient.retrofit.create(TripApi::class.java) }
    private lateinit var trip: TripDto
    private var isPending = true
    private var pendingOdometer: Double = 0.0

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            submitStart(pendingOdometer)
        } else {
            binding.statusText.text = "Location permission is required to start this trip"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActiveTripBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SessionManager.init(applicationContext)

        val extra = getTripExtra()
        if (extra == null) {
            finish()
            return
        }
        trip = extra
        isPending = trip.tripStatus == "pending_start"

        bindTripInfo()
        binding.confirmButton.setOnClickListener { attemptSubmit() }
    }

    private fun bindTripInfo() {
        binding.reservationCodeText.text = trip.reservationCode
        binding.destinationText.text = trip.destination
        binding.vehicleText.text = "${trip.vehicleBrand} ${trip.vehicleModel} \u2014 ${trip.plateNumber}"

        if (isPending) {
            binding.odometerLabel.text = "Starting Odometer (km)"
            binding.confirmButton.text = "Confirm Start"
        } else {
            binding.odometerLabel.text = "Ending Odometer (km)"
            binding.confirmButton.text = "Complete Trip"

            val startOdo = trip.odometerStartKm
            if (startOdo != null) {
                binding.referenceOdometerText.text = "Started at: $startOdo km"
                binding.referenceOdometerText.visibility = View.VISIBLE
            }
        }
    }

    private fun attemptSubmit() {
        val odometer = binding.odometerInput.text.toString().trim().toDoubleOrNull()

        if (odometer == null) {
            binding.statusText.text = "Enter a valid odometer reading"
            return
        }
        if (odometer < 0) {
            binding.statusText.text = "Odometer reading cannot be negative"
            return
        }
        if (!isPending) {
            val startOdo = trip.odometerStartKm
            if (startOdo != null && odometer < startOdo) {
                binding.statusText.text = "Ending odometer ($odometer) cannot be less than starting odometer ($startOdo)"
                return
            }
        }

        if (isPending) {
            pendingOdometer = odometer
            if (hasRequiredPermissions()) {
                submitStart(odometer)
            } else {
                permissionLauncher.launch(requiredPermissions())
            }
        } else {
            submitComplete(odometer)
        }
    }

    private fun hasRequiredPermissions(): Boolean {
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

    private fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun submitStart(odometer: Double) {
        setLoading(true)
        tripApi.startTrip("trips/${trip.tripId}/start", StartTripRequest(odometer))
            .enqueue(object : Callback<TripActionResponse> {
                override fun onResponse(call: Call<TripActionResponse>, response: Response<TripActionResponse>) {
                    setLoading(false)
                    if (response.isSuccessful) {
                        val serviceIntent = Intent(this@ActiveTripActivity, GpsTrackingService::class.java)
                        serviceIntent.putExtra("trip_id", trip.tripId)
                        ContextCompat.startForegroundService(this@ActiveTripActivity, serviceIntent)
                        returnToTripList()
                    } else {
                        binding.statusText.text = response.parseError()
                    }
                }

                override fun onFailure(call: Call<TripActionResponse>, t: Throwable) {
                    setLoading(false)
                    binding.statusText.text = "Connection failed: ${t.message}"
                }
            })
    }

    private fun submitComplete(odometer: Double) {
        setLoading(true)
        tripApi.completeTrip("trips/${trip.tripId}/complete", CompleteTripRequest(odometer))
            .enqueue(object : Callback<TripActionResponse> {
                override fun onResponse(call: Call<TripActionResponse>, response: Response<TripActionResponse>) {
                    setLoading(false)
                    if (response.isSuccessful) {
                        stopService(Intent(this@ActiveTripActivity, GpsTrackingService::class.java))
                        returnToTripList()
                    } else {
                        binding.statusText.text = response.parseError()
                    }
                }

                override fun onFailure(call: Call<TripActionResponse>, t: Throwable) {
                    setLoading(false)
                    binding.statusText.text = "Connection failed: ${t.message}"
                }
            })
    }

    private fun returnToTripList() {
        val intent = Intent(this@ActiveTripActivity, TripListActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.loadingSpinner.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.confirmButton.isEnabled = !isLoading
        if (isLoading) binding.statusText.text = ""
    }

    private fun getTripExtra(): TripDto? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("trip", TripDto::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("trip") as? TripDto
        }
    }
}