package com.lvms.driver.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class TripDto(
    @SerializedName("trip_id") val tripId: Int,
    @SerializedName("reservation_id") val reservationId: Int,
    @SerializedName("reservation_code") val reservationCode: String,
    val destination: String,
    @SerializedName("departure_datetime") val departureDatetime: String,
    @SerializedName("return_datetime") val returnDatetime: String,
    @SerializedName("trip_status") val tripStatus: String,
    @SerializedName("plate_number") val plateNumber: String,
    @SerializedName("vehicle_brand") val vehicleBrand: String,
    @SerializedName("vehicle_model") val vehicleModel: String,
    @SerializedName("odometer_start_km") val odometerStartKm: Double? = null,
    @SerializedName("passenger_count") val passengerCount: Int,
    @SerializedName("cargo_weight_kg") val cargoWeightKg: Double,
    @SerializedName("purpose_name") val purposeName: String,
    // History fields — only present for completed/cancelled trips.
    @SerializedName("odometer_end_km") val odometerEndKm: Double? = null,
    @SerializedName("actual_departure") val actualDeparture: String? = null,
    @SerializedName("actual_return") val actualReturn: String? = null,
    @SerializedName("cancellation_reason") val cancellationReason: String? = null,
    // Gate pass — every trip implies an approved gatepass (trips are only
    // created at gatepass approval), but these are rendered null-safe anyway.
    @SerializedName("gatepass_code") val gatepassCode: String? = null,
    @SerializedName("gatepass_status") val gatepassStatus: String? = null,
    @SerializedName("gatepass_approver") val gatepassApprover: String? = null,
    @SerializedName("gatepass_approved_at") val gatepassApprovedAt: String? = null
) : Serializable

data class TripListResponse(
    val trips: List<TripDto>
)

data class StartTripRequest(
    @SerializedName("odometer_start_km") val odometerStartKm: Double
)

data class CompleteTripRequest(
    @SerializedName("odometer_end_km") val odometerEndKm: Double
)

data class TripActionResponse(
    val status: String,
    @SerializedName("trip_id") val tripId: Int,
    val message: String
)