package com.lvms.driver.network

import com.lvms.driver.model.NotificationListResponse
import com.lvms.driver.model.StatusResponse
import retrofit2.Call
import retrofit2.http.PATCH
import retrofit2.http.GET
import retrofit2.http.Query

interface NotificationApi {
    @GET("index.php")
    fun getNotifications(@Query("url") route: String): Call<NotificationListResponse>

    @PATCH("index.php")
    fun markRead(@Query("url") route: String): Call<StatusResponse>
}
