package com.lvms.driver.network

import com.lvms.driver.model.VersionInfo
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Url

// version.json lives as a static file outside /api/, so this hits an
// absolute URL directly rather than following the index.php?url= pattern
// the rest of the API uses — it's a public, unauthenticated asset, not a
// server route.
interface VersionApi {
    @GET
    fun getLatestVersion(@Url url: String): Call<VersionInfo>
}
