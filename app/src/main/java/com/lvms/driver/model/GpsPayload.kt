package com.lvms.driver.model

import com.google.gson.annotations.SerializedName

data class GpsPayload(
    @SerializedName("trip_id") val tripId: Int,
    val latitude: Double,
    val longitude: Double,
    @SerializedName("speed_kph") val speedKph: Double? = null,
    @SerializedName("heading_degrees") val headingDegrees: Int? = null,
    @SerializedName("accuracy_meters") val accuracyMeters: Double? = null
)

data class StatusResponse(
    val status: String
)