package com.lvms.driver.ui

import android.view.View
import android.widget.TextView

/**
 * In-memory cache of the driver's unread notification count, shared across
 * the bottom-nav screens. Only Home and Notifications hit the network for
 * this (GET /api/notifications returns unread_count alongside the list) —
 * Trips and History just render whatever was last fetched, on their own
 * onResume, so the badge stays reasonably in sync without extra requests.
 */
object NotificationBadgeState {
    var unreadCount: Int = 0

    fun render(badge: TextView) {
        if (unreadCount > 0) {
            badge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
    }
}
