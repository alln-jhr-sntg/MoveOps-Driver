package com.lvms.driver.ui

import com.lvms.driver.R

/**
 * GPS health tiers for the Home screen's GPS card, derived from how many
 * milliseconds old GpsTrackingService.lastPingAtMillis is. Mirrors the
 * GPS_*_MAX_SECONDS thresholds and tiers in the web app's
 * config/constants.php, so the driver's card and the dispatcher's live map
 * agree on what "Live"/"Delayed"/"Stale" mean. GpsTrackingService posts
 * every UPDATE_INTERVAL_MS (10s), so Live spans two cycles — one missed
 * ping shouldn't flash the card amber.
 */
object GpsStatus {
    private const val LIVE_MAX_MS = 20_000L
    private const val DELAYED_MAX_MS = 60_000L
    private const val STALE_MAX_MS = 300_000L

    enum class Tier { LIVE, DELAYED, STALE, NO_SIGNAL, INACTIVE, AWAITING }

    data class Classification(val tier: Tier, val label: String, val dotRes: Int)

    /**
     * @param isTracking GpsTrackingService.isTracking
     * @param lastPingAtMillis GpsTrackingService.lastPingAtMillis (0 = no ping yet this trip)
     * @param nowMillis defaults to the real clock; overridable for tests
     */
    fun classify(
        isTracking: Boolean,
        lastPingAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Classification {
        if (!isTracking) {
            return Classification(Tier.INACTIVE, "GPS inactive", R.drawable.dot_gps_inactive)
        }
        if (lastPingAtMillis <= 0L) {
            return Classification(Tier.AWAITING, "Awaiting GPS", R.drawable.dot_gps_inactive)
        }

        return when (val age = nowMillis - lastPingAtMillis) {
            in 0..LIVE_MAX_MS -> Classification(Tier.LIVE, "Live", R.drawable.dot_gps_active)
            in LIVE_MAX_MS..DELAYED_MAX_MS -> Classification(Tier.DELAYED, "Delayed", R.drawable.dot_gps_delayed)
            in DELAYED_MAX_MS..STALE_MAX_MS -> Classification(Tier.STALE, "Stale", R.drawable.dot_gps_stale)
            else -> {
                if (age < 0) {
                    // Clock moved backward (device time change) — treat as
                    // fresh rather than showing a nonsensical negative age.
                    Classification(Tier.LIVE, "Live", R.drawable.dot_gps_active)
                } else {
                    Classification(Tier.NO_SIGNAL, "No Signal", R.drawable.dot_gps_inactive)
                }
            }
        }
    }

    // "just now" / "5 seconds ago" / "1 minute 30 seconds ago" — matches the
    // web live map's formatDuration() in public/js/live_map.js.
    fun formatAge(ageMillis: Long): String {
        val totalSeconds = (ageMillis / 1000).coerceAtLeast(0)
        if (totalSeconds < 5) return "just now"

        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val parts = mutableListOf<String>()
        if (minutes > 0) parts.add("$minutes " + if (minutes == 1L) "minute" else "minutes")
        if (seconds > 0 || minutes == 0L) parts.add("$seconds " + if (seconds == 1L) "second" else "seconds")
        return parts.joinToString(" ") + " ago"
    }
}
