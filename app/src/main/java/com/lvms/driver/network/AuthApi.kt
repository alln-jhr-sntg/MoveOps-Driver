package com.lvms.driver.network

import com.lvms.driver.model.LoginRequest
import com.lvms.driver.model.LoginResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface AuthApi {
    @POST("index.php")
    fun login(
        @Query("url") route: String,
        @Body request: LoginRequest
    ): Call<LoginResponse>
}