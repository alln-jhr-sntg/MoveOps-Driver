package com.lvms.driver.network

import com.lvms.driver.model.CompleteTripRequest
import com.lvms.driver.model.StartTripRequest
import com.lvms.driver.model.TripActionResponse
import com.lvms.driver.model.TripListResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Query

interface TripApi {
    @GET("index.php")
    fun getTrips(@Query("url") route: String): Call<TripListResponse>

    @PATCH("index.php")
    fun startTrip(@Query("url") route: String, @Body request: StartTripRequest): Call<TripActionResponse>

    @PATCH("index.php")
    fun completeTrip(@Query("url") route: String, @Body request: CompleteTripRequest): Call<TripActionResponse>
}