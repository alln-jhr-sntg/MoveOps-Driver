package com.lvms.driver.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
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
                "${trip.vehicleBrand} ${trip.vehicleModel} \u2014 ${trip.plateNumber}"
            binding.purposeText.text = trip.purposeName
            binding.departureText.text = "Departs: ${formatDateTime(trip.departureDatetime)}"

            val isPending = trip.tripStatus == "pending_start"
            binding.statusBadge.text = if (isPending) "PENDING START" else "IN PROGRESS"

            binding.root.setOnClickListener { onTripClick(trip) }
        }
    }
}