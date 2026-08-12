package com.lvms.driver.ui

import android.content.Context
import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lvms.driver.network.SessionManager
import com.lvms.driver.service.GpsTrackingService

/**
 * Shared logout confirmation for every bottom-nav screen. Warns about live
 * GPS tracking before stopping it — GpsTrackingService.isTracking is read
 * directly so this works even on screens holding no trip list of their own.
 */
object LogoutHelper {

    fun showLogoutDialog(context: Context) {
        val title: String
        val message: String
        if (GpsTrackingService.isTracking) {
            title = "Active Trip Detected"
            message = "You currently have an ongoing trip. Logging out will " +
                "stop GPS tracking. Are you sure you want to continue?"
        } else {
            title = "Logout Confirmation"
            message = "Are you sure you want to log out?"
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Log Out") { _, _ -> performLogout(context) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLogout(context: Context) {
        context.stopService(Intent(context, GpsTrackingService::class.java))
        SessionManager.clear()
        val intent = Intent(context, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
    }
}
