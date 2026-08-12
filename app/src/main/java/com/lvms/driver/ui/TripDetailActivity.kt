package com.lvms.driver.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.lvms.driver.databinding.ActivityTripDetailBinding
import com.lvms.driver.model.TripDto
import com.lvms.driver.service.GpsTrackingService

class TripDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val trip = getTripExtra()
        if (trip == null) {
            Toast.makeText(this, "Trip data missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        bindTrip(trip)
    }

    private fun bindTrip(trip: TripDto) {
        binding.reservationCodeText.text = trip.reservationCode
        binding.destinationText.text = trip.destination
        binding.vehicleText.text = "${trip.vehicleBrand} ${trip.vehicleModel} \u2014 ${trip.plateNumber}"
        binding.purposeText.text = trip.purposeName
        binding.passengerText.text = "${trip.passengerCount} passenger(s)"
        binding.cargoText.text = "Cargo: ${trip.cargoWeightKg} kg"
        binding.departureText.text = "Departure: ${formatDateTime(trip.departureDatetime, "MMM d, yyyy \u2014 h:mm a")}"
        binding.returnText.text = "Expected return: ${formatDateTime(trip.returnDatetime, "MMM d, yyyy \u2014 h:mm a")}"

        val isPending = trip.tripStatus == "pending_start"
        binding.statusBadge.text = if (isPending) "PENDING START" else "IN PROGRESS"
        binding.actionButton.text = if (isPending) "Start Trip" else "View Active Trip"

        if (!isPending) {
            binding.trackingStatusText.text = "Live tracking: ${GpsTrackingService.lastPingCount} location update(s) sent"
            binding.trackingStatusText.visibility = View.VISIBLE
        }

        binding.actionButton.setOnClickListener {
            val intent = Intent(this, ActiveTripActivity::class.java)
            intent.putExtra("trip", trip)
            startActivity(intent)
        }
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