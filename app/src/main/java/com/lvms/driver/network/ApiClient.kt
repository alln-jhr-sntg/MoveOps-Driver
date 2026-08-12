package com.lvms.driver.network

import com.google.gson.Gson
import com.lvms.driver.model.ErrorResponse
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // Update this to match your computer's current IPv4 address (ipconfig).
    private const val BASE_URL = "http://10.234.55.170/lvms/api/"

    private val authInterceptor = Interceptor { chain ->
        val token = SessionManager.getToken()
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}

// Pulls the {"error": "..."} message out of a failed response body.
// Every future screen reuses this instead of re-parsing errors itself.
fun <T> Response<T>.parseError(): String {
    val message = try {
        val errorBody = errorBody()?.string()
        Gson().fromJson(errorBody, ErrorResponse::class.java)?.error
    } catch (e: Exception) {
        null
    }
    return message ?: "Request failed (${code()})"
}