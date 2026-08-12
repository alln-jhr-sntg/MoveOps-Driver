package com.lvms.driver.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
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

    private var bottomNavView: BottomNavigationView? = null
    private var navSelectedItemId: Int = 0
    private var suppressNavCallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }

    override fun onResume() {
        super.onResume()
        syncSelectedTab()
    }

    /**
     * Wires [bottomNav], marks [selectedItemId] as selected, and launches the
     * matching Activity for any other tap. Uses REORDER_TO_FRONT (except for
     * Home, which always collapses the stack via [goHome]) so the four
     * screens share one task instead of growing an unbounded back stack.
     *
     * Also registers a Back handler: from any non-Home tab, Back returns to
     * Home instead of surfacing whatever tab happens to be stacked beneath.
     */
    protected fun setupBottomNav(bottomNav: BottomNavigationView, selectedItemId: Int) {
        bottomNavView = bottomNav
        navSelectedItemId = selectedItemId
        bottomNav.selectedItemId = selectedItemId

        bottomNav.setOnItemSelectedListener { item ->
            if (suppressNavCallback || item.itemId == navSelectedItemId) {
                return@setOnItemSelectedListener true
            }
            if (item.itemId == R.id.nav_home) {
                goHome()
                // Don't paint this bar's Home item as selected — this screen
                // is about to be finished (CLEAR_TOP) or left behind.
                return@setOnItemSelectedListener false
            }
            val target = when (item.itemId) {
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
            // Returning false leaves this (outgoing) bar showing its own tab
            // as selected, instead of briefly highlighting the tapped one.
            false
        }

        if (selectedItemId != R.id.nav_home) {
            onBackPressedDispatcher.addCallback(this) {
                goHome()
            }
        }
    }

    /** Re-asserts the correct tab after REORDER_TO_FRONT resumes this screen
     * without re-running onCreate. */
    private fun syncSelectedTab() {
        val bottomNav = bottomNavView ?: return
        if (bottomNav.selectedItemId == navSelectedItemId) return
        suppressNavCallback = true
        bottomNav.selectedItemId = navSelectedItemId
        suppressNavCallback = false
    }

    /**
     * Collapses the task down to a single, resumed Home instance. Used both
     * for the Home tab tap and for Back from any other tab, so the two paths
     * leave the stack in the same state: Back from Home then exits the app
     * instead of revealing a stale tab underneath.
     */
    protected fun goHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
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
