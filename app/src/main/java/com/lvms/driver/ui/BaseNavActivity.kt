package com.lvms.driver.ui

import android.content.Intent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.lvms.driver.R

/**
 * Shared wiring for the four bottom-nav destinations (Home, Trips, History,
 * Notifications). Every screen stays a plain Activity — no Fragments, no
 * Navigation component — this just avoids re-wiring the same
 * BottomNavigationView and header logic four times.
 */
abstract class BaseNavActivity : AppCompatActivity() {

    /**
     * Wires [bottomNav], marks [selectedItemId] checked, and launches the
     * matching Activity for any other tap. Uses REORDER_TO_FRONT so the four
     * screens share one task instead of growing an unbounded back stack.
     */
    protected fun setupBottomNav(bottomNav: BottomNavigationView, selectedItemId: Int) {
        bottomNav.menu.findItem(selectedItemId)?.isChecked = true
        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == selectedItemId) {
                return@setOnItemSelectedListener true
            }
            val target = when (item.itemId) {
                R.id.nav_home -> HomeActivity::class.java
                R.id.nav_trips -> TripListActivity::class.java
                R.id.nav_history -> HistoryActivity::class.java
                R.id.nav_notifications -> NotificationsActivity::class.java
                else -> return@setOnItemSelectedListener false
            }
            val intent = Intent(this, target)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            true
        }
    }

    /**
     * Wires the shared header's bell and logout buttons. [onBell] defaults to
     * opening NotificationsActivity — screens that already are Notifications
     * should pass a no-op.
     */
    protected fun setupHeader(
        bellButton: View,
        logoutButton: View,
        onBell: () -> Unit = {
            val intent = Intent(this, NotificationsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }
    ) {
        bellButton.setOnClickListener { onBell() }
        logoutButton.setOnClickListener { LogoutHelper.showLogoutDialog(this) }
    }
}
