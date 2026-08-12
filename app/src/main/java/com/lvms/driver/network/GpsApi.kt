package com.lvms.driver.network

import com.lvms.driver.model.GpsPayload
import com.lvms.driver.model.StatusResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface GpsApi {
    @POST("index.php")
    fun postGps(@Query("url") route: String, @Body payload: GpsPayload): Call<StatusResponse>
}