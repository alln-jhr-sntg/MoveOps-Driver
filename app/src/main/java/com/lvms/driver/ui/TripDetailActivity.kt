package com.lvms.driver.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.lvms.driver.R
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
        binding.vehicleText.text = "${trip.vehicleBrand} ${trip.vehicleModel} — ${trip.plateNumber}"
        binding.purposeText.text = trip.purposeName
        binding.passengerText.text = "${trip.passengerCount} passenger(s)"
        binding.cargoText.text = "Cargo: ${trip.cargoWeightKg} kg"
        binding.departureText.text = "Departure: ${formatDateTime(trip.departureDatetime, "MMM d, yyyy — h:mm a")}"
        binding.returnText.text = "Expected return: ${formatDateTime(trip.returnDatetime, "MMM d, yyyy — h:mm a")}"

        binding.statusBadge.text = statusBadgeLabel(trip.tripStatus)
        binding.statusBadge.setBackgroundResource(statusBadgeBackgroundRes(trip.tripStatus))
        binding.statusBadge.setTextColor(getColor(statusBadgeTextColorRes(trip.tripStatus)))

        bindGatepass(trip)

        // This screen is also reached from History (completed/cancelled
        // trips), which have nothing left to start or complete.
        when (trip.tripStatus) {
            "pending_start" -> {
                binding.actionButton.visibility = View.VISIBLE
                binding.actionButton.text = "Start Trip"
                binding.trackingStatusText.visibility = View.GONE
            }
            "in_progress" -> {
                binding.actionButton.visibility = View.VISIBLE
                binding.actionButton.text = "View Active Trip"
                binding.trackingStatusText.text =
                    "Live tracking: ${GpsTrackingService.lastPingCount} location update(s) sent"
                binding.trackingStatusText.visibility = View.VISIBLE
            }
            else -> {
                binding.actionButton.visibility = View.GONE
                binding.trackingStatusText.visibility = View.GONE
            }
        }

        binding.actionButton.setOnClickListener {
            val intent = Intent(this, ActiveTripActivity::class.java)
            intent.putExtra("trip", trip)
            startActivity(intent)
        }
    }

    private fun bindGatepass(trip: TripDto) {
        val code = trip.gatepassCode
        if (code == null) {
            binding.gatepassSection.visibility = View.GONE
            return
        }
        binding.gatepassSection.visibility = View.VISIBLE
        binding.gatepassCodeText.text = code

        val status = trip.gatepassStatus ?: "approved"
        binding.gatepassStatusBadge.text = status.uppercase()
        val (badgeBg, badgeColor) = when (status) {
            "approved" -> R.drawable.bg_badge_success to R.color.status_success
            "pending" -> R.drawable.bg_badge_warning to R.color.status_warning
            "rejected" -> R.drawable.bg_badge_danger to R.color.status_danger
            else -> R.drawable.bg_badge_neutral to R.color.text_secondary
        }
        binding.gatepassStatusBadge.setBackgroundResource(badgeBg)
        binding.gatepassStatusBadge.setTextColor(getColor(badgeColor))

        binding.gatepassApproverText.text = trip.gatepassApprover?.let { "Approved by: $it" } ?: "Approver not on record"

        val approvedAt = trip.gatepassApprovedAt
        binding.gatepassApprovedAtText.text = if (approvedAt != null) {
            "Approved: ${formatDateTime(approvedAt, "MMM d, yyyy — h:mm a")}"
        } else {
            "Approval date not on record"
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
