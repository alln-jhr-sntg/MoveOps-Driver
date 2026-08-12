package com.lvms.driver.network

import android.content.Context
import android.content.SharedPreferences

object SessionManager {
    private const val PREFS_NAME = "lvms_session"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_FIRST_NAME = "first_name"
    private const val KEY_LAST_NAME = "last_name"

    private lateinit var prefs: SharedPreferences

    // Must be called before any other method here — LoginActivity does
    // this in onCreate() since it's currently always the entry point.
    // If a non-login screen ever becomes reachable first, this moves to
    // a custom Application class instead.
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveSession(token: String, userId: Int, firstName: String, lastName: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putInt(KEY_USER_ID, userId)
            .putString(KEY_FIRST_NAME, firstName)
            .putString(KEY_LAST_NAME, lastName)
            .apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)
    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, -1)
    fun getFirstName(): String? = prefs.getString(KEY_FIRST_NAME, null)
    fun getLastName(): String? = prefs.getString(KEY_LAST_NAME, null)
    fun isLoggedIn(): Boolean = getToken() != null

    fun clear() {
        prefs.edit().clear().apply()
    }
}