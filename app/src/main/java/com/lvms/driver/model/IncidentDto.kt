package com.lvms.driver.model

import com.google.gson.annotations.SerializedName

data class IncidentDto(
    @SerializedName("trip_id") val tripId: Int,
    @SerializedName("incident_type") val incidentType: String,
    val description: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerializedName("occurred_at") val occurredAt: String? = null
) {
    companion object {
        const val TYPE_ACCIDENT = "accident"
        const val TYPE_BREAKDOWN = "breakdown"
        const val TYPE_TRAFFIC_DELAY = "traffic_delay"
        const val TYPE_OTHER = "other"
    }
}

data class IncidentResponse(
    val status: String,
    @SerializedName("incident_id") val incidentId: Int
)