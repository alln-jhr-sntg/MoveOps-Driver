package com.lvms.driver.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lvms.driver.R
import com.lvms.driver.databinding.ItemTripBinding
import com.lvms.driver.model.TripDto
import java.text.SimpleDateFormat
import java.util.Locale

fun formatDateTime(raw: String, pattern: String = "MMM d, h:mm a"): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formatter = SimpleDateFormat(pattern, Locale.getDefault())
        formatter.format(parser.parse(raw)!!)
    } catch (e: Exception) {
        raw
    }
}

// Shared status -> (label, badge background, badge text color) mapping so
// every screen (trip list, history, home) renders the same status the same way.
fun statusBadgeLabel(status: String): String = when (status) {
    "pending_start" -> "PENDING START"
    "in_progress" -> "IN PROGRESS"
    "completed" -> "COMPLETED"
    "cancelled" -> "CANCELLED"
    else -> status.uppercase(Locale.getDefault())
}

fun statusBadgeBackgroundRes(status: String): Int = when (status) {
    "pending_start" -> R.drawable.bg_badge_warning
    "in_progress" -> R.drawable.bg_badge_success
    "completed" -> R.drawable.bg_badge_info
    "cancelled" -> R.drawable.bg_badge_danger
    else -> R.drawable.bg_badge_neutral
}

fun statusBadgeTextColorRes(status: String): Int = when (status) {
    "pending_start" -> R.color.status_warning
    "in_progress" -> R.color.status_success
    "completed" -> R.color.status_info
    "cancelled" -> R.color.status_danger
    else -> R.color.text_secondary
}

class TripAdapter(
    private val onTripClick: (TripDto) -> Unit
) : RecyclerView.Adapter<TripAdapter.TripViewHolder>() {

    private var trips: List<TripDto> = emptyList()

    fun submitList(newTrips: List<TripDto>) {
        trips = newTrips
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        val binding = ItemTripBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TripViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        holder.bind(trips[position])
    }

    override fun getItemCount(): Int = trips.size

    inner class TripViewHolder(private val binding: ItemTripBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(trip: TripDto) {
            binding.reservationCodeText.text = trip.reservationCode
            binding.destinationText.text = trip.destination
            binding.vehicleText.text =
                "${trip.vehicleBrand} ${trip.vehicleModel} — ${trip.plateNumber}"
            binding.purposeText.text = trip.purposeName
            binding.departureText.text = "Departs: ${formatDateTime(trip.departureDatetime)}"

            binding.statusBadge.text = statusBadgeLabel(trip.tripStatus)
            binding.statusBadge.setBackgroundResource(statusBadgeBackgroundRes(trip.tripStatus))
            binding.statusBadge.setTextColor(
                binding.root.context.getColor(statusBadgeTextColorRes(trip.tripStatus))
            )

            bindHistoryDetail(trip)

            binding.root.setOnClickListener { onTripClick(trip) }
        }

        private fun bindHistoryDetail(trip: TripDto) {
            val detail = when (trip.tripStatus) {
                "completed" -> {
                    val start = trip.odometerStartKm
                    val end = trip.odometerEndKm
                    if (start != null && end != null) {
                        "Distance: ${"%.0f".format(end - start)} km"
                    } else {
                        null
                    }
                }
                "cancelled" -> trip.cancellationReason?.let { "Reason: $it" }
                else -> null
            }

            if (detail != null) {
                binding.historyDetailText.text = detail
                binding.historyDetailText.visibility = View.VISIBLE
            } else {
                binding.historyDetailText.visibility = View.GONE
            }
        }
    }
}
